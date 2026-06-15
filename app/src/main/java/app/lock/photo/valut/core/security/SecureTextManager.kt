package app.lock.photo.valut.core.security

import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts short text (private notes) with the Keystore-backed vault key
 * (AES-256-GCM). Output is Base64 of `[12-byte IV][ciphertext+tag]`, safe to store in
 * Room. A fresh IV is generated per call, so editing a note re-randomises the IV.
 * Never logs plaintext or ciphertext.
 */
@Singleton
class SecureTextManager @Inject constructor(
    private val keyManager: VaultKeyManager
) {

    /** Encrypts [plainText]; returns Base64 token, or null if the key is unavailable. */
    fun encryptText(plainText: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyManager.getOrCreateVaultKey())
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + cipherBytes, Base64.NO_WRAP)
    }.getOrNull()

    /** Decrypts a token produced by [encryptText]; returns null on any failure. */
    fun decryptText(cipherText: String): String? = runCatching {
        val data = Base64.decode(cipherText, Base64.NO_WRAP)
        if (data.size <= IV_LENGTH) return null
        val iv = data.copyOfRange(0, IV_LENGTH)
        val body = data.copyOfRange(IV_LENGTH, data.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyManager.getOrCreateVaultKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}
