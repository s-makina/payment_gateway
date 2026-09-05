package com.paymentgateway.PaymentGateway.idempotency

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
@Table(name = "idempotency_keys")
class IdempotencyKeyEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "idempotency_key", nullable = false, length = 80)
    var idempotencyKey: String,

    @Column(name = "request_hash", nullable = false, length = 64)
    var requestHash: String,

    @Column(name = "transaction_id", nullable = false, columnDefinition = "UUID")
    var transactionId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_payload", columnDefinition = "jsonb")
    var responsePayload: Map<String, Any>? = null,

    @Column(name = "status", nullable = false, length = 50)
    var status: String = "PENDING",

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant
)