package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import tools.jackson.databind.JsonNode
import java.math.BigDecimal
import java.time.Instant

data class PaymentStatusResult(
    val gatewayTransactionId: String,
    val status: PaymentStatus,
    val amount: BigDecimal? = null,
    val currency: String? = null,
    val transactionDate: Instant? = null,
    val responseCode: String? = null,
    val responseMessage: String? = null,
    val metadata: Map<String, Any>? = null,
    val gatewayResponse: JsonNode? = null
)
