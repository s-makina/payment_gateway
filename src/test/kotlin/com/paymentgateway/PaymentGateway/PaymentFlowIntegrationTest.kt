package com.paymentgateway.PaymentGateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post as wireMockPost
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.util.Hashing
import com.paymentgateway.PaymentGateway.transactions.TransactionRepository
import com.paymentgateway.PaymentGateway.webhooks.WebhookEventRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "payment.gateways.onekhusa.enabled=true",
        "payment.gateways.onekhusa.webhook-secret=test-webhook-secret",
        "payment.gateways.onekhusa.merchant-account-number=12345678",
        "payment.gateways.onekhusa.captured-by=user@example.com"
    ]
)
@Import(TestTokenCacheConfig::class)
@AutoConfigureMockMvc
class PaymentFlowIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        val wireMock: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
            .apply {
                start()
                stubEndpoints()
            }

        @JvmStatic
        @DynamicPropertySource
        fun dynamicProperties(registry: DynamicPropertyRegistry) {
            registry.add("payment.gateways.onekhusa.base-url") { "http://localhost:${wireMock.port()}" }
        }

        private fun stubEndpoints() {
            wireMock.stubFor(
                wireMockPost(urlEqualTo("/account/getAccessToken"))
                    .willReturn(
                        okJson(
                            """
                            {
                              "accessToken": "test-token",
                              "expiresOn": "2099-01-01T00:00:00.000Z",
                              "expiryInMinutes": 5
                            }
                            """.trimIndent()
                        )
                    )
            )
            wireMock.stubFor(
                wireMockPost(urlEqualTo("/collections/requestToPay/initiate"))
                    .willReturn(
                        okJson(
                            """
                            {
                              "merchantAccountNumber": 12345678,
                              "timedAccountNumber": "11005533",
                              "expiryDate": "2099-01-01T00:00:00.000Z",
                              "expiryInMinutes": 15
                            }
                            """.trimIndent()
                        )
                    )
            )
        }
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @Autowired
    lateinit var webhookEventRepository: WebhookEventRepository

    private val objectMapper = jsonMapper { addModule(kotlinModule()) }

    @Test
    fun `initiates request to pay, processes signed webhook, and deduplicates`() {
        // 1. Initiate a request-to-pay payment
        val initiateBody = """
            {
              "gateway": "ONEKHUSA",
              "paymentType": "REQUEST_TO_PAY",
              "amount": 10000,
              "currency": "MWK",
              "reference": "INV-10001",
              "description": "Test purchase"
            }
        """.trimIndent()

        val initiateResponse = mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(initiateBody)
        )
            .andExpect(status().isOk)
            .andReturn().response.contentAsString

        val initiateMap: Map<String, Any> = objectMapper.readValue(initiateResponse)
        val transactionId = initiateMap["transactionId"] as String
        assertEquals("AWAITING_CUSTOMER_PAYMENT", initiateMap["status"])
        val paymentInstructions = initiateMap["paymentInstructions"] as Map<*, *>
        assertEquals("11005533", paymentInstructions["timedAccountNumber"])

        // 2. Deliver a signed payrequest.success webhook
        val webhookBody = """
            {
              "connectorId": 892353,
              "sourceAccountNumber": "74629183",
              "sourceInstitution": "National Bank of Malawi",
              "transactionReferenceNumber": "TXN123",
              "transactionAmount": 10000,
              "transactionStatusCode": "S",
              "responseCode": "S100",
              "metaData": {
                "timedAccountNumber": "11005533",
                "referenceNumber": "INV10001"
              }
            }
        """.trimIndent()
        val signature = Hashing.hmacSha512Hex(webhookBody, "test-webhook-secret")

        mockMvc.perform(
            post("/api/v1/webhooks/onekhusa")
                .header("X-OneKhusa-Webhook-Event", "payrequest.success")
                .header("X-OneKhusa-Webhook-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isOk)
            .andExpect(content().string("acknowledged"))

        // 3. Transaction is now SUCCESS with the gateway reference captured
        mockMvc.perform(get("/api/v1/payments/$transactionId"))
            .andExpect(status().isOk)
            .andExpect(content().json(
                """
                {
                  "transactionId": "$transactionId",
                  "status": "SUCCESS",
                  "gatewayTransactionId": "TXN123"
                }
                """.trimIndent()
            ))

        // 4. Replaying the same webhook is acknowledged but not re-processed
        mockMvc.perform(
            post("/api/v1/webhooks/onekhusa")
                .header("X-OneKhusa-Webhook-Event", "payrequest.success")
                .header("X-OneKhusa-Webhook-Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isOk)

        assertEquals(1, webhookEventRepository.count())
        val transaction = transactionRepository.findById(UUID.fromString(transactionId)).get()
        assertEquals(PaymentStatus.SUCCESS, transaction.status)
        assertEquals("TXN123", transaction.gatewayTransactionId)
    }

    @Test
    fun `rejects webhooks with an invalid signature`() {
        val webhookBody = """{"transactionReferenceNumber":"TXN456","transactionStatusCode":"S"}"""

        mockMvc.perform(
            post("/api/v1/webhooks/onekhusa")
                .header("X-OneKhusa-Webhook-Event", "payment.success")
                .header("X-OneKhusa-Webhook-Signature", "invalid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)

        assertEquals(0, webhookEventRepository.count())
    }

    @Test
    fun `initiate payment with an unsupported payment type returns bad request`() {
        val body = """
            {
              "gateway": "ONEKHUSA",
              "paymentType": "SINGLE_DISBURSEMENT",
              "amount": 10000,
              "currency": "MWK",
              "reference": "INV-10002"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
    }
}