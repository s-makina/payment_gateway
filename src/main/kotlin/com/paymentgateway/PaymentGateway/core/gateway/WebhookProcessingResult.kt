package com.paymentgateway.PaymentGateway.core.gateway

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus

data class WebhookProcessingResult(
    val transactionReference: String,
    val eventType: String,
    val newStatus: PaymentStatus,
    val processed: Boolean,
    val duplicate: Boolean = false,
    val errorMessage: String? = null
)
