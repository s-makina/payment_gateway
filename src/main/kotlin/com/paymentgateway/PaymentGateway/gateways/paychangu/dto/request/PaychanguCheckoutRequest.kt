package com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Body of `POST /payment` (hosted checkout / "Initiate Transaction").
 * Paychangu's wire format is snake_case; [JsonProperty] renames each field.
 * Amount is sent as a string per the API contract.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaychanguCheckoutRequest(
    @JsonProperty("amount")
    val amount: String,
    @JsonProperty("currency")
    val currency: String,
    @JsonProperty("tx_ref")
    val txRef: String,
    @JsonProperty("callback_url")
    val callbackUrl: String,
    @JsonProperty("return_url")
    val returnUrl: String,
    @JsonProperty("first_name")
    val firstName: String? = null,
    @JsonProperty("last_name")
    val lastName: String? = null,
    @JsonProperty("email")
    val email: String? = null,
    @JsonProperty("customization")
    val customization: PaychanguCustomization? = null,
    @JsonProperty("meta")
    val meta: Map<String, Any>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PaychanguCustomization(
    @JsonProperty("title")
    val title: String? = null,
    @JsonProperty("description")
    val description: String? = null
)
