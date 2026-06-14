package app.lock.photo.valut.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a device-bound secret held in the Android Keystore, used as a "pepper"
 * on top of the salted PBKDF2 hash. The key material never leaves the Keystore,
 * so stored credential hashes cannot be brute-forced off-device.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    /** Returns HMAC-SHA256 of [data] using the device-bound Keystore key. */
    @Synchronized
    fun pepper(data: ByteArray): ByteArray {
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(getOrCreateKey())
        return mac.doFinal(data)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
            ANDROID_KEYSTORE
        )
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "plv_master_pepper"
        const val MAC_ALGORITHM = "HmacSHA256"
    }
}
