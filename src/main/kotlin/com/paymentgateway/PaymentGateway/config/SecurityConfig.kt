package com.paymentgateway.PaymentGateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * The service exposes a public, provider-neutral payment API; webhook
 * authenticity is verified per-gateway via signatures, not via sessions.
 * Swagger/OpenAPI endpoints must stay publicly reachable for docs.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/api/**",
                    "/actuator/health",
                    "/h2-console/**"
                ).permitAll()
                    .anyRequest().authenticated()
            }
            .headers { headers ->
                // h2-console (dev only) renders in a frame
                headers.frameOptions { it.disable() }
            }
        return http.build()
    }
}
