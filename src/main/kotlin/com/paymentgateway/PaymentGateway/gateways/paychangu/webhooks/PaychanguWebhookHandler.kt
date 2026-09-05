package com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.exceptions.WebhookVerificationException
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import org.springframework.stereotype.Component

/**
 * Maps a signature-verified Paychangu webhook to a provider-neutral
 * [PaychanguWebhookMapping].
 *
 * Payloads carry the lookup key in `tx_ref` (checkout) or `charge_id` (direct
 * API payments — the `reference` field there is a gateway-assigned number that
 * does not match any local identifier); `event_type` discriminates the flow
 * (`checkout.payment`, `api.charge.payment`, `api.payout`, ...).
 */
@Component
class PaychanguWebhookHandler {

    fun map(request: GatewayWebhookRequest): PaychanguWebhookMapping {
        val payload = request.payload

        val txRef = payload.string("tx_ref")
        val reference = payload.string("reference")
        val chargeId = payload.string("charge_id")

        val transactionReference = txRef
            ?: chargeId
            ?: reference
            ?: throw WebhookVerificationException(
                "Webhook payload missing tx_ref/charge_id/reference for event: ${request.eventType}"
            )

        return PaychanguWebhookMapping(
            transactionReference = transactionReference,
            eventType = payload.string("event_type") ?: request.eventType,
            newStatus = mapStatus(payload.string("status")),
            chargeId = chargeId
        )
    }

    /**
     * Paychangu payment statuses are lowercase words: success | pending |
     * failed | cancelled | reversed. Unknown values map to PENDING so a new
     * vocabulary cannot accidentally advance a transaction to a terminal state.
     */
    fun mapStatus(status: String?): PaymentStatus = when (status?.lowercase()) {
        "success", "successful" -> PaymentStatus.SUCCESS
        "failed" -> PaymentStatus.FAILED
        "cancelled", "canceled" -> PaymentStatus.FAILED
        "reversed" -> PaymentStatus.REVERSED
        else -> PaymentStatus.PENDING
    }

    private fun Map<String, Any>.string(key: String): String? =
        (this[key] as? String)?.takeIf { it.isNotBlank() }
}
