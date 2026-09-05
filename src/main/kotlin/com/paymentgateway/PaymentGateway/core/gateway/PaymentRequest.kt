package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class PaymentRequest(
    @field:NotNull
    val gateway: GatewayType,
    @field:NotNull
    val paymentType: PaymentType,
    @field:NotNull
    @field:Positive
    val amount: BigDecimal,
    @field:Size(min = 3, max = 3)
    val currency: String = "MWK",
    @field:NotBlank
    @field:Size(max = 50)
    val reference: String,
    val description: String? = null,
    val customerId: String? = null,
    @field:Size(min = 15, max = 80)
    @field:Pattern(regexp = "^[A-Za-z0-9-]+$")
    val idempotencyKey: String? = null,
    val metadata: Map<String, Any>? = null
)
