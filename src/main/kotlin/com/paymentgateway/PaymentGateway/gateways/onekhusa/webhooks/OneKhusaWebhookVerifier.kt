package com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks

import com.paymentgateway.PaymentGateway.core.util.Hashing
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class OneKhusaWebhookVerifier(
    private val properties: OneKhusaProperties
) {

    /**
     * Computes HMAC-SHA512 over the raw request body with the webhook secret and
     * compares it to the provided signature using a timing-safe comparison.
     * Both hex and base64 encodings are accepted.
     */
    fun verify(rawBody: String?, signature: String?): Boolean {
        if (rawBody.isNullOrBlank() || signature.isNullOrBlank()) return false
        if (properties.webhookSecret.isBlank()) return false

        val provided = signature.trim()
        val hexExpected = Hashing.hmacSha512Hex(rawBody, properties.webhookSecret)
        val base64Expected = Hashing.hmacSha512Base64(rawBody, properties.webhookSecret)

        return MessageDigest.isEqual(hexExpected.toByteArray(Charsets.UTF_8), provided.toByteArray(Charsets.UTF_8)) ||
            MessageDigest.isEqual(base64Expected.toByteArray(Charsets.UTF_8), provided.toByteArray(Charsets.UTF_8))
    }
}