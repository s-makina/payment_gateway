package com.paymentgateway.PaymentGateway.gateways.onekhusa.webhooks

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.exceptions.WebhookVerificationException
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import org.springframework.stereotype.Component

@Component
class OneKhusaWebhookHandler {

    fun map(request: GatewayWebhookRequest): OneKhusaWebhookMapping {
        val transactionReference = request.payload["transactionReferenceNumber"] as? String
            ?: throw WebhookVerificationException(
                "Webhook payload missing transactionReferenceNumber for event: ${request.eventType}"
            )

        return OneKhusaWebhookMapping(
            transactionReference = transactionReference,
            eventType = request.eventType,
            newStatus = mapStatus(request.eventType, request.payload["transactionStatusCode"] as? String)
        )
    }

    private fun mapStatus(eventType: String, statusCode: String?): PaymentStatus {
        if (eventType.endsWith(".success")) return PaymentStatus.SUCCESS
        if (eventType.endsWith(".reversed")) return PaymentStatus.REVERSED
        if (eventType.endsWith(".expired")) return PaymentStatus.EXPIRED
        if (eventType.endsWith(".failed")) return PaymentStatus.FAILED
        return when (statusCode) {
            "S" -> PaymentStatus.SUCCESS
            "F" -> PaymentStatus.FAILED
            "R" -> PaymentStatus.REVERSED
            else -> PaymentStatus.PENDING
        }
    }
}