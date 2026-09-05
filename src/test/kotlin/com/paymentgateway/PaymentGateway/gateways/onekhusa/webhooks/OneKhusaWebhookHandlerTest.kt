package com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.exceptions.WebhookVerificationException
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OneKhusaWebhookHandlerTest {

    private val handler = OneKhusaWebhookHandler()

    @Test
    fun `payrequest success maps to SUCCESS`() {
        val result = handler.map(webhook("payrequest.success"))
        assertEquals("TXN123", result.transactionReference)
        assertEquals(PaymentStatus.SUCCESS, result.newStatus)
    }

    @Test
    fun `payment success maps to SUCCESS`() {
        assertEquals(PaymentStatus.SUCCESS, handler.map(webhook("payment.success")).newStatus)
    }

    @Test
    fun `reversed events map to REVERSED`() {
        assertEquals(PaymentStatus.REVERSED, handler.map(webhook("payment.reversed")).newStatus)
        assertEquals(PaymentStatus.REVERSED, handler.map(webhook("payrequest.reversed")).newStatus)
    }

    @Test
    fun `expired and failed events map correctly`() {
        assertEquals(PaymentStatus.EXPIRED, handler.map(webhook("payrequest.expired")).newStatus)
        assertEquals(PaymentStatus.FAILED, handler.map(webhook("payrequest.failed")).newStatus)
    }

    @Test
    fun `transaction status code is used as fallback`() {
        assertEquals(PaymentStatus.FAILED, handler.map(webhook("payment.unknown", statusCode = "F")).newStatus)
        assertEquals(PaymentStatus.REVERSED, handler.map(webhook("payment.unknown", statusCode = "R")).newStatus)
    }

    @Test
    fun `missing transaction reference is rejected`() {
        assertFailsWith<WebhookVerificationException> {
            handler.map(GatewayWebhookRequest(eventType = "payment.success", payload = emptyMap()))
        }
    }

    private fun webhook(eventType: String, statusCode: String? = null): GatewayWebhookRequest {
        val payload = buildMap {
            put("transactionReferenceNumber", "TXN123")
            if (statusCode != null) put("transactionStatusCode", statusCode)
        }
        return GatewayWebhookRequest(eventType = eventType, payload = payload, rawBody = "{}")
    }
}