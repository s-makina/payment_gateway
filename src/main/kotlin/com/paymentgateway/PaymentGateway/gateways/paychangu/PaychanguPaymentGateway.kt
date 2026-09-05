package com.paymentgateway.PaymentGateway.gateways.paychangu

import com.paymentgateway.PaymentGateway.core.domain.GatewayCapability
import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import com.paymentgateway.PaymentGateway.core.exceptions.WebhookVerificationException
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentGateway
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import com.paymentgateway.PaymentGateway.core.gateway.PaymentStatusResult
import com.paymentgateway.PaymentGateway.core.gateway.WebhookProcessingResult
import com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks.PaychanguWebhookHandler
import com.paymentgateway.PaymentGateway.gateways.paychangu.webhooks.PaychanguWebhookVerifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "payment.gateways.paychangu", name = ["enabled"], havingValue = "true")
class PaychanguPaymentGateway(
    private val mapper: PaychanguMapper,
    private val operationsClient: PaychanguOperationsClient,
    private val webhookVerifier: PaychanguWebhookVerifier,
    private val webhookHandler: PaychanguWebhookHandler
) : PaymentGateway {

    override fun getGatewayType(): GatewayType = GatewayType.PAYCHANGU

    override fun getCapabilities(): Set<GatewayCapability> = setOf(
        GatewayCapability.COLLECTIONS,
        GatewayCapability.DIRECT_CHARGE,
        GatewayCapability.WEBHOOKS
    )

    override suspend fun initiatePayment(request: PaymentRequest): PaymentResponse {
        return when (request.paymentType) {
            PaymentType.COLLECTION -> initiateCheckout(request)
            PaymentType.DIRECT_CHARGE -> initiateDirectCharge(request)
            else -> throw IllegalArgumentException(
                "Gateway PAYCHANGU does not support payment type: ${request.paymentType}"
            )
        }
    }

    private suspend fun initiateCheckout(request: PaymentRequest): PaymentResponse {
        val checkoutRequest = mapper.toCheckoutRequest(request)
        val initiate = operationsClient.initiateCheckout(checkoutRequest)
        return mapper.toPaymentResponse(initiate.checkout, request, initiate.rawResponse)
    }

    private suspend fun initiateDirectCharge(request: PaymentRequest): PaymentResponse {
        val chargeRequest = mapper.toDirectChargeRequest(request)
        val initiate = operationsClient.initiateDirectCharge(chargeRequest)
        return mapper.toDirectChargeResponse(initiate.charge, request, initiate.rawResponse)
    }

    override suspend fun getPaymentStatus(transactionReference: String): PaymentStatusResult {
        // The gateway's verify endpoint is the source of truth. Checkout payments
        // are keyed by tx_ref (/verify-payment); direct charges by their charge_id
        // (/mobile-money/payments/{chargeId}/verify — Paychangu excludes direct
        // charges from /verify-payment). Charge_ids issued by this adapter carry
        // the PaychanguMapper.DIRECT_CHARGE_PREFIX marker, which makes the routing
        // deterministic. An unknown reference (404) is normalized to PENDING,
        // which keeps an initiated transaction in its current awaiting state until
        // a webhook or a later poll resolves it.
        val lookup = if (transactionReference.startsWith(PaychanguMapper.DIRECT_CHARGE_PREFIX)) {
            operationsClient.verifyDirectCharge(transactionReference)
        } else {
            val checkoutLookup = operationsClient.verifyPayment(transactionReference)
            if (checkoutLookup.transaction == null) {
                // Paychangu excludes direct charges from /verify-payment, so a
                // non-prefixed charge id (e.g. learned from a webhook) is retried
                // against the direct-charge verification endpoint before being
                // treated as unknown.
                operationsClient.verifyDirectCharge(transactionReference)
            } else {
                checkoutLookup
            }
        }
        val transaction = lookup.transaction
            ?: return PaymentStatusResult(
                gatewayTransactionId = transactionReference,
                status = PaymentStatus.PENDING,
                responseMessage = "Transaction not found at gateway"
            )
        return mapper.toPaymentStatusResult(transaction, lookup.lookupReference ?: transactionReference, lookup.rawResponse)
    }

    override suspend fun processWebhook(request: GatewayWebhookRequest): WebhookProcessingResult {
        if (!webhookVerifier.verify(request.rawBody, request.signature)) {
            throw WebhookVerificationException("Invalid webhook signature for event: ${request.eventType}")
        }
        val mapping = webhookHandler.map(request)
        return WebhookProcessingResult(
            transactionReference = mapping.transactionReference,
            eventType = mapping.eventType,
            newStatus = mapping.newStatus,
            processed = false
        )
    }
}
