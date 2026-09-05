package com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.exceptions.WebhookVerificationException
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PaychanguWebhookHandlerTest {

    private val handler = PaychanguWebhookHandler()

    @Test
    fun `maps a checkout payment webhook by tx_ref`() {
        val mapping = handler.map(
            GatewayWebhookRequest(
                eventType = "checkout.payment",
                payload = mapOf(
                    "event_type" to "checkout.payment",
                    "tx_ref" to "INV-10001",
                    "status" to "success",
                    "reference" to "26262633201"
                )
            )
        )
        assertEquals("INV-10001", mapping.transactionReference)
        assertEquals("checkout.payment", mapping.eventType)
        assertEquals(PaymentStatus.SUCCESS, mapping.newStatus)
        assertNull(mapping.chargeId)
    }

    @Test
    fun `maps a direct API charge webhook by charge_id`() {
        val mapping = handler.map(
            GatewayWebhookRequest(
                eventType = "api.charge.payment",
                payload = mapOf(
                    "event_type" to "api.charge.payment",
                    "charge_id" to "PDC-INV10001",
                    "reference" to "71308131545",
                    "status" to "success"
                )
            )
        )
        assertEquals("PDC-INV10001", mapping.transactionReference)
        assertEquals("PDC-INV10001", mapping.chargeId)
        assertEquals(PaymentStatus.SUCCESS, mapping.newStatus)
    }

    @Test
    fun `uses event_type from the payload when present`() {
        val mapping = handler.map(
            GatewayWebhookRequest(
                eventType = "header-fallback",
                payload = mapOf("event_type" to "api.payout", "tx_ref" to "T1", "status" to "failed")
            )
        )
        assertEquals("api.payout", mapping.eventType)
    }

    @Test
    fun `falls back to the transport event type when payload omits event_type`() {
        val mapping = handler.map(
            GatewayWebhookRequest(
                eventType = "checkout.payment",
                payload = mapOf("tx_ref" to "T1", "status" to "pending")
            )
        )
        assertEquals("checkout.payment", mapping.eventType)
    }

    @Test
    fun `maps payment statuses`() {
        assertEquals(PaymentStatus.SUCCESS, handler.mapStatus("success"))
        assertEquals(PaymentStatus.FAILED, handler.mapStatus("failed"))
        assertEquals(PaymentStatus.FAILED, handler.mapStatus("cancelled"))
        assertEquals(PaymentStatus.REVERSED, handler.mapStatus("reversed"))
        assertEquals(PaymentStatus.PENDING, handler.mapStatus("pending"))
        assertEquals(PaymentStatus.PENDING, handler.mapStatus(null))
        assertEquals(PaymentStatus.PENDING, handler.mapStatus("mystery"))
    }

    @Test
    fun `rejects payloads without any usable reference`() {
        val ex = assertFailsWith<WebhookVerificationException> {
            handler.map(
                GatewayWebhookRequest(
                    eventType = "checkout.payment",
                    payload = mapOf("event_type" to "checkout.payment", "status" to "success")
                )
            )
        }
        assertEquals(
            "Webhook payload missing tx_ref/charge_id/reference for event: checkout.payment",
            ex.message
        )
    }

    @Test
    fun `ignores blank reference values`() {
        assertFailsWith<WebhookVerificationException> {
            handler.map(
                GatewayWebhookRequest(
                    eventType = "checkout.payment",
                    payload = mapOf("tx_ref" to "  ", "reference" to "")
                )
            )
        }
    }
}
