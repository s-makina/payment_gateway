package com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * `data` of `GET /verify-payment/{tx_ref}` (checkout payments) and
 * `GET /mobile-money/payments/{chargeId}/verify` (direct charges). The two
 * shapes overlap almost fully, so one DTO serves both.
 *
 * [status] is the payment status: "success" | "pending" | "failed" | "cancelled".
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguTransactionData(
    @JsonProperty("event_type")
    val eventType: String? = null,
    @JsonProperty("tx_ref")
    val txRef: String? = null,
    @JsonProperty("mode")
    val mode: String? = null,
    @JsonProperty("type")
    val type: String? = null,
    @JsonProperty("status")
    val status: String? = null,
    @JsonProperty("reference")
    val reference: String? = null,
    @JsonProperty("charge_id")
    val chargeId: String? = null,
    @JsonProperty("currency")
    val currency: String? = null,
    @JsonProperty("amount")
    val amount: java.math.BigDecimal? = null,
    @JsonProperty("charges")
    val charges: java.math.BigDecimal? = null,
    @JsonProperty("number_of_attempts")
    val numberOfAttempts: Int? = null,
    @JsonProperty("created_at")
    val createdAt: String? = null,
    @JsonProperty("updated_at")
    val updatedAt: String? = null,
    @JsonProperty("authorization")
    val authorization: PaychanguAuthorization? = null,
    @JsonProperty("customer")
    val customer: PaychanguCustomer? = null,
    @JsonProperty("mobile_money")
    val mobileMoney: PaychanguMobileMoney? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguAuthorization(
    @JsonProperty("channel")
    val channel: String? = null,
    @JsonProperty("brand")
    val brand: String? = null,
    @JsonProperty("provider")
    val provider: String? = null,
    @JsonProperty("card_number")
    val cardNumber: String? = null,
    @JsonProperty("mobile_number")
    val mobileNumber: String? = null,
    @JsonProperty("completed_at")
    val completedAt: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PaychanguCustomer(
    @JsonProperty("email")
    val email: String? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("last_name")
    val lastName: String? = null
)
