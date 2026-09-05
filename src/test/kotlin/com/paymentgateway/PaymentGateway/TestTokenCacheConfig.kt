package com.paymentgateway.PaymentGateway

import com.paymentgateway.PaymentGateway.gateways.onekhusa.auth.OneKhusaTokenCache
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@TestConfiguration
class TestTokenCacheConfig {

    @Bean
    @Primary
    fun inMemoryTokenCache(): OneKhusaTokenCache = InMemoryTokenCache()

    class InMemoryTokenCache : OneKhusaTokenCache {

        private data class TokenEntry(val token: String, val expiresAtEpochMillis: Long)

        private val store = ConcurrentHashMap<String, TokenEntry>()

        override fun get(): String? {
            val entry = store[KEY] ?: return null
            if (System.currentTimeMillis() > entry.expiresAtEpochMillis) {
                store.remove(KEY)
                return null
            }
            return entry.token
        }

        override fun put(token: String, ttl: Duration) {
            store[KEY] = TokenEntry(token, System.currentTimeMillis() + ttl.toMillis())
        }

        private companion object {
            const val KEY = "test-token"
        }
    }
}