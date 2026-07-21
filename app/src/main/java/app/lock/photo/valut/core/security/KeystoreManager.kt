package app.lock.photo.valut.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a device-bound secret used as a "pepper" on top of the salted PBKDF2 hash.
 *
 * The pepper key normally lives in the Android Keystore, so it never leaves secure
 * hardware and stored credential hashes cannot be brute-forced off-device. Some devices
 * have a broken/flaky Keystore where key generation throws [java.security.ProviderException]
 * ("Keystore operation failed"); on those we fall back to a random software pepper key
 * persisted in private storage. It is weaker than a hardware key, but it keeps the app
 * usable instead of crashing during PIN/pattern setup, and — critically — it is stable for
 * the life of the install so hashing and verification always use the same pepper.
 */
@Singleton
class KeystoreManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Returns HMAC-SHA256 of [data] using the pepper key (Keystore-backed, or software fallback). */
    @Synchronized
    fun pepper(data: ByteArray): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(pepperKey())
        return mac.doFinal(data)
    }

    /**
     * True when a pepper key exists (Keystore or software fallback). A fresh install after an
     * uninstall has neither, so credential data restored from a backup can never verify —
     * callers use this to detect that state and reset to a fresh setup.
     */
    fun hasPepperKey(): Boolean = keystoreAliasExists() || hasSoftwarePepper()

    /**
     * Resolves the pepper key with a strict, stable priority so a device never silently
     * switches pepper (which would invalidate every stored hash):
     *  1. An existing Keystore key always wins — keeps prior healthy installs verifying.
     *  2. An already-committed software pepper is reused (broken-Keystore devices).
     *  3. Otherwise try to create a Keystore key (healthy devices / new installs).
     *  4. If the Keystore can't create one, create+persist a software pepper.
     */
    @Synchronized
    private fun pepperKey(): SecretKey {
        existingKeystoreKey()?.let { return it }
        if (hasSoftwarePepper()) return softwarePepperKey()
        createKeystoreKey()?.let { return it }
        return softwarePepperKey()
    }

    // --- Keystore-backed key ---

    private fun keystore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun keystoreAliasExists(): Boolean =
        runCatching { keystore().containsAlias(KEY_ALIAS) }.getOrDefault(false)

    private fun existingKeystoreKey(): SecretKey? = runCatching {
        (keystore().getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }.getOrNull()

    /** Generates the Keystore key; returns null if the device's Keystore can't. */
    private fun createKeystoreKey(): SecretKey? =
        runCatching { generateKeystoreKey() }.getOrElse {
            // A half-written/corrupt alias can make generation fail; drop it and retry once.
            runCatching { keystore().deleteEntry(KEY_ALIAS) }
            runCatching { generateKeystoreKey() }.getOrNull()
        }

    private fun generateKeystoreKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN).build())
        return generator.generateKey()
    }

    // --- Software fallback key (used only when the Keystore is unusable) ---

    private fun softwarePrefs() = context.getSharedPreferences(SOFT_PREFS, Context.MODE_PRIVATE)

    private fun hasSoftwarePepper(): Boolean =
        runCatching { softwarePrefs().contains(PREF_SOFT_PEPPER) }.getOrDefault(false)

    private fun softwarePepperKey(): SecretKey {
        val prefs = softwarePrefs()
        val stored = prefs.getString(PREF_SOFT_PEPPER, null)
        val bytes = if (stored != null) {
            Base64.decode(stored, Base64.NO_WRAP)
        } else {
            ByteArray(SOFT_KEY_BYTES).also { SecureRandom().nextBytes(it) }.also { fresh ->
                prefs.edit().putString(PREF_SOFT_PEPPER, Base64.encodeToString(fresh, Base64.NO_WRAP)).apply()
            }
        }
        return SecretKeySpec(bytes, MAC_ALGORITHM)
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "plv_master_pepper"
        const val MAC_ALGORITHM = "HmacSHA256"

        const val SOFT_PREFS = "plv_pepper_fallback"
        const val PREF_SOFT_PEPPER = "soft_pepper"
        const val SOFT_KEY_BYTES = 32
    }
}
