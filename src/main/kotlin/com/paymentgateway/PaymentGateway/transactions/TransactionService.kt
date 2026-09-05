package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.exceptions.TransactionNotFoundException
import com.paymentgateway.PaymentGateway.core.gateway.GatewayWebhookRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.core.gateway.PaymentRequest
import com.paymentgateway.PaymentGateway.core.gateway.PaymentResponse
import com.paymentgateway.PaymentGateway.core.gateway.WebhookProcessingResult
import com.paymentgateway.PaymentGateway.core.util.Hashing
import com.paymentgateway.PaymentGateway.idempotency.IdempotencyService
import com.paymentgateway.PaymentGateway.webhooks.WebhookEventEntity
import com.paymentgateway.PaymentGateway.webhooks.WebhookEventRepository
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class TransactionService(
    private val transactionRepository: TransactionRepository,
    private val webhookEventRepository: WebhookEventRepository,
    private val gatewayResolver: PaymentGatewayResolver,
    private val idempotencyService: IdempotencyService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Creates and persists a transaction, initiates payment through the resolved
     * gateway, and records the idempotency result. Repeated calls with the same
     * idempotency key replay the original response.
     */
    @Transactional
    suspend fun initiatePayment(request: PaymentRequest): PaymentResponse {
        val key = request.idempotencyKey ?: UUID.randomUUID().toString()
        val requestHash = idempotencyService.hashRequest(request)

        idempotencyService.findExisting(key)?.let {
            log.info("Replaying idempotent request: key={}", key)
            return idempotencyService.replayOrThrow(key, requestHash)
        }

        val transaction = transactionRepository.save(
            PaymentTransactionEntity(
                gateway = request.gateway,
                merchantReference = request.reference,
                amount = request.amount,
                currency = request.currency,
                paymentType = request.paymentType,
                idempotencyKey = key
            )
        )

        val gateway = gatewayResolver.resolve(request.gateway)
        val response = try {
            transaction.transitionTo(PaymentStatus.INITIATED)
            transactionRepository.save(transaction)
            gateway.initiatePayment(request.copy(idempotencyKey = key))
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            log.error("Gateway initiation failed: reference={}, gateway={}", request.reference, request.gateway, ex)
            transaction.transitionTo(PaymentStatus.FAILED)
            transaction.completedAt = Instant.now()
            transactionRepository.save(transaction)
            throw ex
        }

        transaction.gatewayTransactionId = response.gatewayTransactionId
        transaction.gatewayMetadata = response.paymentInstructions
        transaction.transitionTo(response.status)
        if (response.status.isTerminal()) {
            transaction.completedAt = Instant.now()
        }
        transactionRepository.save(transaction)

        val transactionId = requireNotNull(transaction.id) { "Transaction ID was not generated on persist" }
        val finalResponse = response.copy(transactionId = transactionId.toString())
        idempotencyService.record(key, requestHash, transactionId, finalResponse)
        return finalResponse
    }

    /**
     * Returns the current status of a transaction. Polls the gateway whenever the
     * transaction is not yet terminal and has a usable reference: the gateway id
     * when known, otherwise the initiate reference stored in gateway_metadata.
     * The status is only ever advanced to what the gateway reports — auto-expiry
     * is owned by the periodic reconciliation job.
     */
    @Transactional
    suspend fun getPaymentStatus(transactionId: UUID): PaymentStatusResponse {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { TransactionNotFoundException(transactionId) }

        val gatewayReference = transaction.gatewayLookupReference()
        if (transaction.status.isTerminal() || gatewayReference == null) {
            return PaymentStatusResponse.from(transaction)
        }

        val gateway = gatewayResolver.resolve(transaction.gateway)
        val result = gateway.getPaymentStatus(gatewayReference)

        // Learn the gateway-assigned id when the lookup went through the initiate
        // reference, so later lookups and webhook matching use the real id.
        var changed = false
        if (transaction.gatewayTransactionId == null &&
            result.status != PaymentStatus.PENDING &&
            result.gatewayTransactionId != gatewayReference
        ) {
            transaction.gatewayTransactionId = result.gatewayTransactionId
            changed = true
        }

        if (result.status != transaction.status && transaction.status.canTransitionTo(result.status)) {
            transaction.transitionTo(result.status)
            if (result.status.isTerminal()) {
                transaction.completedAt = Instant.now()
            }
            changed = true
        }
        if (changed) {
            transactionRepository.save(transaction)
        }
        return PaymentStatusResponse.from(transaction).copy(gatewayResponse = result.gatewayResponse)
    }

    /**
     * Persists a verified webhook event, rejects duplicates, and updates the
     * matching transaction. The provider adapter has already verified the
     * signature and mapped the event to a generic result.
     */
    @Transactional
    suspend fun processWebhookEvent(
        webhookRequest: GatewayWebhookRequest,
        result: WebhookProcessingResult
    ): WebhookProcessingResult {
        val existing = webhookEventRepository.findByTransactionReferenceAndEventType(
            result.transactionReference,
            result.eventType
        )
        if (existing != null) {
            log.info("Duplicate webhook ignored: reference={}, eventType={}", result.transactionReference, result.eventType)
            return result.copy(duplicate = true, processed = false)
        }

        val event = webhookEventRepository.save(
            WebhookEventEntity(
                eventType = result.eventType,
                transactionReference = result.transactionReference,
                payloadHash = Hashing.sha256Hex(webhookRequest.rawBody ?: ""),
                payload = webhookRequest.payload,
                signature = webhookRequest.signature,
                verificationStatus = "VERIFIED",
                processingStatus = "PENDING",
                receivedAt = Instant.now()
            )
        )

        val transaction = findTransactionForWebhook(webhookRequest.payload, result.transactionReference)
        if (transaction == null) {
            log.warn(
                "Verified webhook received for unknown transaction: reference={}, eventType={}",
                result.transactionReference,
                result.eventType
            )
            event.processingStatus = "FAILED"
            webhookEventRepository.save(event)
            return result.copy(processed = false, errorMessage = "No matching transaction found")
        }

        if (transaction.gatewayTransactionId == null) {
            transaction.gatewayTransactionId = result.transactionReference
        }
        if (transaction.status.canTransitionTo(result.newStatus)) {
            transaction.transitionTo(result.newStatus)
            if (result.newStatus.isTerminal()) {
                transaction.completedAt = Instant.now()
            }
        } else {
            log.warn(
                "Webhook status transition ignored: transactionId={}, current={}, target={}",
                transaction.id, transaction.status, result.newStatus
            )
        }
        transactionRepository.save(transaction)

        event.processingStatus = "PROCESSED"
        event.processedAt = Instant.now()
        webhookEventRepository.save(event)

        return result.copy(processed = true)
    }

    private suspend fun findTransactionForWebhook(
        payload: Map<String, Any>,
        transactionReference: String
    ): PaymentTransactionEntity? {
        transactionRepository.findByGatewayTransactionId(transactionReference)?.let { return it }

        val metaData = payload["metaData"] as? Map<*, *>
        val referenceNumber = metaData?.get("referenceNumber") as? String
        if (!referenceNumber.isNullOrBlank()) {
            transactionRepository.findByMetadataContaining("""{"referenceNumber":"$referenceNumber"}""")
                ?.let { return it }
        }
        val tan = metaData?.get("timedAccountNumber") as? String
        if (!tan.isNullOrBlank()) {
            transactionRepository.findByMetadataContaining("""{"timedAccountNumber":"$tan"}""")
                ?.let { return it }
        }
        return null
    }
}