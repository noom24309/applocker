package app.lock.photo.valut.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the Android Keystore-backed AES-256-GCM master key used to encrypt all
 * vault media. The key never leaves the Keystore and is not exportable.
 */
@Singleton
class VaultKeyManager @Inject constructor() {

    /** Returns the existing vault key, creating it on first use. */
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

    fun hasVaultKey(): Boolean = keyStore().containsAlias(KEY_ALIAS)

    /** Deletes the master key. Only call during a full vault reset — files become unreadable. */
    fun deleteVaultKeyOnlyForFullReset() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    fun getCurrentKeyAlias(): String = KEY_ALIAS

    fun getCurrentKeyVersion(): Int = KEY_VERSION

    private fun keyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "private_lock_vault_master_key_v1"
        const val KEY_VERSION = 1
        const val KEY_SIZE_BITS = 256
    }
}
