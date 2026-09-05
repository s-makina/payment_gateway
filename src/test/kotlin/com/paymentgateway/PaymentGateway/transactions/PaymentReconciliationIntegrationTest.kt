package com.paymentgateway.PaymentGateway.transactions

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post as wireMockPost
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.paymentgateway.PaymentGateway.TestTokenCacheConfig
import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = [
        "payment.gateways.onekhusa.enabled=true",
        "payment.gateways.onekhusa.merchant-account-number=12345678",
        "payment.gateways.onekhusa.captured-by=user@example.com",
        // The scheduler is disabled; the service is invoked directly for determinism.
        "payment.reconciliation.enabled=false"
    ]
)
@Import(TestTokenCacheConfig::class)
class PaymentReconciliationIntegrationTest {

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
            // INV-PAID resolves to a completed (paid) transaction.
            wireMock.stubFor(
                wireMockPost(urlEqualTo("/collections/getTransaction"))
                    .atPriority(1)
                    .withRequestBody(matchingJsonPath("$.transactionReferenceNumber", equalTo("INV-PAID")))
                    .willReturn(
                        okJson(
                            """
                            {
                              "transaction": {
                                "transactionReferenceNumber": "GWY-PAID-001",
                                "transactionStatusCode": "S",
                                "responseCode": "S100",
                                "responseMessage": "Successful transaction"
                              }
                            }
                            """.trimIndent()
                        )
                    )
            )
            // Everything else (unknown / still pending at the gateway) is a 204.
            wireMock.stubFor(
                wireMockPost(urlEqualTo("/collections/getTransaction"))
                    .withRequestBody(matchingJsonPath("$.transactionReferenceNumber"))
                    .willReturn(aResponse().withStatus(204))
            )
        }
    }

    @Autowired
    lateinit var transactionRepository: TransactionRepository

    @Autowired
    lateinit var reconciliationService: PaymentReconciliationService

    @Test
    fun `reconciles paid, expired, and still-pending transactions`() {
        val expiredId = seedTransaction(
            reference = "INV-EXPIRED",
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            expiryDate = "2026-01-01T00:00:00Z"
        )
        val paidId = seedTransaction(
            reference = "INV-PAID",
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            expiryDate = "2099-01-01T00:00:00Z"
        )
        val pendingId = seedTransaction(
            reference = "INV-PENDING",
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            expiryDate = "2099-01-01T00:00:00Z"
        )

        val summary = runBlocking { reconciliationService.reconcilePendingTransactions() }

        assertEquals(3, summary.checked)
        assertEquals(1, summary.updated)
        assertEquals(1, summary.expired)
        assertEquals(0, summary.failed)
        assertEquals(0, summary.skipped)

        // TAN lapsed and the gateway never saw the payment -> EXPIRED.
        val expired = transactionRepository.findById(expiredId).get()
        assertEquals(PaymentStatus.EXPIRED, expired.status)
        assertNotNull(expired.completedAt)

        // Gateway reports the payment completed -> SUCCESS with the gateway ref captured.
        val paid = transactionRepository.findById(paidId).get()
        assertEquals(PaymentStatus.SUCCESS, paid.status)
        assertEquals("GWY-PAID-001", paid.gatewayTransactionId)
        assertNotNull(paid.completedAt)

        // Still within its TAN window and gateway reports nothing -> unchanged.
        val pending = transactionRepository.findById(pendingId).get()
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_PAYMENT, pending.status)
        assertNull(pending.gatewayTransactionId)
        assertNull(pending.completedAt)
    }

    @Test
    fun `second reconciliation run is a no-op once transactions are terminal`() {
        val expiredId = seedTransaction(
            reference = "INV-EXPIRED-2",
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            expiryDate = "2026-01-01T00:00:00Z"
        )

        runBlocking { reconciliationService.reconcilePendingTransactions() }
        assertEquals(PaymentStatus.EXPIRED, transactionRepository.findById(expiredId).get().status)

        val second = runBlocking { reconciliationService.reconcilePendingTransactions() }
        assertEquals(0, second.checked)
        assertEquals(0, second.updated)
        assertEquals(0, second.expired)
    }

    private fun seedTransaction(
        reference: String,
        status: PaymentStatus,
        expiryDate: String
    ): UUID {
        val saved = transactionRepository.save(
            PaymentTransactionEntity(
                gateway = GatewayType.ONEKHUSA,
                merchantReference = reference,
                amount = BigDecimal("10000"),
                currency = "MWK",
                paymentType = PaymentType.REQUEST_TO_PAY,
                status = status,
                gatewayMetadata = mapOf(
                    "referenceNumber" to reference,
                    "expiryDate" to expiryDate,
                    "expiryInMinutes" to 15
                ),
                createdAt = Instant.now(),
                updatedAt = Instant.now()
            )
        )
        return requireNotNull(saved.id)
    }
}
