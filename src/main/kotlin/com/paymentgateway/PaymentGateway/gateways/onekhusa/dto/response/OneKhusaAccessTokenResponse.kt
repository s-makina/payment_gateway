package com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response

data class OneKhusaAccessTokenResponse(
    val accessToken: String,
    val expiresOn: String? = null,
    val expiryInMinutes: Int? = null
)