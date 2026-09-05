package com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Envelope shared by every Paychangu response: `{ status, message, data }`.
 * `status` is "success" or "failed" at the API level (not the payment status).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguEnvelope<T>(
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("message")
    val message: String? = null,
    @JsonProperty("data")
    val data: T? = null
)

/** `data` of the checkout initiate response (`POST /payment`). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguCheckoutData(
    @JsonProperty("event")
    val event: String? = null,
    @JsonProperty("checkout_url")
    val checkoutUrl: String? = null,
    @JsonProperty("data")
    val inner: PaychanguCheckoutInner? = null
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PaychanguCheckoutInner(
        @JsonProperty("tx_ref")
        val txRef: String? = null,
        @JsonProperty("currency")
        val currency: String? = null,
        @JsonProperty("amount")
        val amount: java.math.BigDecimal? = null,
        @JsonProperty("mode")
        val mode: String? = null,
        @JsonProperty("status")
        val status: String? = null
    )
}

/**
 * `data` of the direct-charge initiate response
 * (`POST /mobile-money/payments/initialize`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguDirectChargeData(
    @JsonProperty("charge_id")
    val chargeId: String? = null,
    @JsonProperty("ref_id")
    val refId: String? = null,
    @JsonProperty("trans_id")
    val transId: String? = null,
    @JsonProperty("amount")
    val amount: java.math.BigDecimal? = null,
    @JsonProperty("currency")
    val currency: String? = null,
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("mobile")
    val mobile: String? = null,
    @JsonProperty("attempts")
    val attempts: Int? = null,
    @JsonProperty("mode")
    val mode: String? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    @JsonProperty("completed_at")
    val completedAt: String? = null,
    @JsonProperty("mobile_money")
    val mobileMoney: PaychanguMobileMoney? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguMobileMoney(
    @JsonProperty("name")
    val name: String? = null,
    @JsonProperty("ref_id")
    val refId: String? = null,
    @JsonProperty("country")
    val country: String? = null
)
