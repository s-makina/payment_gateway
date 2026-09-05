package com.paymentgateway.PaymentGateway.gateways.paychangu.client

import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody
import kotlinx.coroutines.reactor.awaitSingleOrNull

/**
 * HTTP client for the Paychangu API. Authentication is a static secret key sent
 * as a Bearer token (no token-exchange flow). Returns the raw response body so
 * callers can keep the original gateway payload alongside the parsed DTO.
 */
@Component
class PaychanguApiClient(
    private val properties: PaychanguProperties,
    webClientBuilder: WebClient.Builder,
    private val errorMapper: PaychanguErrorMapper
) {

    private val webClient: WebClient = webClientBuilder.baseUrl(properties.baseUrl).build()

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * GETs the given path and returns the raw response body, or null when the
     * gateway answers 204 No Content.
     */
    suspend fun get(path: String): String? {
        val responseSpec = webClient.get()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, bearerToken())
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()

        return try {
            responseSpec.bodyToMono(String::class.java).awaitSingleOrNull()
        } catch (ex: WebClientResponseException) {
            throw errorMapper.mapError(ex)
        }
    }

    /**
     * POSTs the given body and returns the raw response body. Throws
     * [GatewayApiException] on non-2xx responses.
     */
    suspend fun post(path: String, body: Any): String {
        val responseSpec = webClient.post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, bearerToken())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .bodyValue(body)
            .retrieve()

        return try {
            responseSpec.awaitBody()
        } catch (ex: WebClientResponseException) {
            // Error bodies carry no credentials — safe to log.
            log.warn(
                "Paychangu request failed: path={} status={} body={}",
                path, ex.statusCode.value(), ex.responseBodyAsString
            )
            throw errorMapper.mapError(ex)
        }
    }

    private fun bearerToken(): String = "Bearer ${properties.secretKey.trim()}"
}
