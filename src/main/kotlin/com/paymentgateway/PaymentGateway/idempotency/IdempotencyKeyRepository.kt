package com.paymentgateway.PaymentGateway.idempotency

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface IdempotencyKeyRepository : JpaRepository<IdempotencyKeyEntity, UUID> {

    fun findByIdempotencyKey(idempotencyKey: String): IdempotencyKeyEntity?
}