package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import com.paymentgateway.PaymentGateway.core.gateway.PaymentStatusResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

class PaymentReconciliationServiceTest {

    private val now = Instant.parse("2026-09-05T12:00:00Z")

    private fun transaction(
        status: PaymentStatus = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
        gatewayTransactionId: String? = null,
        metadata: Map<String, Any>? = null
    ): PaymentTransactionEntity = PaymentTransactionEntity(
        gateway = GatewayType.ONEKHUSA,
        merchantReference = "INV-10001",
        amount = BigDecimal("10000"),
        currency = "MWK",
        paymentType = PaymentType.REQUEST_TO_PAY,
        gatewayTransactionId = gatewayTransactionId,
        gatewayMetadata = metadata,
        status = status
    )

    private fun gatewayResult(status: PaymentStatus, gatewayTransactionId: String = "GWY-TXN-1") =
        PaymentStatusResult(gatewayTransactionId = gatewayTransactionId, status = status)

    // --- reference resolution ---

    @Test
    fun `resolveGatewayReference prefers the gateway transaction id`() {
        val tx = transaction(
            gatewayTransactionId = "CBPC73IQ5U2E",
            metadata = mapOf("referenceNumber" to "INV10001", "expiryDate" to "2099-01-01T00:00:00Z")
        )
        assertEquals("CBPC73IQ5U2E", tx.gatewayLookupReference())
    }

    @Test
    fun `resolveGatewayReference falls back to the metadata reference number`() {
        val tx = transaction(metadata = mapOf("referenceNumber" to "INV10001"))
        assertEquals("INV10001", tx.gatewayLookupReference())
    }

    @Test
    fun `resolveGatewayReference returns null without any usable reference`() {
        assertNull(transaction().gatewayLookupReference())
        assertNull(transaction(metadata = mapOf("referenceNumber" to "  ")).gatewayLookupReference())
    }

    // --- decideUpdate ---

    @Test
    fun `advances to the gateway status when the transition is allowed`() {
        val tx = transaction(status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT)
        assertEquals(
            PaymentStatus.SUCCESS,
            PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.SUCCESS), now)
        )
        assertEquals(
            PaymentStatus.FAILED,
            PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.FAILED), now)
        )
    }

    @Test
    fun `keeps awaiting payment when gateway still reports pending and TAN has not expired`() {
        val tx = transaction(
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            metadata = mapOf("referenceNumber" to "INV10001", "expiryDate" to "2099-01-01T00:00:00Z")
        )
        assertNull(PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.PENDING), now))
    }

    @Test
    fun `marks expired when gateway still pending and the TAN has lapsed`() {
        val tx = transaction(
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            metadata = mapOf("referenceNumber" to "INV10001", "expiryDate" to "2026-01-01T00:00:00Z")
        )
        assertEquals(
            PaymentStatus.EXPIRED,
            PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.PENDING), now)
        )
    }

    @Test
    fun `does not auto-expire when the gateway reports a terminal state`() {
        val tx = transaction(
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            metadata = mapOf("referenceNumber" to "INV10001", "expiryDate" to "2026-01-01T00:00:00Z")
        )
        assertEquals(
            PaymentStatus.SUCCESS,
            PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.SUCCESS), now)
        )
    }

    @Test
    fun `never auto-expires a transaction that is not awaiting customer payment`() {
        val tx = transaction(
            status = PaymentStatus.PENDING,
            metadata = mapOf("referenceNumber" to "INV10001", "expiryDate" to "2026-01-01T00:00:00Z")
        )
        assertNull(PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.PENDING), now))
    }

    @Test
    fun `ignores disallowed gateway transitions`() {
        val tx = transaction(status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT)
        assertNull(PaymentReconciliationService.decideUpdate(tx, gatewayResult(PaymentStatus.REVERSED), now))
    }

    // --- expiry parsing ---

    @Test
    fun `expiryPassed handles offset and malformed dates`() {
        val pastOffset = transaction(metadata = mapOf("expiryDate" to "2026-01-01T10:00:00+02:00"))
        assertTrue(PaymentReconciliationService.expiryPassed(pastOffset, now))

        val future = transaction(metadata = mapOf("expiryDate" to "2099-01-01T00:00:00Z"))
        assertFalse(PaymentReconciliationService.expiryPassed(future, now))

        val malformed = transaction(metadata = mapOf("expiryDate" to "not-a-date"))
        assertFalse(PaymentReconciliationService.expiryPassed(malformed, now))

        val missing = transaction()
        assertFalse(PaymentReconciliationService.expiryPassed(missing, now))
    }
}
