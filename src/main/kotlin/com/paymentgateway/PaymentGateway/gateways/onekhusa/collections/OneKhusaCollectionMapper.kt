package com.paymentgateway.PaymentGateway.gateways.onekhusa.collections

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import com.paymentgateway.PaymentGateway.core.gateway.PaymentStatusResult
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaRequestToPayRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaRequestToPayResponse
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaTransactionResponse
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import java.util.UUID

@Component
class OneKhusaCollectionMapper(
    private val properties: OneKhusaProperties
) {

    fun toRequestToPayRequest(request: PaymentRequest): OneKhusaRequestToPayRequest {
        return OneKhusaRequestToPayRequest(
            merchantAccountNumber = properties.merchantAccountNumber,
            transactionAmount = request.amount,
            transactionDescription = request.description ?: "Payment ${request.reference}",
            referenceNumber = sanitizeReference(request.reference),
            capturedBy = properties.capturedBy
        )
    }

    fun toPaymentResponse(
        response: OneKhusaRequestToPayResponse,
        request: PaymentRequest
    ): PaymentResponse {
        return PaymentResponse(
            transactionId = UUID.randomUUID().toString(),
            gateway = request.gateway,
            status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
            reference = request.reference,
            paymentInstructions = buildMap {
                response.timedAccountNumber?.let { put("timedAccountNumber", it) }
                response.expiryDate?.let { put("expiryDate", it) }
                response.expiryInMinutes?.let { put("expiryInMinutes", it) }
                put("referenceNumber", sanitizeReference(request.reference))
            }
        )
    }

    fun toPaymentStatusResult(
        response: OneKhusaTransactionResponse,
        transactionReference: String
    ): PaymentStatusResult {
        return PaymentStatusResult(
            gatewayTransactionId = response.transaction?.transactionReferenceNumber ?: transactionReference,
            status = mapStatus(response.transaction?.transactionStatusCode),
            amount = response.beneficiary?.amountReceived,
            currency = response.beneficiary?.currencyCode,
            transactionDate = parseDate(response.transaction?.transactionDate),
            responseCode = response.transaction?.responseCode,
            responseMessage = response.transaction?.responseMessage,
            metadata = metadataOf(
                "connectorId" to response.source?.connectorId,
                "sourceInstitution" to response.source?.connectorName,
                "sourceAccountNumber" to response.source?.accountNumber,
                "sourceCustomerName" to response.source?.customerName,
                "transactionFee" to response.transaction?.transactionFee,
                "transactionStatusCode" to response.transaction?.transactionStatusCode
            )
        )
    }

    private fun metadataOf(vararg entries: Pair<String, Any?>): Map<String, Any> =
        entries.filter { it.second != null }.associate { it.first to it.second!! }

    /**
     * OneKhusa requires 5-25 alphanumeric characters. Strips non-alphanumeric
     * characters and pads/truncates to satisfy the constraint.
     */
    fun sanitizeReference(reference: String): String {
        val sanitized = reference.filter { it.isLetterOrDigit() }
        val truncated = if (sanitized.length > 25) sanitized.takeLast(25) else sanitized
        return if (truncated.length < 5) truncated.padEnd(5, 'X') else truncated
    }

    private fun mapStatus(statusCode: String?): PaymentStatus = when (statusCode) {
        "S" -> PaymentStatus.SUCCESS
        "F" -> PaymentStatus.FAILED
        "R" -> PaymentStatus.REVERSED
        else -> PaymentStatus.PENDING
    }

    private fun parseDate(value: String?): Instant? {
        if (value.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(value).toInstant()
        } catch (_: DateTimeParseException) {
            try {
                Instant.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}