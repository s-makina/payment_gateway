package com.paymentgateway.PaymentGateway.gateways.onekhusa.collections

import com.paymentgateway.PaymentGateway.gateways.onekhusa.client.OneKhusaApiClient
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaGetTransactionRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaRequestToPayRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaRequestToPayResponse
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaTransactionResponse
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Component
class OneKhusaCollectionsClient(
    private val properties: OneKhusaProperties,
    private val apiClient: OneKhusaApiClient,
    private val objectMapper: JsonMapper
) {

    /**
     * Initiates a request-to-pay and returns the parsed response together with
     * the original gateway payload so it can be surfaced to callers.
     */
    suspend fun initiateRequestToPay(
        request: OneKhusaRequestToPayRequest,
        idempotencyKey: String
    ): OneKhusaInitiateResult {
        val body = apiClient.post(
            path = "/collections/requestToPay/initiate",
            body = request,
            headers = mapOf("X-Idempotency-Key" to idempotencyKey)
        ) ?: throw IllegalStateException("OneKhusa returned an empty response for requestToPay/initiate")
        return OneKhusaInitiateResult(
            rawResponse = parseRaw(body),
            response = objectMapper.readValue(body)
        )
    }

    /**
     * Looks a transaction up at the gateway. [OneKhusaGetTransactionResult.response]
     * is null when OneKhusa responds 204 (transaction not found); the original
     * payload is kept so it can be surfaced to callers.
     */
    suspend fun getTransaction(transactionReferenceNumber: String): OneKhusaGetTransactionResult {
        val request = OneKhusaGetTransactionRequest(
            merchantAccountNumber = properties.merchantAccountNumber,
            transactionReferenceNumber = transactionReferenceNumber
        )
        val body = apiClient.post(
            path = "/collections/getTransaction",
            body = request,
            allowNoContent = true
        )
        return if (body == null) {
            OneKhusaGetTransactionResult(rawResponse = null, response = null)
        } else {
            OneKhusaGetTransactionResult(
                rawResponse = parseRaw(body),
                response = objectMapper.readValue(body)
            )
        }
    }

    private fun parseRaw(body: String): JsonNode? = runCatching { objectMapper.readTree(body) }.getOrNull()
}

/** Result of a request-to-pay initiation: parsed DTO plus the original payload. */
data class OneKhusaInitiateResult(
    val rawResponse: JsonNode?,
    val response: OneKhusaRequestToPayResponse
)

/** Result of a getTransaction lookup: parsed DTO (null on 204) plus the original payload. */
data class OneKhusaGetTransactionResult(
    val rawResponse: JsonNode?,
    val response: OneKhusaTransactionResponse?
)