package com.paymentgateway.PaymentGateway.gateways.onekhusa.auth

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.springframework.stereotype.Component
import java.time.Duration

interface OneKhusaTokenProvider {
    suspend fun getAccessToken(): String
}

@Component
class OneKhusaTokenService(
    private val authClient: OneKhusaAuthClient,
    private val tokenCache: OneKhusaTokenCache
) : OneKhusaTokenProvider {

    private val mutex = Mutex()

    override suspend fun getAccessToken(): String {
        tokenCache.get()?.let { return it }
        return mutex.withLock {
            // Double-check after acquiring the lock to avoid duplicate token requests.
            tokenCache.get()?.let { return it }

            val response = authClient.getAccessToken()
            val expiryMinutes = (response.expiryInMinutes ?: 5).toLong().coerceAtLeast(1)
            // Cache with a buffer shorter than the actual token validity.
            val ttl = Duration.ofMinutes((expiryMinutes - 1).coerceAtLeast(1))
            tokenCache.put(response.accessToken, ttl)
            response.accessToken
        }
    }
}