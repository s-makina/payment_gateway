package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import java.math.BigDecimal

data class PaymentRequest(
    val gateway: GatewayType,
    val paymentType: PaymentType,
    val amount: BigDecimal,
    val currency: String = "MWK",
    val reference: String,
    val description: String? = null,
    val customerId: String? = null,
    val idempotencyKey: String? = null,
    val metadata: Map<String, Any>? = null
)
