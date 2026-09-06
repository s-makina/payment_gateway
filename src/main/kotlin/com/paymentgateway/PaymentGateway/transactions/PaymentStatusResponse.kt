package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import io.swagger.v3.oas.annotations.media.Schema
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Schema(description = "Current state of a payment transaction")
data class PaymentStatusResponse(
    @Schema(description = "Internal transaction ID")
    val transactionId: UUID,
    @Schema(description = "Gateway handling the payment", example = "PAYCHANGU")
    val gateway: GatewayType,
    @Schema(description = "Merchant reference", example = "INV-10001")
    val merchantReference: String,
    @Schema(description = "Current transaction status", example = "SUCCESS")
    val status: PaymentStatus,
    @Schema(description = "Type of payment", example = "REQUEST_TO_PAY")
    val paymentType: PaymentType,
    @Schema(description = "Payment amount", example = "10000")
    val amount: BigDecimal,
    @Schema(description = "ISO currency code", example = "MWK")
    val currency: String,
    @Schema(description = "Gateway-side transaction identifier, if known")
    val gatewayTransactionId: String? = null,
    @Schema(description = "Original response payload returned by the gateway on the last status check")
    val gatewayResponse: JsonNode? = null,
    @Schema(description = "Completion timestamp, once terminal")
    val completedAt: Instant? = null
) {
    companion object {
        fun from(entity: PaymentTransactionEntity): PaymentStatusResponse = PaymentStatusResponse(
            transactionId = requireNotNull(entity.id) { "Transaction ID was not generated on persist" },
            gateway = entity.gateway,
            merchantReference = entity.merchantReference,
            status = entity.status,
            paymentType = entity.paymentType,
            amount = entity.amount,
            currency = entity.currency,
            gatewayTransactionId = entity.gatewayTransactionId,
            completedAt = entity.completedAt
        )
    }
}
