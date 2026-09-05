package com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus

data class OneKhusaWebhookMapping(
    val transactionReference: String,
    val eventType: String,
    val newStatus: PaymentStatus
)