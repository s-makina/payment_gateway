package com.paymentgateway.PaymentGateway.gateways.paychangu.client

import com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Maps Paychangu error responses to the provider-neutral [GatewayApiException].
 *
 * Paychangu errors are flat JSON bodies: `{ "status": "failed", "message": "..." }`
 * (HTTP 400 example from the docs). The HTTP status itself carries the semantics,
 * so it is passed through for 4xx and collapsed to BAD_GATEWAY for upstream 5xx,
 * mirroring how OneKhusa errors are handled.
 */
@Component
class PaychanguErrorMapper {

    private val log = LoggerFactory.getLogger(javaClass)

    fun mapError(ex: WebClientResponseException): GatewayApiException {
        val statusCode = ex.statusCode.value()
        val httpStatus = fallbackStatus(statusCode)
        val message = parseMessage(ex.responseBodyAsString) ?: ex.responseBodyAsString.take(MAX_DETAIL_LENGTH)

        return GatewayApiException(
            status = httpStatus,
            errorCode = fallbackErrorCode(statusCode),
            message = message
        )
    }

    private fun parseMessage(raw: String): String? = try {
        val node = MAPPER.readTree(raw)
        val message = node?.get("message")?.asText()
        message?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun fallbackStatus(statusCode: Int): HttpStatus = when (statusCode) {
        400 -> HttpStatus.BAD_REQUEST
        401 -> HttpStatus.UNAUTHORIZED
        403 -> HttpStatus.FORBIDDEN
        404 -> HttpStatus.NOT_FOUND
        408 -> HttpStatus.REQUEST_TIMEOUT
        409 -> HttpStatus.CONFLICT
        422 -> HttpStatus.UNPROCESSABLE_ENTITY
        429 -> HttpStatus.TOO_MANY_REQUESTS
        else -> if (statusCode in 500..599) HttpStatus.BAD_GATEWAY else HttpStatus.FAILED_DEPENDENCY
    }

    private fun fallbackErrorCode(statusCode: Int): String = when (statusCode) {
        400 -> "E900"
        401 -> "E901"
        403 -> "E902"
        404 -> "E903"
        408 -> "E904"
        429 -> "E905"
        else -> "E950"
    }

    private companion object {
        const val MAX_DETAIL_LENGTH = 500
        val MAPPER = tools.jackson.databind.json.JsonMapper()
    }
}
