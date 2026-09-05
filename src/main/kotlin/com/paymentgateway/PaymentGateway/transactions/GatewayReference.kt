package com.paymentgateway.PaymentGateway.transactions

/**
 * Request-to-pay initiate metadata keys persisted in gateway_metadata by the
 * gateway mapper. Shared so the status endpoint, reconciliation, and webhook
 * matching all agree on the reconciliation keys.
 */
internal const val METADATA_REFERENCE_NUMBER = "referenceNumber"
internal const val METADATA_EXPIRY_DATE = "expiryDate"

/**
 * The reference to use when asking the gateway for a transaction's status:
 * the gateway-assigned transaction id when known (e.g. learned from a webhook
 * or a successful poll), otherwise the initiate `referenceNumber` that was
 * stored in gateway_metadata at initiate time.
 */
internal fun PaymentTransactionEntity.gatewayLookupReference(): String? {
    gatewayTransactionId?.takeIf { it.isNotBlank() }?.let { return it }
    val reference = gatewayMetadata?.get(METADATA_REFERENCE_NUMBER) as? String
    return reference?.takeIf { it.isNotBlank() }
}
