package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface TransactionRepository : JpaRepository<PaymentTransactionEntity, UUID> {

    fun findByGatewayTransactionId(gatewayTransactionId: String): PaymentTransactionEntity?

    fun findTopByMerchantReferenceOrderByCreatedAtDesc(merchantReference: String): PaymentTransactionEntity?

    fun findByStatusIn(statuses: Collection<PaymentStatus>): List<PaymentTransactionEntity>

    /**
     * Matches transactions whose gateway_metadata JSONB contains the given fragment,
     * e.g. {"timedAccountNumber":"11005533"}. Postgres-specific (JSONB @> operator).
     */
    @Query(
        value = "SELECT * FROM payment_transactions WHERE gateway_metadata @> CAST(:fragment AS jsonb) LIMIT 1",
        nativeQuery = true
    )
    fun findByMetadataContaining(fragment: String): PaymentTransactionEntity?
}