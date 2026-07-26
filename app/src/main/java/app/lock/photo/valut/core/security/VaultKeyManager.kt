package app.lock.photo.valut.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the keys that protect vault media.
 *
 * Two-level design (envelope encryption), because it has to be both safe *and* fast:
 *
 *  - **KEK** — an AES-256-GCM key inside the Android Keystore. Non-exportable, hardware backed
 *    where available. It only ever encrypts 32 bytes: the data key.
 *  - **DEK** — a random AES-256 data key used for the actual file bytes. It lives wrapped by the
 *    KEK on disk and is unwrapped once per process into memory.
 *
 * Why not use the Keystore key on the files directly (what [KEY_VERSION_KEYSTORE] files did):
 * every cipher update on a Keystore key round-trips to the secure element, which measured at
 * roughly 0.1 MB/s here — a 3 MB video took 35 seconds to decrypt. The DEK runs in-process on
 * the CPU's AES instructions instead, which is hundreds of MB/s, while the raw key material is
 * still protected at rest by the Keystore.
 */
@Singleton
class VaultKeyManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    @Volatile
    private var cachedDataKey: SecretKey? = null

    /** The Keystore key-encryption key. Creates it on first use. */
    @Synchronized
    fun getOrCreateVaultKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .build()
        )
        return generator.generateKey()
    }

    /**
     * The in-process data key used for file bytes. Unwrapped from disk on first call (one
     * Keystore operation on 32 bytes), then cached for the life of the process.
     */
    @Synchronized
    fun getOrCreateDataKey(): SecretKey {
        cachedDataKey?.let { return it }

        val stored = prefs.getString(KEY_WRAPPED_DEK, null)
        val key = if (stored != null) unwrapDataKey(stored) else null
        val resolved = key ?: createAndStoreDataKey()
        cachedDataKey = resolved
        return resolved
    }

    /** Picks the right key for a file, based on the version recorded in its header. */
    fun keyForVersion(keyVersion: Int): SecretKey =
        if (keyVersion >= KEY_VERSION_DATA_KEY) getOrCreateDataKey() else getOrCreateVaultKey()

    fun hasVaultKey(): Boolean = keyStore().containsAlias(KEY_ALIAS)

    /** Deletes the master key. Only call during a full vault reset — files become unreadable. */
    fun deleteVaultKeyOnlyForFullReset() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
        cachedDataKey = null
        runCatching { prefs.edit { remove(KEY_WRAPPED_DEK) } }
    }

    fun getCurrentKeyAlias(): String = KEY_ALIAS

    /** Version stamped into newly written files. */
    fun getCurrentKeyVersion(): Int = KEY_VERSION_DATA_KEY

    // --- data-key wrapping ---

    private fun createAndStoreDataKey(): SecretKey {
        val raw = ByteArray(KEY_SIZE_BITS / 8).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateVaultKey())
        val wrapped = cipher.doFinal(raw)
        val blob = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + SEPARATOR +
            Base64.encodeToString(wrapped, Base64.NO_WRAP)
        prefs.edit { putString(KEY_WRAPPED_DEK, blob) }
        return SecretKeySpec(raw, KeyProperties.KEY_ALGORITHM_AES)
    }

    private fun unwrapDataKey(blob: String): SecretKey? = runCatching {
        val (ivPart, keyPart) = blob.split(SEPARATOR)
        val iv = Base64.decode(ivPart, Base64.NO_WRAP)
        val wrapped = Base64.decode(keyPart, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateVaultKey(),
            GCMParameterSpec(GCM_TAG_BITS, iv)
        )
        SecretKeySpec(cipher.doFinal(wrapped), KeyProperties.KEY_ALGORITHM_AES)
    }.getOrNull()

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "private_lock_vault_master_key_v1"
        const val KEY_SIZE_BITS = 256

        /** Legacy: file bytes encrypted with the Keystore key itself (very slow to read). */
        const val KEY_VERSION_KEYSTORE = 1

        /** Current: file bytes encrypted with the wrapped, in-process data key. */
        const val KEY_VERSION_DATA_KEY = 2

        private const val PREFS = "vault_key_store"
        private const val KEY_WRAPPED_DEK = "wrapped_data_key_v2"
        private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val SEPARATOR = ":"
    }
}
