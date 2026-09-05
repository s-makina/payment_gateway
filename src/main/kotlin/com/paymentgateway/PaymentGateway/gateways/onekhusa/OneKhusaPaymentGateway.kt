package com.paymentgateway.PaymentGateway.gateways.onekhusa

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
import com.paymentgateway.PaymentGateway.gateways.onekhusa.collections.OneKhusaCollectionMapper
import com.paymentgateway.PaymentGateway.gateways.onekhusa.collections.OneKhusaCollectionsClient
import com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks.OneKhusaWebhookHandler
import com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks.OneKhusaWebhookVerifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.util.UUID

@Component
@ConditionalOnProperty(prefix = "payment.gateways.onekhusa", name = ["enabled"], havingValue = "true")
class OneKhusaPaymentGateway(
    private val mapper: OneKhusaCollectionMapper,
    private val collectionsClient: OneKhusaCollectionsClient,
    private val webhookVerifier: OneKhusaWebhookVerifier,
    private val webhookHandler: OneKhusaWebhookHandler
) : PaymentGateway {

    override fun getGatewayType(): GatewayType = GatewayType.ONEKHUSA

    override fun getCapabilities(): Set<GatewayCapability> = setOf(
        GatewayCapability.COLLECTIONS,
        GatewayCapability.REQUEST_TO_PAY,
        GatewayCapability.WEBHOOKS
    )

    override suspend fun initiatePayment(request: PaymentRequest): PaymentResponse {
        require(request.paymentType == PaymentType.REQUEST_TO_PAY || request.paymentType == PaymentType.COLLECTION) {
            "Gateway ONEKHUSA does not support payment type: ${request.paymentType}"
        }
        val idempotencyKey = request.idempotencyKey ?: UUID.randomUUID().toString()
        val oneKhusaRequest = mapper.toRequestToPayRequest(request)
        val initiate = collectionsClient.initiateRequestToPay(oneKhusaRequest, idempotencyKey)
        return mapper.toPaymentResponse(initiate.response, request, initiate.rawResponse)
    }

    override suspend fun getPaymentStatus(transactionReference: String): PaymentStatusResult {
        // The gateway's check endpoint is the source of truth: every lookup is
        // forwarded to /collections/getTransaction regardless of what the
        // reference looks like. A 204 (transaction not found) is normalized to
        // PENDING, which keeps a request-to-pay in AWAITING_CUSTOMER_PAYMENT and
        // lets the reconciliation job expire it locally once its TAN lapses.
        val lookup = collectionsClient.getTransaction(transactionReference)
        val response = lookup.response
            ?: return PaymentStatusResult(
                gatewayTransactionId = transactionReference,
                status = PaymentStatus.PENDING,
                responseMessage = "Transaction not found at gateway"
            )
        return mapper.toPaymentStatusResult(response, transactionReference, lookup.rawResponse)
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