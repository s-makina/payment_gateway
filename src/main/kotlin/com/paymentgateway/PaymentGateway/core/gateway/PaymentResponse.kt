package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import java.time.Instant

data class PaymentResponse(
    val transactionId: String,
    val gateway: GatewayType,
    val status: PaymentStatus,
    val reference: String,
    val gatewayTransactionId: String? = null,
    val paymentInstructions: Map<String, Any>? = null,
    val createdAt: Instant = Instant.now()
)
