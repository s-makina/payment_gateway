package com.paymentgateway.PaymentGateway.gateways.paychangu

import com.paymentgateway.PaymentGateway.gateways.paychangu.client.PaychanguApiClient
import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguCheckoutRequest
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguDirectChargeRequest
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguCheckoutData
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguDirectChargeData
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguEnvelope
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.response.PaychanguTransactionData
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/**
 * High-level Paychangu operations. Each method returns the parsed payload
 * together with the original raw response body so it can be surfaced to
 * callers — the same pattern as [com.paymentgateway.PaymentGateway.gateways.onekhusa.collections.OneKhusaCollectionsClient].
 */
@Component
class PaychanguOperationsClient(
    private val properties: PaychanguProperties,
    private val apiClient: PaychanguApiClient,
    private val objectMapper: JsonMapper
) {

    /**
     * Creates a hosted-checkout session (`POST /payment`). The caller hands the
     * returned `checkout_url` to the customer to complete the payment.
     */
    suspend fun initiateCheckout(request: PaychanguCheckoutRequest): PaychanguInitiateResult {
        val body = apiClient.post(path = "/payment", body = request)
        val envelope = objectMapper.readValue<PaychanguEnvelope<PaychanguCheckoutData>>(body)
        val data = requireNotNull(envelope.data?.checkoutUrl) {
            "Paychangu checkout response did not include a checkout_url: ${envelope.message}"
        }
        return PaychanguInitiateResult(
            rawResponse = parseRaw(body),
            checkout = data,
            envelopeMessage = envelope.message
        )
    }

    /**
     * Initiates a direct mobile-money charge
     * (`POST /mobile-money/payments/initialize`).
     */
    suspend fun initiateDirectCharge(request: PaychanguDirectChargeRequest): PaychanguDirectChargeResult {
        val body = apiClient.post(path = "/mobile-money/payments/initialize", body = request)
        val envelope = objectMapper.readValue<PaychanguEnvelope<PaychanguDirectChargeData>>(body)
        val data = requireNotNull(envelope.data?.chargeId) {
            "Paychangu direct charge response did not include a charge_id: ${envelope.message}"
        }
        return PaychanguDirectChargeResult(
            rawResponse = parseRaw(body),
            charge = data,
            envelopeMessage = envelope.message
        )
    }

    /**
     * Verifies a transaction by its `tx_ref` (`GET /verify-payment/{tx_ref}`).
     * Covers hosted-checkout payments; also returns 404-normalized results when
     * the reference is unknown at the gateway.
     */
    suspend fun verifyPayment(txRef: String): PaychanguVerifyResult = verify(
        path = "/verify-payment/$txRef",
        lookupReference = txRef
    )

    /**
     * Verifies a direct charge by its `charge_id`
     * (`GET /mobile-money/payments/{chargeId}/verify`).
     */
    suspend fun verifyDirectCharge(chargeId: String): PaychanguVerifyResult = verify(
        path = "/mobile-money/payments/$chargeId/verify",
        lookupReference = chargeId
    )

    private suspend fun verify(path: String, lookupReference: String): PaychanguVerifyResult {
        val body = try {
            apiClient.get(path)
        } catch (ex: com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException) {
            // An unknown reference is a legitimate polling state (e.g. tx_ref not
            // yet visible), so it is normalized to PENDING like OneKhusa's 204.
            if (ex.status == org.springframework.http.HttpStatus.NOT_FOUND) {
                return PaychanguVerifyResult(rawResponse = null, transaction = null)
            }
            throw ex
        } ?: return PaychanguVerifyResult(rawResponse = null, transaction = null)

        val envelope = objectMapper.readValue<PaychanguEnvelope<PaychanguTransactionData>>(body)
        return PaychanguVerifyResult(
            rawResponse = parseRaw(body),
            transaction = envelope.data,
            lookupReference = lookupReference
        )
    }

    private fun parseRaw(body: String): JsonNode? = runCatching { objectMapper.readTree(body) }.getOrNull()
}

/** Result of a hosted-checkout initiation: parsed DTO plus the original payload. */
data class PaychanguInitiateResult(
    val rawResponse: JsonNode?,
    val checkout: PaychanguCheckoutData,
    val envelopeMessage: String?
)

/** Result of a direct-charge initiation: parsed DTO plus the original payload. */
data class PaychanguDirectChargeResult(
    val rawResponse: JsonNode?,
    val charge: PaychanguDirectChargeData,
    val envelopeMessage: String?
)

/** Result of a verify lookup: parsed DTO (null when unknown at the gateway) plus the original payload. */
data class PaychanguVerifyResult(
    val rawResponse: JsonNode?,
    val transaction: PaychanguTransactionData?,
    val lookupReference: String? = null
)
