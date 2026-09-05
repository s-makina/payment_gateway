package com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks

import com.paymentgateway.PaymentGateway.core.util.Hashing
import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * Verifies Paychangu webhook requests. Paychangu sends a `Signature` header
 * containing the HMAC-SHA256 hex hash of the raw request body keyed with the
 * webhook secret from the merchant dashboard. The comparison is timing-safe.
 */
@Component
class PaychanguWebhookVerifier(
    private val properties: PaychanguProperties
) {

    fun verify(rawBody: String?, signature: String?): Boolean {
        if (rawBody.isNullOrBlank() || signature.isNullOrBlank()) return false
        if (properties.webhookSecret.isBlank()) return false

        val provided = signature.trim()
        val expected = Hashing.hmacSha256Hex(rawBody, properties.webhookSecret)

        return MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), provided.toByteArray(Charsets.UTF_8))
    }
}
