package com.paymentgateway.PaymentGateway.gateways.onekhusa.auth

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

interface OneKhusaTokenCache {

    fun get(): String?

    fun put(token: String, ttl: Duration)
}

@Component
class RedisOneKhusaTokenCache(
    private val redisTemplate: StringRedisTemplate
) : OneKhusaTokenCache {

    override fun get(): String? = redisTemplate.opsForValue().get(KEY)

    override fun put(token: String, ttl: Duration) {
        redisTemplate.opsForValue().set(KEY, token, ttl)
    }

    private companion object {
        const val KEY = "onekhusa:access-token"
    }
}