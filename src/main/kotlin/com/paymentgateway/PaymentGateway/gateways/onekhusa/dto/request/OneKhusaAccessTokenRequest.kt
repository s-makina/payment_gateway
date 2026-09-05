package com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request

data class OneKhusaAccessTokenRequest(
    val apiKey: String,
    val apiSecret: String,
    val organisationId: String,
    val merchantAccountNumber: Long
)