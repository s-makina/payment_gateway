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
) {

    /**
     * Validates the settings required to initiate a collection (request-to-pay).
     * Invoked at startup (fail fast when the gateway is enabled) and again by the
     * mapper as a defence in depth. Uses [require] so a blank/missing value surfaces
     * as a clear configuration error rather than a cryptic gateway failure.
     */
    fun requireValidCollectionConfig() {
        require(merchantAccountNumber in MERCHANT_ACCOUNT_MIN..MERCHANT_ACCOUNT_MAX) {
            "OneKhusa merchant account number is not configured (ONEKHUSA_MERCHANT_ACCOUNT_NUMBER must be 8 digits)"
        }
        require(capturedBy.isNotBlank()) {
            "OneKhusa capturedBy is not configured (ONEKHUSA_CAPTURED_BY must be a background user under the merchant account)"
        }
    }

    private companion object {
        const val MERCHANT_ACCOUNT_MIN = 10_000_000L
        const val MERCHANT_ACCOUNT_MAX = 99_999_999L
    }
}