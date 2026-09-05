package com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request

data class OneKhusaGetTransactionRequest(
    val merchantAccountNumber: Long,
    val transactionReferenceNumber: String
)