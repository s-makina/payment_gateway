package com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Body of `POST /mobile-money/payments/initialize` (Direct MoMo Charge).
 * The customer's wallet is charged server-to-server; the customer confirms
 * via USSD/push prompt on their phone.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaychanguDirectChargeRequest(
    @JsonProperty("mobile")
    val mobile: String,
    @JsonProperty("mobile_money_operator_ref_id")
    val mobileMoneyOperatorRefId: String,
    @JsonProperty("amount")
    val amount: String,
    @JsonProperty("charge_id")
    val chargeId: String,
    @JsonProperty("email")
    val email: String? = null,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("last_name")
    val lastName: String? = null
)
