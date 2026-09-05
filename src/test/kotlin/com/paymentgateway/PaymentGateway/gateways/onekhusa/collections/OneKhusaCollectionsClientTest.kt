package com.paymentgateway.PaymentGateway.gateways.onekhusa.collections

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException
import com.paymentgateway.PaymentGateway.gateways.onekhusa.auth.OneKhusaTokenProvider
import com.paymentgateway.PaymentGateway.gateways.onekhusa.client.OneKhusaApiClient
import com.paymentgateway.PaymentGateway.gateways.onekhusa.client.OneKhusaErrorMapper
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import com.paymentgateway.PaymentGateway.gateways.onekhusa.dto.request.OneKhusaRequestToPayRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.math.BigDecimal

class OneKhusaCollectionsClientTest {

    private lateinit var wireMock: WireMockServer

    @BeforeEach
    fun setUp() {
        wireMock = WireMockServer(WireMockConfiguration.options().dynamicPort())
        wireMock.start()
    }

    @AfterEach
    fun tearDown() {
        wireMock.stop()
    }

    @Test
    fun `initiates request to pay with bearer token and idempotency key`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/collections/requestToPay/initiate"))
                .withHeader("Authorization", matching("Bearer .*"))
                .withHeader("X-Idempotency-Key", equalTo("key-123"))
                .willReturn(
                    okJson(
                        """
                        {
                          "merchantAccountNumber": 12345678,
                          "timedAccountNumber": "11005533",
                          "expiryDate": "2099-01-01T00:00:00.000Z",
                          "expiryInMinutes": 15
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = client().initiateRequestToPay(
            OneKhusaRequestToPayRequest(
                merchantAccountNumber = 12345678,
                transactionAmount = BigDecimal("100.00"),
                transactionDescription = "Test purchase",
                referenceNumber = "INV10001",
                capturedBy = "user@example.com"
            ),
            "key-123"
        )

        assertEquals("11005533", result.response.timedAccountNumber)
        assertEquals(15, result.response.expiryInMinutes)
        // The original gateway payload is preserved for callers.
        assertEquals("11005533", result.rawResponse?.get("timedAccountNumber")?.asText())
    }

    @Test
    fun `maps a 400 upstream error to GatewayApiException`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/collections/requestToPay/initiate"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "title": "Bad Request",
                              "status": 400,
                              "errorCode": "E900",
                              "detail": "Validation failed",
                              "errors": ["Transaction Amount is required."]
                            }
                            """.trimIndent()
                        )
                )
        )

        val ex = assertFailsWith<GatewayApiException> {
            client().initiateRequestToPay(
                OneKhusaRequestToPayRequest(12345678, BigDecimal("100.00"), "d", "INV10001", "u@example.com"),
                "key-123"
            )
        }

        assertEquals("E900", ex.errorCode)
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
    }

    @Test
    fun `get transaction returns empty result on 204 not found`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/collections/getTransaction"))
                .willReturn(aResponse().withStatus(204))
        )

        val result = client().getTransaction("NONEXISTENT")
        assertNull(result.response)
        assertNull(result.rawResponse)
    }

    @Test
    fun `get transaction parses a successful response`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/collections/getTransaction"))
                .willReturn(
                    okJson(
                        """
                        {
                          "beneficiary": {
                            "accountNumber": 12346198,
                            "accountName": "MERCHANT SANDBOX",
                            "amountReceived": 49500,
                            "currencyCode": "MWK"
                          },
                          "source": {
                            "accountNumber": "5271306",
                            "customerName": "ANGEL BAULENI",
                            "amountSent": 50000,
                            "currencyCode": "MWK",
                            "connectorId": 212188,
                            "connectorName": "National Bank of Malawi"
                          },
                          "transaction": {
                            "transactionReferenceNumber": "CBPC73IQ5U2E",
                            "transactionFee": 500,
                            "transactionDate": "2026-02-09T15:12:52.802+02:00",
                            "transactionStatusCode": "S",
                            "transactionStatusName": "Success",
                            "responseCode": "S100",
                            "responseMessage": "Successful transaction"
                          }
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = client().getTransaction("CBPC73IQ5U2E")
        val response = result.response

        assertEquals("CBPC73IQ5U2E", response?.transaction?.transactionReferenceNumber)
        assertEquals("S", response?.transaction?.transactionStatusCode)
        assertEquals("MWK", response?.beneficiary?.currencyCode)
        assertEquals(49500, response?.beneficiary?.amountReceived?.toInt())
        // The original gateway payload is preserved for callers.
        assertEquals("CBPC73IQ5U2E", result.rawResponse?.get("transaction")?.get("transactionReferenceNumber")?.asText())
    }

    private fun client(): OneKhusaCollectionsClient {
        val properties = OneKhusaProperties(baseUrl = "http://localhost:${wireMock.port()}", merchantAccountNumber = 12345678)
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val apiClient = OneKhusaApiClient(
            properties = properties,
            tokenProvider = object : OneKhusaTokenProvider {
                override suspend fun getAccessToken(): String = "test-token"
            },
            webClientBuilder = WebClient.builder(),
            errorMapper = OneKhusaErrorMapper(mapper)
        )
        return OneKhusaCollectionsClient(properties, apiClient, mapper)
    }
}