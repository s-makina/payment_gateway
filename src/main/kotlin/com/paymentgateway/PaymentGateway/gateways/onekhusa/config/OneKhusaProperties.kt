package com.paymentgateway.PaymentGateway.gateways.onekhusa.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.gateways.onekhusa")
data class OneKhusaProperties(
    val enabled: Boolean = false,
    val environment: String = "sandbox",
    val baseUrl: String = "https://api.onekhusa.com/sandbox/v1",
    val apiKey: String = "",
    val apiSecret: String = "",
    val organisationId: String = "",
    val merchantAccountNumber: Long = 0,
    val webhookSecret: String = "",
    val capturedBy: String = "salvation.developer@gmail.com"
)