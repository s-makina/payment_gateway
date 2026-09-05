package com.paymentgateway.PaymentGateway.gateways.onekhusa.client

import com.paymentgateway.PaymentGateway.gateways.onekhusa.auth.OneKhusaTokenProvider
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBody

@Component
class OneKhusaApiClient(
    private val properties: OneKhusaProperties,
    private val tokenProvider: OneKhusaTokenProvider,
    private val webClientBuilder: WebClient.Builder,
    private val errorMapper: OneKhusaErrorMapper
) {

    private val webClient: WebClient = webClientBuilder.baseUrl(properties.baseUrl).build()

    /**
     * POSTs to the given path with the cached bearer token and returns the raw
     * response body. Returns null for 204 No Content responses when
     * [allowNoContent] is true.
     */
    suspend fun post(
        path: String,
        body: Any,
        headers: Map<String, String> = emptyMap(),
        allowNoContent: Boolean = false
    ): String? {
        val token = tokenProvider.getAccessToken()
        val responseSpec = webClient.post()
            .uri(path)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .bodyValue(body)
            .retrieve()

        return try {
            if (allowNoContent) {
                responseSpec.bodyToMono(String::class.java).awaitSingleOrNull()
            } else {
                responseSpec.awaitBody()
            }
        } catch (ex: WebClientResponseException) {
            throw errorMapper.mapError(ex)
        }
    }
}