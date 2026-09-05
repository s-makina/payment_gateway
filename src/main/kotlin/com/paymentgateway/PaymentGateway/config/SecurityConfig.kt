package com.paymentgateway.PaymentGateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain

/**
 * All endpoints are currently open to the public.
 * Organisation/merchant authentication will be added later.
 * Webhook authenticity is still verified per-gateway via signatures.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .authorizeHttpRequests {
                it.anyRequest().permitAll()
            }
            .headers { headers ->
                // h2-console (dev only) renders in a frame
                headers.frameOptions { it.disable() }
            }
        return http.build()
    }
}
