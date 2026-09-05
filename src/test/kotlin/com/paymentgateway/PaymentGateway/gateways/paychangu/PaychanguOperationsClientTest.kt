package com.paymentgateway.PaymentGateway.gateways.paychangu

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.paymentgateway.PaymentGateway.core.exceptions.GatewayApiException
import com.paymentgateway.PaymentGateway.gateways.paychangu.client.PaychanguApiClient
import com.paymentgateway.PaymentGateway.gateways.paychangu.client.PaychanguErrorMapper
import com.paymentgateway.PaymentGateway.gateways.paychangu.config.PaychanguProperties
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguCheckoutRequest
import com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguCustomization
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule

class PaychanguOperationsClientTest {

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
    fun `initiates checkout with bearer secret key`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/payment"))
                .withHeader("Authorization", equalTo("Bearer sec-test-key"))
                .withHeader("Content-Type", matching("application/json.*"))
                .willReturn(
                    okJson(
                        """
                        {
                          "message": "Hosted payment session generated successfully.",
                          "status": "success",
                          "data": {
                            "event": "checkout.session:created",
                            "checkout_url": "https://test-checkout.paychangu.com/7887951180",
                            "data": {
                              "tx_ref": "ae041eae-6abd-4602-a949-56fbd65c29fe",
                              "currency": "MWK",
                              "amount": 1000,
                              "mode": "sandbox",
                              "status": "pending"
                            }
                          }
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = client().initiateCheckout(
            PaychanguCheckoutRequest(
                amount = "1000",
                currency = "MWK",
                txRef = "INV-10001",
                callbackUrl = "https://shop.example.com/callback",
                returnUrl = "https://shop.example.com/return",
                customization = PaychanguCustomization(title = "Test Payment", description = "Test purchase")
            )
        )

        assertEquals("https://test-checkout.paychangu.com/7887951180", result.checkout.checkoutUrl)
        assertEquals("ae041eae-6abd-4602-a949-56fbd65c29fe", result.checkout.inner?.txRef)
        // The original gateway payload is preserved for callers.
        assertEquals("7887951180", result.rawResponse?.get("data")?.get("checkout_url")?.asText()?.takeLast(10))
        assertEquals("Hosted payment session generated successfully.", result.envelopeMessage)
    }

    @Test
    fun `initiates a direct charge`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/mobile-money/payments/initialize"))
                .withHeader("Authorization", equalTo("Bearer sec-test-key"))
                .willReturn(
                    okJson(
                        """
                        {
                          "status": "success",
                          "message": "Payment initiated successfully.",
                          "data": {
                            "amount": 1000,
                            "charge_id": "27",
                            "ref_id": "95652259752",
                            "status": "pending",
                            "mobile": "+265997xxxx50",
                            "currency": "MWK",
                            "mobile_money": {
                              "name": "Airtel Money",
                              "ref_id": "20be6c20-adeb-4b5b-a7ba-0769820df4fb",
                              "country": "Malawi"
                            }
                          }
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = client().initiateDirectCharge(
            com.paymentgateway.PaymentGateway.gateways.paychangu.dto.request.PaychanguDirectChargeRequest(
                mobile = "265990000000",
                mobileMoneyOperatorRefId = "20be6c20-adeb-4b5b-a7ba-0769820df4fb",
                amount = "1000",
                chargeId = "PDC-INV10001"
            )
        )

        assertEquals("27", result.charge.chargeId)
        assertEquals("95652259752", result.charge.refId)
        assertEquals("Airtel Money", result.charge.mobileMoney?.name)
        assertEquals("27", result.rawResponse?.get("data")?.get("charge_id")?.asText())
    }

    @Test
    fun `verifies a payment by tx_ref`() = runBlocking {
        wireMock.stubFor(
            get(urlEqualTo("/verify-payment/PA54231315"))
                .withHeader("Authorization", equalTo("Bearer sec-test-key"))
                .willReturn(
                    okJson(
                        """
                        {
                          "status": "success",
                          "message": "Payment details retrieved successfully.",
                          "data": {
                            "event_type": "checkout.payment",
                            "tx_ref": "PA54231315",
                            "mode": "live",
                            "type": "API Payment (Checkout)",
                            "status": "success",
                            "reference": "26262633201",
                            "currency": "MWK",
                            "amount": 1000,
                            "charges": 40,
                            "authorization": {
                              "channel": "Card",
                              "brand": "MASTERCARD",
                              "completed_at": "2024-08-08T23:21:22.000000Z"
                            }
                          }
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = client().verifyPayment("PA54231315")
        assertEquals("success", result.transaction?.status)
        assertEquals("26262633201", result.transaction?.reference)
        assertEquals("Card", result.transaction?.authorization?.channel)
        assertEquals("PA54231315", result.lookupReference)
    }

    @Test
    fun `verifies a direct charge by charge id`() = runBlocking {
        wireMock.stubFor(
            get(urlEqualTo("/mobile-money/payments/2345/verify"))
                .willReturn(
                    okJson(
                        """
                        {
                          "status": "successful",
                          "message": "Payment authorized and completed successfully.",
                          "data": {
                            "charge_id": "2345",
                            "status": "success",
                            "amount": 100,
                            "currency": "MWK",
                            "event_type": "api.charge.payment"
                          }
                        }
                        """.trimIndent()
                    )
                )
        )

        val result = client().verifyDirectCharge("2345")
        assertEquals("2345", result.transaction?.chargeId)
        assertEquals("success", result.transaction?.status)
    }

    @Test
    fun `normalizes a 404 on verify to an empty result`() = runBlocking {
        wireMock.stubFor(
            get(urlEqualTo("/verify-payment/UNKNOWN"))
                .willReturn(aResponse().withStatus(404).withBody("""{"status":"failed","message":"Not found"}"""))
        )

        val result = client().verifyPayment("UNKNOWN")
        assertNull(result.transaction)
        assertNull(result.rawResponse)
    }

    @Test
    fun `maps a 400 upstream error to GatewayApiException`() = runBlocking {
        wireMock.stubFor(
            post(urlEqualTo("/payment"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"status":"failed","message":"currency is required","data":null}""")
                )
        )

        val ex = assertFailsWith<GatewayApiException> {
            client().initiateCheckout(
                PaychanguCheckoutRequest(
                    amount = "1000",
                    currency = "MWK",
                    txRef = "INV-10001",
                    callbackUrl = "https://cb",
                    returnUrl = "https://ret"
                )
            )
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.status)
        assertEquals("E900", ex.errorCode)
        assertEquals("currency is required", ex.message)
    }

    private fun client(): PaychanguOperationsClient {
        val properties = PaychanguProperties(
            baseUrl = "http://localhost:${wireMock.port()}",
            secretKey = "sec-test-key"
        )
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val apiClient = PaychanguApiClient(
            properties = properties,
            webClientBuilder = WebClient.builder(),
            errorMapper = PaychanguErrorMapper()
        )
        return PaychanguOperationsClient(properties, apiClient, mapper)
    }
}
