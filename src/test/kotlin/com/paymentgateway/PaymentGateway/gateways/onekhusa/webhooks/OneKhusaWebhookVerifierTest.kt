package com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks

import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OneKhusaWebhookVerifierTest {

    private val secret = "test-webhook-secret"
    private val verifier = OneKhusaWebhookVerifier(OneKhusaProperties(webhookSecret = secret))

    @Test
    fun `accepts valid hex signature`() {
        val body = """{"transactionReferenceNumber":"TXN123","transactionStatusCode":"S"}"""
        assertTrue(verifier.verify(body, hmacHex(body)))
    }

    @Test
    fun `accepts valid base64 signature`() {
        val body = """{"transactionReferenceNumber":"TXN123"}"""
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        val base64 = java.util.Base64.getEncoder().encodeToString(mac.doFinal(body.toByteArray(Charsets.UTF_8)))
        assertTrue(verifier.verify(body, base64))
    }

    @Test
    fun `rejects tampered body`() {
        val body = """{"transactionReferenceNumber":"TXN123"}"""
        val tampered = """{"transactionReferenceNumber":"TXN999"}"""
        assertFalse(verifier.verify(tampered, hmacHex(body)))
    }

    @Test
    fun `rejects missing body or signature`() {
        assertFalse(verifier.verify(null, "abc"))
        assertFalse(verifier.verify("""{"a":1}""", null))
        assertFalse(verifier.verify("""{"a":1}""", ""))
    }

    @Test
    fun `rejects when webhook secret is not configured`() {
        val noSecret = OneKhusaWebhookVerifier(OneKhusaProperties(webhookSecret = ""))
        assertFalse(noSecret.verify("""{"a":1}""", "anything"))
    }

    private fun hmacHex(body: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        return HexFormat.of().formatHex(mac.doFinal(body.toByteArray(Charsets.UTF_8)))
    }
}