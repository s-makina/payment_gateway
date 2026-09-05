package com.paymentgateway.PaymentGateway.webhooks

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface WebhookEventRepository : JpaRepository<WebhookEventEntity, UUID> {

    fun findByTransactionReferenceAndEventType(
        transactionReference: String,
        eventType: String
    ): WebhookEventEntity?
}