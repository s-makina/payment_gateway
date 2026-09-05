package com.paymentgateway.PaymentGateway.transactions

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Runs gateway status reconciliation on a fixed delay (default 30s). Fixed delay
 * waits for the previous run to finish, so runs never overlap on one instance.
 *
 * Reconciliation is driven by database rows, so deploying multiple instances
 * would need a distributed lock or FOR UPDATE SKIP LOCKED before enabling the
 * job on more than one replica.
 */
@Component
@ConditionalOnProperty(prefix = "payment.reconciliation", name = ["enabled"], havingValue = "true")
class PaymentReconciliationScheduler(
    private val reconciliationService: PaymentReconciliationService
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        initialDelayString = "\${payment.reconciliation.interval-ms:30000}",
        fixedDelayString = "\${payment.reconciliation.interval-ms:30000}"
    )
    fun reconcilePendingTransactions() {
        // Spring scheduling is blocking; bridge into the coroutine-based service.
        runBlocking {
            try {
                reconciliationService.reconcilePendingTransactions()
            } catch (ex: Exception) {
                log.error("Reconciliation run failed", ex)
            }
        }
    }
}
