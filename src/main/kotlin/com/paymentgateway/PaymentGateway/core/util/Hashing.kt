package com.paymentgateway.PaymentGateway.core.util

import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Hashing {

    fun sha256Hex(input: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)))

    fun hmacSha256Hex(input: String, secret: String): String =
        HexFormat.of().formatHex(hmacSha256Digest(input, secret))

    private fun hmacSha256Digest(input: String, secret: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8))
    }

    fun hmacSha512Hex(input: String, secret: String): String =
        HexFormat.of().formatHex(hmacSha512Digest(input, secret))

    fun hmacSha512Base64(input: String, secret: String): String =
        Base64.getEncoder().encodeToString(hmacSha512Digest(input, secret))

    private fun hmacSha512Digest(input: String, secret: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        return mac.doFinal(input.toByteArray(Charsets.UTF_8))
    }
}