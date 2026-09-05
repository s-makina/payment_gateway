package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import com.paymentgateway.PaymentGateway.idempotency.IdempotencyKeyEntity
import com.paymentgateway.PaymentGateway.webhooks.WebhookEventEntity
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant

/**
 * New entities must leave [id] null so Spring Data's `save()` takes the
 * `persist()` path. A pre-assigned non-null ID makes `save()` call `merge()`,
 * which fails with StaleObjectStateException ("Row was already updated or
 * deleted") for rows that don't exist yet.
 */
class EntityIdGenerationTest {

    @Test
    fun `new payment transaction has null id until persisted`() {
        val entity = PaymentTransactionEntity(
            gateway = GatewayType.ONEKHUSA,
            merchantReference = "INV-10001",
            amount = BigDecimal("10000"),
            currency = "MWK",
            paymentType = PaymentType.REQUEST_TO_PAY
        )

        assertNull(entity.id)
    }

    @Test
    fun `new idempotency key has null id until persisted`() {
        val entity = IdempotencyKeyEntity(
            idempotencyKey = "key-123",
            requestHash = "hash",
            transactionId = java.util.UUID.randomUUID(),
            expiresAt = Instant.now().plusSeconds(3600)
        )

        assertNull(entity.id)
    }

    @Test
    fun `new webhook event has null id until persisted`() {
        val entity = WebhookEventEntity(
            eventType = "payrequest.success",
            transactionReference = "TXN123",
            payloadHash = "hash",
            payload = mapOf("transactionReferenceNumber" to "TXN123")
        )

        assertNull(entity.id)
    }
}
