package app.lock.photo.valut.core.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
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
        if (hashB64.isNullOrEmpty() || saltB64.isNullOrEmpty()) return false
        val salt = saltB64.decode() ?: return false
        val expected = hashB64.decode() ?: return false
        val actual = derive(secret, salt).let { it.decode() } ?: return false
        return actual.constantTimeEquals(expected)
    }

    private fun derive(secret: CharArray, salt: ByteArray): String {
        val spec = PBEKeySpec(secret, salt, ITERATIONS, KEY_LENGTH_BITS)
        val pbkdf = try {
            SecretKeyFactory.getInstance(PBKDF2_ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        // Device-bound pepper so off-device cracking is infeasible.
        return keystoreManager.pepper(pbkdf).encode()
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
        const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_LENGTH = 16
    }
}
