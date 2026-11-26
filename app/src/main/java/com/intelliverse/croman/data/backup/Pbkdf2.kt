package com.intelliverse.croman.data.backup

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * PBKDF2-HMAC-SHA256 using HmacSHA256, which Android 6.0 already provides.
 * Android's SecretKeyFactory only adds PBKDF2WithHmacSHA256 at API 26.
 */
internal object Pbkdf2 {
    fun hmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        require(iterations > 0 && keyLength > 0)
        val mac = Mac.getInstance("HmacSHA256")
        val hashLength = mac.macLength
        val blocks = (keyLength + hashLength - 1) / hashLength
        val output = ByteArray(blocks * hashLength)
        val block = ByteArray(hashLength)
        val mixed = ByteArray(hashLength)
        for (index in 1..blocks) {
            mac.init(SecretKeySpec(password, "HmacSHA256"))
            mac.update(salt)
            mac.update(
                byteArrayOf(
                    (index ushr 24).toByte(),
                    (index ushr 16).toByte(),
                    (index ushr 8).toByte(),
                    index.toByte(),
                ),
            )
            mac.doFinal(block, 0)
            block.copyInto(mixed)
            for (round in 2..iterations) {
                mac.init(SecretKeySpec(password, "HmacSHA256"))
                mac.update(block)
                mac.doFinal(block, 0)
                for (i in mixed.indices) mixed[i] = (mixed[i].toInt() xor block[i].toInt()).toByte()
            }
            mixed.copyInto(output, (index - 1) * hashLength)
        }
        return output.copyOf(keyLength)
    }
}
