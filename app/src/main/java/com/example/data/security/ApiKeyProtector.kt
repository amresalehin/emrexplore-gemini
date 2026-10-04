package com.example.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object ApiKeyProtector {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "emrexplore.ai.api-key.v1"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val PREFIX = "keystore:v1:"

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setUnlockedDeviceRequired(true)
                    }
                }
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(value: String): String {
        if (value.isBlank() || value.startsWith(PREFIX)) return value
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(
            ByteBuffer.allocate(4 + iv.size + ciphertext.size)
                .putInt(iv.size).put(iv).put(ciphertext).array(),
            Base64.NO_WRAP
        )
    }

    fun decrypt(value: String): String {
        if (value.isBlank() || !value.startsWith(PREFIX)) return value
        return try {
            val packed = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
            if (packed.size < 4) return ""
            val buffer = ByteBuffer.wrap(packed)
            val ivSize = buffer.int
            if (ivSize !in 12..16 || buffer.remaining() <= ivSize) return ""
            val iv = ByteArray(ivSize).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }
}
