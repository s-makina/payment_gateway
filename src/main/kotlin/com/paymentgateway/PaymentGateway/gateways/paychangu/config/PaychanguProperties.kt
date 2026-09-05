package com.paymentgateway.PaymentGateway.gateways.paychangu.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "payment.gateways.paychangu")
data class PaychanguProperties(
    val enabled: Boolean = false,
    val environment: String = "sandbox",
    val baseUrl: String = "https://api.paychangu.com",
    val secretKey: String = "",
    val webhookSecret: String = "",
    val callbackUrl: String = "",
    val returnUrl: String = "",
    /** Optional override of the checkout screen title/description. */
    val customizationTitle: String = "",
) {

    /**
     * Validates the settings required to initiate a hosted-checkout payment.
     * Invoked at startup (fail fast when the gateway is enabled) and again by
     * the mapper as defence in depth. Uses [require] so a blank/missing value
     * surfaces as a clear configuration error rather than a cryptic gateway
     * failure.
     */
    fun requireValidCheckoutConfig() {
        require(secretKey.isNotBlank()) {
            "Paychangu secret key is not configured (PAYCHANGU_SECRET_KEY)"
        }
        require(callbackUrl.isNotBlank()) {
            "Paychangu callback URL is not configured (PAYCHANGU_CALLBACK_URL)"
        }
        require(returnUrl.isNotBlank()) {
            "Paychangu return URL is not configured (PAYCHANGU_RETURN_URL)"
        }
    }

    fun requireValidDirectChargeConfig() {
        requireValidCheckoutConfig()
        require(webhookSecret.isNotBlank()) {
            "Paychangu webhook secret is not configured (PAYCHANGU_WEBHOOK_SECRET)"
        }
    }
}
