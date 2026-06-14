package app.lock.photo.valut.core.storage

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Encrypted vault file layout:
 *
 *   magic(4) | version(1) | keyVersion(1) | ivLength(1) | iv(ivLength) | ciphertext+GCM tag
 *
 * The header is plain (no secrets); the IV is required to decrypt and is safe to store.
 */
data class EncryptedFileHeader(
    val version: Int,
    val keyVersion: Int,
    val iv: ByteArray
) {
    fun writeTo(out: OutputStream) {
        out.write(MAGIC)
        out.write(version and 0xFF)
        out.write(keyVersion and 0xFF)
        out.write(iv.size and 0xFF)
        out.write(iv)
    }

    // Generated equals/hashCode (ByteArray needs content comparison).
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedFileHeader) return false
        return version == other.version && keyVersion == other.keyVersion && iv.contentEquals(other.iv)
    }

    override fun hashCode(): Int =
        (version * 31 + keyVersion) * 31 + iv.contentHashCode()

    companion object {
        /** "PLV1" */
        val MAGIC = byteArrayOf(0x50, 0x4C, 0x56, 0x31)
        const val CURRENT_VERSION = 1
        const val GCM_TAG_BITS = 128

        /** Reads and validates the header from [input]; throws [InvalidVaultFileException] if invalid. */
        fun readFrom(input: InputStream): EncryptedFileHeader {
            val magic = ByteArray(MAGIC.size)
            if (input.read(magic) != MAGIC.size || !magic.contentEquals(MAGIC)) {
                throw InvalidVaultFileException()
            }
            val version = input.read()
            val keyVersion = input.read()
            val ivLength = input.read()
            if (version < 0 || keyVersion < 0 || ivLength <= 0 || ivLength > 16) {
                throw InvalidVaultFileException()
            }
            val iv = ByteArray(ivLength)
            if (input.read(iv) != ivLength) throw InvalidVaultFileException()
            return EncryptedFileHeader(version, keyVersion, iv)
        }
    }
}

/** Thrown when a vault file's header is missing or malformed. */
class InvalidVaultFileException : IOException("Invalid vault file")

/** Thrown when the Keystore key is missing or decryption fails (possible key loss). */
class VaultKeyUnavailableException(cause: Throwable? = null) :
    IOException("Vault key unavailable", cause)

/** What a decrypt-to-temp operation is for (affects naming only). */
enum class DecryptPurpose { PHOTO_VIEW, VIDEO_PLAYBACK, EXPORT }

/** Outcome of an encrypt/verify operation. */
sealed interface CryptoResult {
    data class Success(val checksum: String, val sizeBytes: Long) : CryptoResult
    data class Error(val reason: Reason) : CryptoResult {
        enum class Reason { CANNOT_OPEN, ENCRYPT_FAILED, KEY_UNAVAILABLE, VERIFY_FAILED }
    }
}

/** Result of encrypting a picked Uri into the vault. */
data class EncryptedVaultFileResult(
    val vaultFileName: String,
    val encryptedFilePath: String,
    val encryptedThumbnailPath: String?,
    val mimeType: String,
    val plainSizeBytes: Long,
    val encryptedSizeBytes: Long,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val checksum: String
)
