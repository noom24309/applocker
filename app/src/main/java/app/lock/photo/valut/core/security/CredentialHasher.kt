package app.lock.photo.valut.core.security

import android.util.Base64
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** A freshly produced salt + hash pair, both Base64-encoded. */
data class HashedCredential(val hash: String, val salt: String)

/**
 * Salts + stretches a secret with PBKDF2, then applies a Keystore-backed device
 * pepper. Reusable for PIN, pattern and recovery key. No raw secret is retained.
 */
@Singleton
class CredentialHasher @Inject constructor(
    private val keystoreManager: KeystoreManager
) {

    /** Hashes [secret] with a new random salt. */
    fun hash(secret: CharArray): HashedCredential {
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        val hash = derive(secret, salt)
        return HashedCredential(hash = hash, salt = salt.encode())
    }

    /** Constant-time verification of [secret] against a stored [hashB64]/[saltB64]. */
    fun verify(secret: CharArray, hashB64: String?, saltB64: String?): Boolean {
        // PBKDF2 rejects an empty password outright — and it can never match anyway.
        if (secret.isEmpty()) return false
        if (hashB64.isNullOrEmpty() || saltB64.isNullOrEmpty()) return false
        val salt = saltB64.decode() ?: return false
        val expected = hashB64.decode() ?: return false
        val actual = derive(secret, salt).let { it.decode() } ?: return false
        return actual.constantTimeEquals(expected)
    }

    private fun derive(secret: CharArray, salt: ByteArray): String {
        val pbkdf = pbkdf2(secret, salt, ITERATIONS, KEY_LENGTH_BITS)
        // Device-bound pepper so off-device cracking is infeasible.
        return keystoreManager.pepper(pbkdf).encode()
    }

    /**
     * RFC 8018 PBKDF2 built directly on the HmacSHA256 [Mac], which exists on every
     * supported API level. The JCE "PBKDF2WithHmacSHA256" SecretKeyFactory only ships
     * on API 26+, so it threw NoSuchAlgorithmException on Android 7 (API 24/25). For the
     * app's ASCII-only secrets (PIN/pattern/recovery key) this produces byte-identical
     * output to the old SecretKeyFactory path — Android's implementation encodes the
     * password as UTF-8, and for ASCII that equals the char bytes — so hashes created on
     * API 26+ still verify after this change.
     */
    private fun pbkdf2(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyLengthBits: Int
    ): ByteArray {
        val passwordBytes = utf8Bytes(password)
        return try {
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            mac.init(SecretKeySpec(passwordBytes, HMAC_ALGORITHM))
            val hLen = mac.macLength
            val dkLen = keyLengthBits / 8
            val derived = ByteArray(dkLen)
            val blockIndex = ByteArray(4)
            var offset = 0
            var block = 1
            while (offset < dkLen) {
                // INT_32_BE(block)
                blockIndex[0] = (block ushr 24).toByte()
                blockIndex[1] = (block ushr 16).toByte()
                blockIndex[2] = (block ushr 8).toByte()
                blockIndex[3] = block.toByte()
                mac.update(salt)
                var u = mac.doFinal(blockIndex)   // U1 = PRF(P, S || INT_32_BE(block))
                val t = u.copyOf()
                for (i in 2..iterations) {
                    u = mac.doFinal(u)            // Ui = PRF(P, Ui-1)
                    for (k in t.indices) t[k] = (t[k].toInt() xor u[k].toInt()).toByte()
                }
                val copyLen = minOf(hLen, dkLen - offset)
                System.arraycopy(t, 0, derived, offset, copyLen)
                offset += copyLen
                block++
            }
            derived
        } finally {
            passwordBytes.fill(0)
        }
    }

    /** UTF-8 encodes [chars] and zeroes the intermediate buffer afterwards. */
    private fun utf8Bytes(chars: CharArray): ByteArray {
        val buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        if (buffer.hasArray()) buffer.array().fill(0)
        return bytes
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray? = runCatching {
        Base64.decode(this, Base64.NO_WRAP)
    }.getOrNull()

    private fun ByteArray.constantTimeEquals(other: ByteArray): Boolean {
        if (size != other.size) return false
        var result = 0
        for (i in indices) result = result or (this[i].toInt() xor other[i].toInt())
        return result == 0
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH = 16
    }
}
