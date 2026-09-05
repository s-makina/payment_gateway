package com.paymentgateway.PaymentGateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

/**
 * Shared HTTP client infrastructure. Spring Boot 4 no longer auto-configures a
 * [WebClient.Builder] bean, so it is defined explicitly here. Gateway adapters
 * inject the builder and set their own base URL — no provider-specific detail
 * lives in this module.
 */
@Configuration
class WebClientConfig {

    @Bean
    fun webClientBuilder(): WebClient.Builder = WebClient.builder()
}
