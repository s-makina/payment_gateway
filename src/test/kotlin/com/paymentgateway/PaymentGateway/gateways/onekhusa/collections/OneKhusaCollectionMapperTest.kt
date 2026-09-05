package com.paymentgateway.PaymentGateway.gateways.onekhusa.collections

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaRequestToPayResponse
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaTransactionResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal

class OneKhusaCollectionMapperTest {

    private val mapper = OneKhusaCollectionMapper(
        OneKhusaProperties(merchantAccountNumber = 12345678, capturedBy = "user@example.com")
    )

    private val request = PaymentRequest(
        gateway = GatewayType.ONEKHUSA,
        paymentType = PaymentType.REQUEST_TO_PAY,
        amount = BigDecimal("100.00"),
        currency = "MWK",
        reference = "INV-10001",
        description = "Test purchase"
    )

    @Test
    fun `sanitizeReference strips non-alphanumeric characters`() {
        assertEquals("INV10001", mapper.sanitizeReference("INV-10001"))
        assertEquals("INV10001", mapper.sanitizeReference("INV_10001!"))
    }

    @Test
    fun `sanitizeReference pads short references`() {
        assertEquals("ABXXX", mapper.sanitizeReference("ab"))
    }

    @Test
    fun `sanitizeReference truncates long references to 25 characters`() {
        val long = "A".repeat(40)
        assertEquals(25, mapper.sanitizeReference(long).length)
    }

    @Test
    fun `maps request to OneKhusa request to pay format`() {
        val mapped = mapper.toRequestToPayRequest(request)
        assertEquals(12345678, mapped.merchantAccountNumber)
        assertEquals(BigDecimal("100.00"), mapped.transactionAmount)
        assertEquals("Test purchase", mapped.transactionDescription)
        assertEquals("INV10001", mapped.referenceNumber)
        assertEquals("user@example.com", mapped.capturedBy)
    }

    @Test
    fun `maps initiate response to provider-neutral payment response`() {
        val response = OneKhusaRequestToPayResponse(
            merchantAccountNumber = 12345678,
            timedAccountNumber = "11005533",
            expiryDate = "2099-01-01T00:00:00Z",
            expiryInMinutes = 15
        )
        val gatewayResponse = JsonMapper().readTree("""{"timedAccountNumber":"11005533"}""")
        val paymentResponse = mapper.toPaymentResponse(response, request, gatewayResponse)
        assertEquals(PaymentStatus.AWAITING_CUSTOMER_PAYMENT, paymentResponse.status)
        assertEquals("INV-10001", paymentResponse.reference)
        assertEquals("11005533", paymentResponse.paymentInstructions?.get("timedAccountNumber"))
        assertEquals(15, paymentResponse.paymentInstructions?.get("expiryInMinutes"))
        assertEquals("INV10001", paymentResponse.paymentInstructions?.get("referenceNumber"))
        assertNull(paymentResponse.gatewayTransactionId)
        assertEquals("11005533", paymentResponse.gatewayResponse?.get("timedAccountNumber")?.asText())
    }

    @Test
    fun `maps transaction status codes`() {
        fun result(statusCode: String) = mapper.toPaymentStatusResult(
            OneKhusaTransactionResponse(
                transaction = OneKhusaTransactionResponse.TransactionInfo(
                    transactionReferenceNumber = "TXN1",
                    transactionStatusCode = statusCode
                )
            ),
            "TXN1"
        )
        assertEquals(PaymentStatus.SUCCESS, result("S").status)
        assertEquals(PaymentStatus.FAILED, result("F").status)
        assertEquals(PaymentStatus.REVERSED, result("R").status)
        assertEquals(PaymentStatus.PENDING, result("X").status)
    }

    @Test
    fun `keeps the original gateway response on the status result`() {
        val gatewayResponse = JsonMapper().readTree("""{"transaction":{"transactionReferenceNumber":"CBPC73IQ5U2E"}}""")
        val result = mapper.toPaymentStatusResult(
            OneKhusaTransactionResponse(
                transaction = OneKhusaTransactionResponse.TransactionInfo(
                    transactionReferenceNumber = "CBPC73IQ5U2E",
                    transactionStatusCode = "S"
                )
            ),
            "CBPC73IQ5U2E",
            gatewayResponse
        )
        assertEquals(PaymentStatus.SUCCESS, result.status)
        assertEquals("CBPC73IQ5U2E", result.gatewayResponse?.get("transaction")?.get("transactionReferenceNumber")?.asText())
    }
}