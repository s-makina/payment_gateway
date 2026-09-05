package com.paymentgateway.PaymentGateway.gateways.onekhusa.client

import com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/**
 * Maps OneKhusa RFC 7807 error responses to a provider-neutral
 * [GatewayApiException]. OneKhusa error codes are translated to the closest
 * HTTP status per the API contract (see docs/api-contract-onekhusa.md).
 */
@Component
class OneKhusaErrorMapper(
    private val objectMapper: JsonMapper
) {

    fun mapError(ex: WebClientResponseException): GatewayApiException {
        val statusCode = ex.statusCode.value()
        val httpStatus = when (statusCode) {
            200 -> HttpStatus.OK
            201 -> HttpStatus.CREATED
            202 -> HttpStatus.ACCEPTED
            204 -> HttpStatus.NO_CONTENT
            400 -> HttpStatus.BAD_REQUEST
            401 -> HttpStatus.UNAUTHORIZED
            403 -> HttpStatus.FORBIDDEN
            404 -> HttpStatus.NOT_FOUND
            408 -> HttpStatus.REQUEST_TIMEOUT
            409 -> HttpStatus.CONFLICT
            429 -> HttpStatus.TOO_MANY_REQUESTS
            500 -> HttpStatus.INTERNAL_SERVER_ERROR
            502 -> HttpStatus.BAD_GATEWAY
            503 -> HttpStatus.SERVICE_UNAVAILABLE
            504 -> HttpStatus.GATEWAY_TIMEOUT
            else -> HttpStatus.resolve(statusCode) ?: DEFAULT_STATUS
        }
        val body = parseBody(ex.responseBodyAsString)
        val errorCode = body["errorCode"] as? String
        val status = errorCode?.let { STATUS_BY_ERROR_CODE[it] } ?: fallbackStatus(httpStatus)
        val detail = body["detail"] as? String ?: ex.responseBodyAsString.take(MAX_DETAIL_LENGTH)
        val errors = (body["errors"] as? List<*>)?.map { it.toString() }

        return GatewayApiException(
            status = status,
            errorCode = errorCode ?: fallbackErrorCode(httpStatus),
            message = detail,
            errors = errors
        )
    }

    private fun parseBody(raw: String): Map<String, Any> = try {
        objectMapper.readValue<Map<String, Any>>(raw)
    } catch (_: Exception) {
        emptyMap()
    }

    private fun fallbackStatus(upstream: HttpStatus): HttpStatus = when (upstream) {
        HttpStatus.BAD_REQUEST -> HttpStatus.BAD_REQUEST
        HttpStatus.UNAUTHORIZED -> HttpStatus.UNAUTHORIZED
        HttpStatus.FORBIDDEN -> HttpStatus.FORBIDDEN
        HttpStatus.NOT_FOUND -> HttpStatus.NOT_FOUND
        HttpStatus.TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS
        HttpStatus.CONFLICT -> HttpStatus.CONFLICT
        else -> if (upstream.is5xxServerError) HttpStatus.BAD_GATEWAY else DEFAULT_STATUS
    }

    private fun fallbackErrorCode(upstream: HttpStatus): String = when (upstream) {
        HttpStatus.BAD_REQUEST -> "E900"
        HttpStatus.UNAUTHORIZED -> "E901"
        HttpStatus.FORBIDDEN -> "E902"
        HttpStatus.NOT_FOUND -> "E903"
        HttpStatus.TOO_MANY_REQUESTS -> "E905"
        HttpStatus.CONFLICT -> "E907"
        else -> "E950"
    }

    private companion object {
        const val MAX_DETAIL_LENGTH = 500

        val STATUS_BY_ERROR_CODE: Map<String, HttpStatus> = mapOf(
            "E900" to HttpStatus.BAD_REQUEST,
            "E901" to HttpStatus.UNAUTHORIZED,
            "E902" to HttpStatus.FORBIDDEN,
            "E903" to HttpStatus.NOT_FOUND,
            "E904" to HttpStatus.REQUEST_TIMEOUT,
            "E905" to HttpStatus.TOO_MANY_REQUESTS,
            "E906" to HttpStatus.FAILED_DEPENDENCY,
            "E907" to HttpStatus.CONFLICT,
            "E950" to HttpStatus.BAD_GATEWAY,
            "E951" to HttpStatus.SERVICE_UNAVAILABLE,
            "E952" to HttpStatus.SERVICE_UNAVAILABLE,
            "E953" to HttpStatus.GATEWAY_TIMEOUT
        )

        val DEFAULT_STATUS: HttpStatus = HttpStatus.FAILED_DEPENDENCY
    }
}