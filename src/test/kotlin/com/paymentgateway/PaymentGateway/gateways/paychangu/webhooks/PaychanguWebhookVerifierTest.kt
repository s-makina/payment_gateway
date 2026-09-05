package com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks

import com.paymentgateway.PaymentGateway.core.util.Hashing
import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaychanguWebhookVerifierTest {

    private val verifier = PaychanguWebhookVerifier(
        PaychanguProperties(secretKey = "sec-test", webhookSecret = "test-webhook-secret")
    )

    private val body = """{"event_type":"checkout.payment","tx_ref":"PA1","status":"success"}"""

    @Test
    fun `accepts a valid HMAC-SHA256 hex signature`() {
        val signature = Hashing.hmacSha256Hex(body, "test-webhook-secret")
        assertTrue(verifier.verify(body, signature))
    }

    @Test
    fun `rejects a tampered body`() {
        val signature = Hashing.hmacSha256Hex(body, "test-webhook-secret")
        assertFalse(verifier.verify(body + " ", signature))
    }

    @Test
    fun `rejects a signature computed with a different secret`() {
        val signature = Hashing.hmacSha256Hex(body, "wrong-secret")
        assertFalse(verifier.verify(body, signature))
    }

    @Test
    fun `rejects missing inputs and unconfigured secret`() {
        assertFalse(verifier.verify(null, "sig"))
        assertFalse(verifier.verify(body, null))
        assertFalse(verifier.verify(body, ""))
        assertFalse(
            PaychanguWebhookVerifier(PaychanguProperties(secretKey = "sec")).verify(body, "sig")
        )
    }

    @Test
    fun `signature comparison is not vulnerable to length-based timing`() {
        // MessageDigest.isEqual handles differing lengths safely; this merely
        // documents that a short/long garbage signature is rejected, not an error.
        assertFalse(verifier.verify(body, "abc"))
        assertFalse(verifier.verify(body, Hashing.hmacSha256Hex(body, "test-webhook-secret") + "00"))
    }
}
