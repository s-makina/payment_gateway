package com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response

data class OneKhusaRequestToPayResponse(
    val merchantAccountNumber: Long? = null,
    val timedAccountNumber: String? = null,
    val expiryDate: String? = null,
    val expiryInMinutes: Int? = null
)