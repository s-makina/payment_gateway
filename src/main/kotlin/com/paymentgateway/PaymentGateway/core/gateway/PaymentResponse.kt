package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import io.swagger.v3.oas.annotations.media.Schema
import tools.jackson.databind.JsonNode
import java.time.Instant

@Schema(description = "Provider-neutral payment initiation result")
data class PaymentResponse(
    @Schema(description = "Internal transaction ID", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    val transactionId: String,
    @Schema(description = "Gateway that received the payment", example = "PAYCHANGU")
    val gateway: GatewayType,
    @Schema(description = "Current transaction status", example = "AWAITING_CUSTOMER_PAYMENT")
    val status: PaymentStatus,
    @Schema(description = "Merchant reference echoed back", example = "INV-10001")
    val reference: String,
    @Schema(description = "Gateway-side transaction identifier, if assigned yet")
    val gatewayTransactionId: String? = null,
    @Schema(description = "Gateway payment instructions (e.g. timed account number)")
    val paymentInstructions: Map<String, Any>? = null,
    @Schema(description = "Original response payload returned by the gateway")
    val gatewayResponse: JsonNode? = null,
    @Schema(description = "Creation timestamp")
    val createdAt: Instant = Instant.now()
)
