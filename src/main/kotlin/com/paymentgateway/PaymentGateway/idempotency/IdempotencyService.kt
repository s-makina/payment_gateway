package com.paymentgateway.PaymentGateway.idempotency

import com.paymentgateway.PaymentGateway.core.exceptions.IdempotencyConflictException
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import com.paymentgateway.PaymentGateway.core.util.Hashing
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class IdempotencyService(
    private val repository: IdempotencyKeyRepository,
    private val objectMapper: JsonMapper
) {

    suspend fun findExisting(key: String): IdempotencyKeyEntity? = repository.findByIdempotencyKey(key)

    /**
     * Returns the previously stored response for a repeated idempotent request.
     * Throws [IdempotencyConflictException] when the same key is reused with a
     * different request payload.
     */
    suspend fun replayOrThrow(key: String, requestHash: String): PaymentResponse {
        val existing = repository.findByIdempotencyKey(key) ?: throw IllegalStateException("No idempotency record for key: $key")
        if (existing.requestHash != requestHash) {
            throw IdempotencyConflictException(key)
        }
        val payload = existing.responsePayload
            ?: throw IllegalStateException("Idempotency record for key $key has no stored response")
        return objectMapper.convertValue(payload, PaymentResponse::class.java)
    }

    suspend fun record(key: String, requestHash: String, transactionId: UUID, response: PaymentResponse) {
        repository.save(
            IdempotencyKeyEntity(
                idempotencyKey = key,
                requestHash = requestHash,
                transactionId = transactionId,
                responsePayload = objectMapper.convertValue(
                    response,
                    object : TypeReference<Map<String, Any>>() {}
                ),
                status = "COMPLETED",
                expiresAt = Instant.now().plus(Duration.ofHours(24))
            )
        )
    }

    fun hashRequest(request: PaymentRequest): String {
        val canonical = objectMapper.writeValueAsString(request.copy(idempotencyKey = null))
        return Hashing.sha256Hex(canonical)
    }
}