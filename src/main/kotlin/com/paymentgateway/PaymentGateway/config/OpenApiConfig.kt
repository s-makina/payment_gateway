package com.paymentgateway.PaymentGateway.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Central OpenAPI definition. Provider-neutral: gateway specifics are
 * documented on the controllers/DTOs via annotations, never here.
 */
@Configuration
class OpenApiConfig(
    @Value("\${server.port:8080}") private val serverPort: String
) {

    @Bean
    fun paymentGatewayOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Payment Gateway Service API")
                .description(
                    "Multi-payment gateway integration microservice. " +
                        "Initiate provider-neutral payments, query transaction status, " +
                        "list available gateways, and receive provider webhooks."
                )
                .version("v1")
                .contact(
                    Contact()
                        .name("Payment Gateway Team")
                        .email("payments@paymentgateway.local")
                )
                .license(License().name("Proprietary"))
        )
        .servers(
            listOf(
                Server().url("http://localhost:$serverPort").description("Local"),
                Server().url("https://api.sandbox.paymentgateway.local").description("Sandbox")
            )
        )
}
