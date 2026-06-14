package app.lock.photo.valut.core.security

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import kotlinx.coroutines.flow.first
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local recovery key. Generated once after PIN setup, shown to the user a single
 * time, then stored only as a salted, peppered hash. There is no email/cloud/server
 * involvement. If the key is lost it cannot be recovered.
 */
@Singleton
class RecoveryKeyManager @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val hasher: CredentialHasher
) {

    suspend fun isRecoveryKeyCreated(): Boolean = dataStore.recoveryKeyCreated.first()

    /**
     * Generates a fresh recovery key, stores its hash, and returns the plaintext
     * to display to the user exactly once.
     */
    suspend fun generateAndStore(): String {
        val key = generateKey()
        val hashed = hasher.hash(normalize(key).toCharArray())
        dataStore.saveRecoveryKey(hash = hashed.hash, salt = hashed.salt)
        return key
    }

    /** Verifies a user-entered recovery key (case/format insensitive). */
    suspend fun verifyRecoveryKey(input: String): Boolean {
        val hash = dataStore.recoveryKeyHash.first()
        val salt = dataStore.recoveryKeySalt.first()
        return hasher.verify(normalize(input).toCharArray(), hash, salt)
    }

    /** Strips separators/whitespace and upper-cases so display formatting doesn't matter. */
    private fun normalize(key: String): String =
        key.filter { !it.isWhitespace() && it != '-' }.uppercase()

    private fun generateKey(): String {
        val random = SecureRandom()
        val raw = buildString {
            repeat(GROUPS * GROUP_SIZE) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
        // Format as XXXX-XXXX-XXXX-XXXX for readability.
        return raw.chunked(GROUP_SIZE).joinToString("-")
    }

    private companion object {
        // Crockford-style alphabet without ambiguous characters (no O/0/I/1/L).
        const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
        const val GROUPS = 4
        const val GROUP_SIZE = 4
    }
}
