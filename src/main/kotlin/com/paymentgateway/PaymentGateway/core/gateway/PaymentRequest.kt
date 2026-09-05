package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

@Schema(description = "Provider-neutral payment initiation request")
data class PaymentRequest(
    @field:NotNull
    @Schema(description = "Gateway to route the payment through", example = "ONEKHUSA")
    val gateway: GatewayType,
    @field:NotNull
    @Schema(description = "Type of payment", example = "REQUEST_TO_PAY")
    val paymentType: PaymentType,
    @field:NotNull
    @field:Positive
    @Schema(description = "Payment amount in minor currency units", example = "10000")
    val amount: BigDecimal,
    @field:Size(min = 3, max = 3)
    @Schema(description = "ISO currency code", example = "MWK", defaultValue = "MWK")
    val currency: String = "MWK",
    @field:NotBlank
    @field:Size(max = 50)
    @Schema(description = "Merchant reference, unique per payment", example = "INV-10001")
    val reference: String,
    @Schema(description = "Human-readable description", example = "Test purchase")
    val description: String? = null,
    @Schema(description = "Merchant customer identifier")
    val customerId: String? = null,
    @field:Size(min = 15, max = 80)
    @field:Pattern(regexp = "^[A-Za-z0-9-]+$")
    @Schema(
        description = "Client-supplied idempotency key; reusing it returns the original result",
        example = "order-12345-abcde"
    )
    val idempotencyKey: String? = null,
    @Schema(description = "Free-form metadata forwarded to the gateway")
    val metadata: Map<String, Any>? = null,
    @field:Size(max = 500)
    @Schema(
        description = "Absolute URL the gateway redirects the customer to after payment " +
            "(used by gateways with hosted checkout, e.g. PAYCHANGU)",
        example = "https://shop.example.com/payments/INV-10001/callback"
    )
    val callbackUrl: String? = null,
    @field:Size(max = 500)
    @Schema(
        description = "Absolute URL the gateway redirects the customer to on cancel/failure " +
            "(used by gateways with hosted checkout, e.g. PAYCHANGU)",
        example = "https://shop.example.com/payments/INV-10001/return"
    )
    val returnUrl: String? = null
)
