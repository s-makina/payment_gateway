package com.paymentgateway.PaymentGateway.gateways.onekhusa.auth

import com.paymentgateway.PaymentGateway.gateways.onekhusa.client.OneKhusaErrorMapper
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaAccessTokenRequest
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.response.OneKhusaAccessTokenResponse
import org.springframework.http.MediaType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Component
class OneKhusaAuthClient(
    private val properties: OneKhusaProperties,
    private val webClientBuilder: WebClient.Builder,
    private val objectMapper: JsonMapper,
    private val errorMapper: OneKhusaErrorMapper
) {

    private val webClient: WebClient = webClientBuilder.baseUrl(properties.baseUrl).build()

    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun getAccessToken(): OneKhusaAccessTokenResponse {
        val request = OneKhusaAccessTokenRequest(
            apiKey = properties.apiKey,
            apiSecret = properties.apiSecret,
            organisationId = properties.organisationId,
            merchantAccountNumber = properties.merchantAccountNumber
        )
        val body = try {
            webClient.post()
                .uri("/account/getAccessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .awaitBody<String>()
        } catch (ex: WebClientResponseException) {
            log.warn(
                "OneKhusa token request failed: status={} body={}",
                ex.statusCode.value(), ex.responseBodyAsString
            )
            throw errorMapper.mapError(ex)
        }
        return objectMapper.readValue(body)
    }
}