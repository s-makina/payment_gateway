package com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus

/**
 * Provider-neutral result of mapping a Paychangu webhook event.
 *
 * [transactionReference] is the `tx_ref` (checkout flow) or `reference`
 * (direct API flow) used to locate the local transaction; [chargeId] is the
 * gateway-assigned charge id when the payload carries one.
 */
data class PaychanguWebhookMapping(
    val transactionReference: String,
    val eventType: String,
    val newStatus: PaymentStatus,
    val chargeId: String? = null
)
