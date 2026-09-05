package com.paymentgateway.PaymentGateway.gateways.onekhusa.auth

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.paymentgateway.PaymentGateway.gateways.onekhusa.client.OneKhusaErrorMapper
import com.paymentgateway.PaymentGateway.gateways.onekhusa.config.OneKhusaProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import tools.jackson.module.kotlin.jsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration

class OneKhusaTokenServiceTest {

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
    fun `fetches and caches a token, reusing it for subsequent calls`() = runBlocking {
        stubTokenResponse()

        val service = service(FakeTokenCache())

        assertEquals("token-1", service.getAccessToken())
        assertEquals("token-1", service.getAccessToken())
        assertEquals(1, wireMock.findAll(postRequestedFor(urlEqualTo("/account/getAccessToken"))).size)
    }

    @Test
    fun `uses the cached token without calling the auth endpoint`() = runBlocking {
        val cache = FakeTokenCache().apply { put("cached-token", Duration.ofMinutes(4)) }

        val service = service(cache)

        assertEquals("cached-token", service.getAccessToken())
        assertEquals(0, wireMock.findAll(postRequestedFor(urlEqualTo("/account/getAccessToken"))).size)
    }

    private fun service(cache: OneKhusaTokenCache): OneKhusaTokenService {
        val properties = OneKhusaProperties(
            baseUrl = "http://localhost:${wireMock.port()}",
            apiKey = "api-key",
            apiSecret = "api-secret",
            organisationId = "org-id",
            merchantAccountNumber = 12345678
        )
        val mapper = jsonMapper { addModule(kotlinModule()) }
        val authClient = OneKhusaAuthClient(properties, WebClient.builder(), mapper, OneKhusaErrorMapper(mapper))
        return OneKhusaTokenService(authClient, cache)
    }

    private fun stubTokenResponse() {
        wireMock.stubFor(
            post(urlEqualTo("/account/getAccessToken"))
                .willReturn(
                    okJson(
                        """
                        {
                          "accessToken": "token-1",
                          "expiresOn": "2099-01-01T00:00:00.000Z",
                          "expiryInMinutes": 5
                        }
                        """.trimIndent()
                    )
                )
        )
    }

    private class FakeTokenCache : OneKhusaTokenCache {
        private var token: String? = null
        private var ttl: Duration = Duration.ZERO

        override fun get(): String? = token

        override fun put(token: String, ttl: Duration) {
            this.token = token
            this.ttl = ttl
        }
    }
}