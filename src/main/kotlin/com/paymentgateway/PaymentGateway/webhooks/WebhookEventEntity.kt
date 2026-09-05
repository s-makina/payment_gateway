package com.paymentgateway.PaymentGateway.webhooks

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "webhook_events")
class WebhookEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "event_type", nullable = false, length = 50)
    var eventType: String,

    @Column(name = "transaction_reference", nullable = false, length = 50)
    var transactionReference: String,

    @Column(name = "payload_hash", nullable = false, length = 64)
    var payloadHash: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    var payload: Map<String, Any>,

    @Column(name = "signature", length = 255)
    var signature: String? = null,

    @Column(name = "verification_status", nullable = false, length = 50)
    var verificationStatus: String = "PENDING",

    @Column(name = "processing_status", nullable = false, length = 50)
    var processingStatus: String = "PENDING",

    @Column(name = "received_at", nullable = false)
    var receivedAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    var processedAt: Instant? = null
)