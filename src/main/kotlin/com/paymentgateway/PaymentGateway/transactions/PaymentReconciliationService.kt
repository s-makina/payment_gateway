package com.paymentgateway.PaymentGateway.transactions

import com.paymentgateway.PaymentGateway.core.domain.PaymentStatus
import com.paymentgateway.PaymentGateway.core.gateway.PaymentGatewayResolver
import com.paymentgateway.PaymentGateway.core.gateway.PaymentStatusResult
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * Periodically reconciles non-terminal transactions against their gateway.
 *
 * Each candidate is looked up at the gateway (by its gateway transaction id, or
 * failing that by the initiate reference stored in gateway_metadata) and the local
 * status is advanced to whatever the gateway reports. As a safety net for
 * request-to-pay, a transaction still awaiting the customer whose TAN expiry has
 * passed is marked EXPIRED when the gateway reports no paid/failed/reversed
 * transaction. This catches completions whose webhook was missed.
 *
 * Deliberately provider-neutral: all gateway interaction goes through
 * [com.paymentgateway.PaymentGateway.core.gateway.PaymentGateway.getPaymentStatus].
 *
 * No class-level @Transactional: gateway HTTP calls must not run inside a single
 * long-lived transaction spanning the whole batch. Each repository write is its
 * own short transaction. Overlap between runs is prevented by fixed-delay
 * scheduling on a single instance.
 */
@Service
class PaymentReconciliationService(
    private val transactionRepository: TransactionRepository,
    private val gatewayResolver: PaymentGatewayResolver
) {

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun reconcilePendingTransactions(): ReconciliationSummary {
        val candidates = transactionRepository.findByStatusIn(POLLABLE_STATUSES)
        if (candidates.isEmpty()) {
            return ReconciliationSummary()
        }
        log.debug("Reconciling {} non-terminal transaction(s)", candidates.size)
        return reconcile(candidates)
    }

    suspend fun reconcile(candidates: List<PaymentTransactionEntity>): ReconciliationSummary {
        var checked = 0
        var updated = 0
        var expired = 0
        var failed = 0
        var skipped = 0

        for (transaction in candidates) {
            val reference = transaction.gatewayLookupReference()
            if (reference == null) {
                skipped++
                continue
            }
            val gateway = try {
                gatewayResolver.resolve(transaction.gateway)
            } catch (ex: Exception) {
                failed++
                log.warn(
                    "Reconciliation skipped: no gateway for transactionId={}, gateway={}",
                    transaction.id, transaction.gateway, ex
                )
                continue
            }

            try {
                val result = gateway.getPaymentStatus(reference)
                checked++

                // Enrich with the gateway's own transaction reference when the
                // lookup went through the initiate reference instead.
                if (transaction.gatewayTransactionId == null &&
                    result.status != PaymentStatus.PENDING &&
                    result.gatewayTransactionId != reference
                ) {
                    transaction.gatewayTransactionId = result.gatewayTransactionId
                }

                val target = decideUpdate(transaction, result, Instant.now())
                when (target) {
                    null -> Unit
                    PaymentStatus.EXPIRED -> {
                        transaction.transitionTo(target)
                        transaction.completedAt = Instant.now()
                        transactionRepository.save(transaction)
                        expired++
                        logReconciliation(transaction, target)
                    }
                    else -> {
                        transaction.transitionTo(target)
                        if (target.isTerminal()) {
                            transaction.completedAt = Instant.now()
                        }
                        transactionRepository.save(transaction)
                        updated++
                        logReconciliation(transaction, target)
                    }
                }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                failed++
                log.warn(
                    "Reconciliation lookup failed: transactionId={}, gateway={}, reference={}",
                    transaction.id, transaction.gateway, reference, ex
                )
            }
        }

        if (checked + updated + expired + failed + skipped > 0) {
            log.info(
                "Reconciliation complete: checked={}, updated={}, expired={}, failed={}, skipped={}",
                checked, updated, expired, failed, skipped
            )
        }
        return ReconciliationSummary(
            checked = checked,
            updated = updated,
            expired = expired,
            failed = failed,
            skipped = skipped
        )
    }

    private fun logReconciliation(transaction: PaymentTransactionEntity, target: PaymentStatus) {
        log.info(
            "Reconciliation updated transaction: transactionId={}, merchantReference={}, status={}",
            transaction.id, transaction.merchantReference, target
        )
    }

    companion object {

        val POLLABLE_STATUSES = listOf(
            PaymentStatus.INITIATED,
            PaymentStatus.PENDING,
            PaymentStatus.AWAITING_CUSTOMER_PAYMENT
        )

        /**
         * Decides whether the transaction should change status given what the
         * gateway reported. Returns null when it should stay as-is. Stateless so
         * it can be unit tested without a database or gateway.
         */
        internal fun decideUpdate(
            transaction: PaymentTransactionEntity,
            gatewayResult: PaymentStatusResult,
            now: Instant
        ): PaymentStatus? {
            val gatewayStatus = gatewayResult.status
            if (gatewayStatus != transaction.status && transaction.status.canTransitionTo(gatewayStatus)) {
                return gatewayStatus
            }
            return if (shouldAutoExpire(transaction, gatewayResult, now)) {
                PaymentStatus.EXPIRED
            } else {
                null
            }
        }

        /**
         * A request-to-pay still awaiting the customer is marked EXPIRED once its
         * TAN has lapsed and the gateway reports no paid/failed/reversed
         * transaction (the gateway's unknown/not-found state is normalized to
         * PENDING).
         */
        internal fun shouldAutoExpire(
            transaction: PaymentTransactionEntity,
            gatewayResult: PaymentStatusResult,
            now: Instant
        ): Boolean {
            if (transaction.status != PaymentStatus.AWAITING_CUSTOMER_PAYMENT) return false
            if (gatewayResult.status != PaymentStatus.PENDING) return false
            return expiryPassed(transaction, now)
        }

        internal fun expiryPassed(transaction: PaymentTransactionEntity, now: Instant): Boolean {
            val raw = transaction.gatewayMetadata?.get(METADATA_EXPIRY_DATE) as? String ?: return false
            val expiry = try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: DateTimeParseException) {
                try {
                    Instant.parse(raw)
                } catch (_: DateTimeParseException) {
                    return false
                }
            }
            return now.isAfter(expiry)
        }
    }
}

data class ReconciliationSummary(
    val checked: Int = 0,
    val updated: Int = 0,
    val expired: Int = 0,
    val failed: Int = 0,
    val skipped: Int = 0
)
