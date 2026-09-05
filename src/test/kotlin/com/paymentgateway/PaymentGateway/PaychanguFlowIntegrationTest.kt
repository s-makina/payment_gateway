package com.paymentgateway.PaymentGateway

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get as wireMockGet
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post as wireMockPost
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
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
        "payment.gateways.paychangu.enabled=true",
        "payment.gateways.paychangu.secret-key=sec-test-key",
        "payment.gateways.paychangu.webhook-secret=test-webhook-secret",
        "payment.gateways.paychangu.callback-url=https://shop.example.com/callback",
        "payment.gateways.paychangu.return-url=https://shop.example.com/return",
        "payment.reconciliation.enabled=false"
    ]
)
@Import(TestTokenCacheConfig::class)
@AutoConfigureMockMvc
class PaychanguFlowIntegrationTest {

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
            registry.add("payment.gateways.paychangu.base-url") { "http://localhost:${wireMock.port()}" }
        }

        private fun stubEndpoints() {
            // Hosted checkout initiation. The inner tx_ref echoes the tx_ref from
            // the request body (response templating) so every checkout gets its
            // own lookup key.
            wireMock.stubFor(
                wireMockPost(urlEqualTo("/payment"))
                    .willReturn(
                        okJson(
                            """
                            {
                              "message": "Hosted payment session generated successfully.",
                              "status": "success",
                              "data": {
                                "event": "checkout.session:created",
                                "checkout_url": "https://test-checkout.paychangu.com/7887951180",
                                "data": {
                                  "tx_ref": "{{jsonPath request.body '$.tx_ref'}}",
                                  "currency": "MWK",
                                  "amount": 10000,
                                  "mode": "sandbox",
                                  "status": "pending"
                                }
                              }
                            }
                            """.trimIndent()
                        )
                    )
                    .withTransformers("response-template")
            )
            // Direct MoMo charge initiation.
            wireMock.stubFor(
                wireMockPost(urlEqualTo("/mobile-money/payments/initialize"))
                    .willReturn(
                        okJson(
                            """
                            {
                              "status": "success",
                              "message": "Payment initiated successfully.",
                              "data": {
                                "charge_id": "27",
                                "ref_id": "95652259752",
                                "status": "pending",
                                "mobile": "+265990000000",
                                "currency": "MWK",
                                "amount": 5000,
                                "mobile_money": {"name": "Airtel Money", "country": "Malawi"}
                              }
                            }
                            """.trimIndent()
                        )
                    )
            )
            // Direct-charge verification keyed by the prefixed charge_id.
            wireMock.stubFor(
                wireMockGet(urlEqualTo("/mobile-money/payments/PDC-INV-20001/verify"))
                    .willReturn(
                        okJson(
                            """
                            {
                              "status": "successful",
                              "message": "Payment authorized and completed successfully.",
                              "data": {
                                "charge_id": "PDC-INV-20001",
                                "status": "success",
                                "amount": 5000,
                                "currency": "MWK",
                                "event_type": "api.charge.payment"
                              }
                            }
                            """.trimIndent()
                        )
                    )
            )
            // Checkout verification: the tx_ref issued to INV-30001 resolves to
            // a completed payment.
            wireMock.stubFor(
                wireMockGet(urlEqualTo("/verify-payment/INV-30001"))
                    .willReturn(
                        okJson(
                            """
                            {
                              "status": "success",
                              "message": "Payment details retrieved successfully.",
                              "data": {
                                "event_type": "checkout.payment",
                                "tx_ref": "ae041eae-6abd-4602-a949-56fbd65c29fe",
                                "mode": "sandbox",
                                "type": "API Payment (Checkout)",
                                "status": "success",
                                "reference": "26262633201",
                                "currency": "MWK",
                                "amount": 10000,
                                "charges": 40
                              }
                            }
                            """.trimIndent()
                        )
                    )
            )
            // Any other checkout verification is unknown at the gateway -> PENDING.
            wireMock.stubFor(
                wireMockGet(urlMatching("/verify-payment/.*"))
                    .willReturn(aResponse().withStatus(404))
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
    fun `initiates hosted checkout, processes signed webhook, and deduplicates`() {
        // 1. Initiate a hosted-checkout payment
        val initiateBody = """
            {
              "gateway": "PAYCHANGU",
              "paymentType": "COLLECTION",
              "amount": 10000,
              "currency": "MWK",
              "reference": "INV-30001",
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
        assertEquals("https://test-checkout.paychangu.com/7887951180", paymentInstructions["checkoutUrl"])
        assertEquals("ae041eae-6abd-4602-a949-56fbd65c29fe", initiateMap["gatewayTransactionId"])

        // 2. Deliver a signed checkout.payment webhook
        val webhookBody = """
            {
              "event_type": "checkout.payment",
              "tx_ref": "ae041eae-6abd-4602-a949-56fbd65c29fe",
              "reference": "26262633201",
              "currency": "MWK",
              "amount": 10000,
              "status": "success",
              "mode": "test"
            }
        """.trimIndent()
        val signature = Hashing.hmacSha256Hex(webhookBody, "test-webhook-secret")

        mockMvc.perform(
            post("/api/v1/webhooks/paychangu")
                .header("Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isOk)
            .andExpect(content().string("acknowledged"))

        // 3. Transaction is now SUCCESS with the gateway reference captured
        mockMvc.perform(get("/api/v1/payments/$transactionId"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                      "transactionId": "$transactionId",
                      "status": "SUCCESS"
                    }
                    """.trimIndent()
                )
            )

        // 4. Replaying the same webhook is acknowledged but not re-processed
        mockMvc.perform(
            post("/api/v1/webhooks/paychangu")
                .header("Signature", signature)
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isOk)

        assertEquals(1, webhookEventRepository.count())
        val transaction = transactionRepository.findById(UUID.fromString(transactionId)).get()
        assertEquals(PaymentStatus.SUCCESS, transaction.status)
    }

    @Test
    fun `rejects paychangu webhooks with an invalid signature`() {
        val webhookBody = """{"event_type":"checkout.payment","tx_ref":"T1","status":"success"}"""

        mockMvc.perform(
            post("/api/v1/webhooks/paychangu")
                .header("Signature", "invalid-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(webhookBody)
        )
            .andExpect(status().isUnauthorized)

        assertEquals(0, webhookEventRepository.count())
    }

    @Test
    fun `status poll verifies a direct charge via the prefixed charge id`() {
        val initiateBody = """
            {
              "gateway": "PAYCHANGU",
              "paymentType": "DIRECT_CHARGE",
              "amount": 5000,
              "currency": "MWK",
              "reference": "INV-20001",
              "metadata": {
                "mobile": "265990000000",
                "operatorRefId": "20be6c20-adeb-4b5b-a7ba-0769820df4fb"
              }
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
        assertEquals("PENDING", initiateMap["status"])
        // The prefixed charge_id we issued is the stable lookup key.
        assertEquals("PDC-INV-20001", initiateMap["gatewayTransactionId"])

        // The gateway reports the direct charge completed; the local status is
        // advanced to what the gateway said.
        mockMvc.perform(get("/api/v1/payments/$transactionId"))
            .andExpect(status().isOk)
            .andExpect(
                content().json(
                    """
                    {
                      "transactionId": "$transactionId",
                      "status": "SUCCESS"
                    }
                    """.trimIndent()
                )
            )

        val transaction = transactionRepository.findById(UUID.fromString(transactionId)).get()
        assertEquals(PaymentStatus.SUCCESS, transaction.status)
        // The prefixed charge_id is the lookup key and matches the stubbed
        // direct-charge verification endpoint.
        assertEquals("PDC-INV-20001", transaction.gatewayTransactionId)
    }

    @Test
    fun `status poll for an unknown checkout reference stays pending`() {
        val initiateBody = """
            {
              "gateway": "PAYCHANGU",
              "paymentType": "COLLECTION",
              "amount": 1000,
              "currency": "MWK",
              "reference": "INV-30002",
              "description": "Unknown reference test"
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

        // The stub returns 404 for unknown tx_refs, which the adapter normalizes
        // to PENDING; the transaction stays AWAITING_CUSTOMER_PAYMENT.
        mockMvc.perform(get("/api/v1/payments/$transactionId"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"status": "AWAITING_CUSTOMER_PAYMENT"}"""))
    }

    @Test
    fun `direct charge without required metadata returns bad request`() {
        val body = """
            {
              "gateway": "PAYCHANGU",
              "paymentType": "DIRECT_CHARGE",
              "amount": 5000,
              "currency": "MWK",
              "reference": "INV-20002"
            }
        """.trimIndent()

        mockMvc.perform(
            post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `unsupported payment type returns bad request`() {
        val body = """
            {
              "gateway": "PAYCHANGU",
              "paymentType": "SINGLE_DISBURSEMENT",
              "amount": 1000,
              "currency": "MWK",
              "reference": "INV-30003"
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
