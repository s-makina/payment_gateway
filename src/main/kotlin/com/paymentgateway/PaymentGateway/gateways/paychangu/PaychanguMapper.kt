package com.paymentgateway.PaymentGateway.gateways.paychangu

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import com.paymentgateway.PaymentGateway.core.gateway.PaymentStatusResult
import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguCheckoutRequest
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguCustomization
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguDirectChargeRequest
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguCheckoutData
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguDirectChargeData
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguTransactionData
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Maps between the provider-neutral core DTOs and Paychangu's wire format.
 * All Paychangu-specific field names, endpoints, and status vocabularies are
 * confined to this class.
 */
@Component
class PaychanguMapper(
    private val properties: PaychanguProperties
) {

    // --- initiate: checkout -------------------------------------------------

    fun toCheckoutRequest(request: PaymentRequest): PaychanguCheckoutRequest {
        // Defence in depth — the same check runs at startup when the gateway is enabled.
        properties.requireValidCheckoutConfig()
        return PaychanguCheckoutRequest(
            amount = request.amount.toPlainString(),
            currency = request.currency,
            txRef = sanitizeTxRef(request.reference),
            callbackUrl = request.callbackUrl ?: properties.callbackUrl,
            returnUrl = request.returnUrl ?: properties.returnUrl,
            firstName = request.stringMetadata(METADATA_FIRST_NAME),
            lastName = request.stringMetadata(METADATA_LAST_NAME),
            email = request.stringMetadata(METADATA_EMAIL),
            customization = PaychanguCustomization(
                title = properties.customizationTitle.ifBlank { null },
                description = request.description
            ),
            meta = request.metadata?.mapValues { it.value.toString() }
        )
    }

    fun toPaymentResponse(
        checkout: PaychanguCheckoutData,
        request: PaymentRequest,
        gatewayResponse: JsonNode? = null
    ): PaymentResponse = PaymentResponse(
        transactionId = "", // filled in by TransactionService once persisted
        gateway = request.gateway,
        status = PaymentStatus.AWAITING_CUSTOMER_PAYMENT,
        reference = request.reference,
        gatewayTransactionId = checkout.inner?.txRef ?: checkout.txRef(),
        paymentInstructions = buildMap {
            checkout.checkoutUrl?.let { put("checkoutUrl", it) }
            checkout.inner?.mode?.let { put("mode", it) }
        },
        gatewayResponse = gatewayResponse
    )

    // --- initiate: direct charge --------------------------------------------

    fun toDirectChargeRequest(request: PaymentRequest): PaychanguDirectChargeRequest {
        properties.requireValidCheckoutConfig()
        val mobile = request.stringMetadata(METADATA_MOBILE)
            ?: throw IllegalArgumentException(
                "metadata.$METADATA_MOBILE is required for PAYCHANGU direct charge payments"
            )
        val operatorRefId = request.stringMetadata(METADATA_OPERATOR_REF_ID)
            ?: throw IllegalArgumentException(
                "metadata.$METADATA_OPERATOR_REF_ID is required for PAYCHANGU direct charge payments"
            )
        return PaychanguDirectChargeRequest(
            mobile = mobile,
            mobileMoneyOperatorRefId = operatorRefId,
            amount = request.amount.toPlainString(),
            chargeId = sanitizeChargeId(request.reference),
            email = request.stringMetadata(METADATA_EMAIL),
            firstName = request.stringMetadata(METADATA_FIRST_NAME),
            lastName = request.stringMetadata(METADATA_LAST_NAME)
        )
    }

    fun toDirectChargeResponse(
        charge: PaychanguDirectChargeData,
        request: PaymentRequest,
        gatewayResponse: JsonNode? = null
    ): PaymentResponse = PaymentResponse(
        transactionId = "", // filled in by TransactionService once persisted
        gateway = request.gateway,
        status = PaymentStatus.PENDING,
        reference = request.reference,
        gatewayTransactionId = charge.chargeId,
        paymentInstructions = buildMap {
            charge.refId?.let { put("operatorRefId", it) }
            charge.mobileMoney?.name?.let { put("operator", it) }
            charge.mobile?.let { put("mobile", it) }
        },
        gatewayResponse = gatewayResponse
    )

    // --- status --------------------------------------------------------------

    fun toPaymentStatusResult(
        transaction: PaychanguTransactionData,
        lookupReference: String,
        gatewayResponse: JsonNode? = null
    ): PaymentStatusResult = PaymentStatusResult(
        gatewayTransactionId = transaction.chargeId
            ?: transaction.reference
            ?: transaction.txRef
            ?: lookupReference,
        status = mapStatus(transaction.status),
        amount = transaction.amount,
        currency = transaction.currency,
        transactionDate = parseDate(transaction.authorization?.completedAt ?: transaction.updatedAt),
        responseCode = transaction.status,
        responseMessage = transaction.eventType,
        metadata = metadataOf(
            "channel" to transaction.authorization?.channel,
            "operator" to transaction.mobileMoney?.name,
            "charges" to transaction.charges,
            "numberOfAttempts" to transaction.numberOfAttempts,
            "customerEmail" to transaction.customer?.email
        ),
        gatewayResponse = gatewayResponse
    )

    fun mapStatus(status: String?): PaymentStatus = when (status?.lowercase()) {
        "success", "successful" -> PaymentStatus.SUCCESS
        "failed" -> PaymentStatus.FAILED
        "cancelled", "canceled" -> PaymentStatus.FAILED
        "reversed" -> PaymentStatus.REVERSED
        else -> PaymentStatus.PENDING
    }

    /**
     * Paychangu `tx_ref` must be unique per transaction; it accepts typical
     * reference characters. Strips characters outside letters/digits/-/_/. and
     * truncates to a safe length, mirroring the OneKhusa reference sanitizer.
     */
    fun sanitizeTxRef(reference: String): String {
        val sanitized = reference.filter { it.isLetterOrDigit() || it in "-_." }
        return sanitized.take(TX_REF_MAX_LENGTH).ifBlank { sanitizedFallback() }
    }

    /** Direct-charge `charge_id` has the same uniqueness requirement as `tx_ref`. */
    fun sanitizeChargeId(reference: String): String = sanitizeTxRef(reference)

    private fun sanitizedFallback(): String = "TX-" + System.nanoTime()

    private fun metadataOf(vararg entries: Pair<String, Any?>): Map<String, Any> =
        entries.filter { it.second != null }.associate { it.first to it.second!! }

    private fun PaymentRequest.stringMetadata(key: String): String? =
        (metadata?.get(key) as? String)?.takeIf { it.isNotBlank() }

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

    companion object {
        const val METADATA_FIRST_NAME = "firstName"
        const val METADATA_LAST_NAME = "lastName"
        const val METADATA_EMAIL = "email"
        const val METADATA_MOBILE = "mobile"
        const val METADATA_OPERATOR_REF_ID = "operatorRefId"

        const val TX_REF_MAX_LENGTH = 100

        /**
         * Payment types this gateway supports, enforced by the gateway facade.
         */
        val SUPPORTED_PAYMENT_TYPES = setOf(PaymentType.COLLECTION, PaymentType.DIRECT_CHARGE)
    }
}
