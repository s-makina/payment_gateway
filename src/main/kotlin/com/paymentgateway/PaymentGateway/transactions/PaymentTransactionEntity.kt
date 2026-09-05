package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "payment_transactions")
class PaymentTransactionEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", columnDefinition = "UUID")
    val id: UUID = UUID.randomUUID(),

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, length = 50)
    var gateway: GatewayType,

    @Column(name = "gateway_transaction_id", length = 100)
    var gatewayTransactionId: String? = null,

    @Column(name = "merchant_reference", nullable = false, length = 50)
    var merchantReference: String,

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    var amount: BigDecimal,

    @Column(name = "currency", nullable = false, length = 3)
    var currency: String = "MWK",

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 50)
    var paymentType: PaymentType,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    var status: PaymentStatus = PaymentStatus.CREATED,

    @Column(name = "idempotency_key", length = 80)
    var idempotencyKey: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "gateway_metadata", columnDefinition = "jsonb")
    var gatewayMetadata: Map<String, Any>? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),

    @Column(name = "completed_at")
    var completedAt: Instant? = null
) {

    /**
     * Applies a status transition guarded by the PaymentStatus state machine.
     * Throws [IllegalStateException] when the transition is not allowed.
     */
    fun transitionTo(target: PaymentStatus) {
        require(status.canTransitionTo(target)) {
            "Invalid status transition from $status to $target for transaction $id"
        }
        status = target
        updatedAt = Instant.now()
    }
}