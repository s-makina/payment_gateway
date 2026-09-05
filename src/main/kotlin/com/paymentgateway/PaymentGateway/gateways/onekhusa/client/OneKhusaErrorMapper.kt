package com.paymentgateway.PaymentGateway.gateways.onekhusa.client

import com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Component
class OneKhusaErrorMapper(
    private val objectMapper: JsonMapper
) {

    fun mapError(ex: WebClientResponseException): GatewayApiException {
        val errorCode = try {
            objectMapper.readValue<Map<String, Any>>(ex.responseBodyAsString)["errorCode"] as? String
        } catch (_: Exception) {
            null
        }
        return GatewayApiException(
            status = HttpStatus.FAILED_DEPENDENCY,
            errorCode = errorCode ?: "E950",
            message = ex.responseBodyAsString.take(500)
        )
    }
}