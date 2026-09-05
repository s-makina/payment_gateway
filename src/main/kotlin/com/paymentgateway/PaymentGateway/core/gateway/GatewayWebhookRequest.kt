package com.paymentgateway.PaymentGateway.core.gateway

data class GatewayWebhookRequest(
    val eventType: String,
    val payload: Map<String, Any>,
    val signature: String? = null,
    val rawBody: String? = null
)
