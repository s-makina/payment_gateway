package com.paymentgateway.PaymentGateway.gateways.onekhusa.collections

import com.paymentgateway.PaymentGateway.gateways.onekhusa.client.OneKhusaApiClient
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaGetTransactionRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaRequestToPayRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaRequestToPayResponse
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaTransactionResponse
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Component
class OneKhusaCollectionsClient(
    private val properties: OneKhusaProperties,
    private val apiClient: OneKhusaApiClient,
    private val objectMapper: JsonMapper
) {

    suspend fun initiateRequestToPay(
        request: OneKhusaRequestToPayRequest,
        idempotencyKey: String
    ): OneKhusaRequestToPayResponse {
        val body = apiClient.post(
            path = "/collections/requestToPay/initiate",
            body = request,
            headers = mapOf("X-Idempotency-Key" to idempotencyKey)
        ) ?: throw IllegalStateException("OneKhusa returned an empty response for requestToPay/initiate")
        return objectMapper.readValue(body)
    }

    /** Returns null when OneKhusa responds 204 (transaction not found). */
    suspend fun getTransaction(transactionReferenceNumber: String): OneKhusaTransactionResponse? {
        val request = OneKhusaGetTransactionRequest(
            merchantAccountNumber = properties.merchantAccountNumber,
            transactionReferenceNumber = transactionReferenceNumber
        )
        val body = apiClient.post(
            path = "/collections/getTransaction",
            body = request,
            allowNoContent = true
        ) ?: return null
        return objectMapper.readValue(body)
    }
}