package com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request

import java.math.BigDecimal

data class OneKhusaRequestToPayRequest(
    val merchantAccountNumber: Long,
    val transactionAmount: BigDecimal,
    val transactionDescription: String,
    val referenceNumber: String,
    val capturedBy: String
)