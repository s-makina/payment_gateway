package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.GatewayType
import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.domain.PaymentType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class PaymentStatusResponse(
    val transactionId: UUID,
    val gateway: GatewayType,
    val merchantReference: String,
    val status: PaymentStatus,
    val paymentType: PaymentType,
    val amount: BigDecimal,
    val currency: String,
    val gatewayTransactionId: String? = null,
    val completedAt: Instant? = null
) {
    companion object {
        fun from(entity: PaymentTransactionEntity): PaymentStatusResponse = PaymentStatusResponse(
            transactionId = entity.id,
            gateway = entity.gateway,
            merchantReference = entity.merchantReference,
            status = entity.status,
            paymentType = entity.paymentType,
            amount = entity.amount,
            currency = entity.currency,
            gatewayTransactionId = entity.gatewayTransactionId,
            completedAt = entity.completedAt
        )
    }
}