package app.lock.photo.valut.core.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import app.lock.photo.valut.core.security.VaultKeyManager
import app.lock.photo.valut.domain.model.MediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central authenticated-encryption engine for vault files (AES/GCM/NoPadding with
 * a Keystore-backed key). Streams data so large videos never load fully into memory.
 * No key, IV, path or decrypted content is ever logged.
 */
@Singleton
class CryptoFileManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyManager: VaultKeyManager,
    private val vaultFileManager: VaultFileManager,
    private val secureCacheManager: SecureCacheManager
) {

    private val transformation = "AES/GCM/NoPadding"

    // --- Encrypt ---

    fun encryptFile(inputFile: File, outputFile: File, mediaId: Long): CryptoResult {
        if (!inputFile.exists()) return CryptoResult.Error(CryptoResult.Error.Reason.CANNOT_OPEN)
        return try {
            val checksum = sha256(inputFile)
            FileInputStream(inputFile).use { input -> encryptStreamToFile(input, outputFile) }
            CryptoResult.Success(checksum, outputFile.length())
        } catch (e: VaultKeyUnavailableException) {
            outputFile.delete()
            CryptoResult.Error(CryptoResult.Error.Reason.KEY_UNAVAILABLE)
        } catch (e: Exception) {
            outputFile.delete()
            CryptoResult.Error(CryptoResult.Error.Reason.ENCRYPT_FAILED)
        }
    }

    fun encryptBytesToFile(bytes: ByteArray, outputFile: File): CryptoResult = try {
        bytes.inputStream().use { input -> encryptStreamToFile(input, outputFile) }
        CryptoResult.Success(sha256Bytes(bytes), outputFile.length())
    } catch (e: VaultKeyUnavailableException) {
        outputFile.delete()
        CryptoResult.Error(CryptoResult.Error.Reason.KEY_UNAVAILABLE)
    } catch (e: Exception) {
        outputFile.delete()
        CryptoResult.Error(CryptoResult.Error.Reason.ENCRYPT_FAILED)
    }

    /** Encrypts a picked Uri straight into the vault, building an encrypted thumbnail too. */
    fun encryptUriToVault(uri: Uri, mediaType: MediaType): EncryptedVaultFileResult? {
        vaultFileManager.createVaultDirectories()
        secureCacheManager.ensureTempDir()
        val mime = context.contentResolver.getType(uri)
            ?: if (mediaType == MediaType.VIDEO) "video/mp4" else "image/jpeg"
        val ext = vaultFileManager.guessExtension(mime, mediaType)

        // Copy to a short-lived temp plain file so we can read metadata + thumbnail, then encrypt.
        // Staged in the import-only area: the shared temp dir gets wiped whenever a vault screen
        // resumes, which is exactly when the picker closes and this copy is running.
        val tempPlain = secureCacheManager.createImportStagingFile(ext)
        val copied = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(tempPlain)).use { out -> input.copyTo(out) }
            } ?: run {
                Log.w(TAG, "import: cannot open source stream (mime=$mime)")
                tempPlain.delete(); return null
            }
        } catch (e: Exception) {
            // Mime + message only: never the path or content.
            Log.w(TAG, "import: staging copy failed (mime=$mime): ${e.javaClass.simpleName} ${e.message}")
            tempPlain.delete()
            return null
        }
        if (copied <= 0L && tempPlain.length() == 0L) {
            Log.w(TAG, "import: staged 0 bytes (mime=$mime)")
            tempPlain.delete()
            return null
        }

        return try {
            val plainSize = tempPlain.length()
            val checksum = sha256(tempPlain)
            val meta = vaultFileManager.readMetadata(tempPlain, mediaType)
            val thumbBytes = vaultFileManager.generateThumbnailBytes(tempPlain, mediaType)

            val vaultFileName = "${UUID.randomUUID()}.plv"
            val encFile = File(vaultFileManager.encryptedMediaDir(mediaType), vaultFileName)
            FileInputStream(tempPlain).use { input -> encryptStreamToFile(input, encFile) }

            val encThumb = thumbBytes?.let { bytes ->
                val thumbFile = File(vaultFileManager.encryptedThumbnailsDir, "${UUID.randomUUID()}_thumb.plv")
                if (encryptBytesToFile(bytes, thumbFile) is CryptoResult.Success) thumbFile else null
            }

            EncryptedVaultFileResult(
                vaultFileName = vaultFileName,
                encryptedFilePath = encFile.absolutePath,
                encryptedThumbnailPath = encThumb?.absolutePath,
                mimeType = mime,
                plainSizeBytes = plainSize,
                encryptedSizeBytes = encFile.length(),
                width = meta.width,
                height = meta.height,
                durationMillis = meta.durationMillis,
                checksum = checksum
            )
        } catch (e: Exception) {
            Log.w(TAG, "import: encrypt failed (mime=$mime): ${e.javaClass.simpleName} ${e.message}")
            null
        } finally {
            secureDeletePlainFile(tempPlain)
        }
    }

    private fun encryptStreamToFile(input: InputStream, outputFile: File) {
        // Bulk bytes go through the in-process data key, not the Keystore key: the Keystore
        // round-trips every chunk to the secure element (~0.1 MB/s measured), which made large
        // videos take tens of seconds. The header records which key was used.
        val key = keyManager.getOrCreateDataKey()
        val cipher = Cipher.getInstance(transformation)
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key)
        } catch (e: Exception) {
            throw VaultKeyUnavailableException(e)
        }
        val header = EncryptedFileHeader(
            version = EncryptedFileHeader.CURRENT_VERSION,
            keyVersion = keyManager.getCurrentKeyVersion(),
            iv = cipher.iv
        )
        val tmp = File(outputFile.parentFile, outputFile.name + ".tmp")
        BufferedOutputStream(FileOutputStream(tmp), BUFFER).use { raw ->
            header.writeTo(raw)
            // Same reason as the decrypt path: feed the cipher big blocks by hand rather than
            // going through CipherOutputStream's small internal buffering.
            streamThroughCipher(input, raw, cipher)
        }
        if (!tmp.renameTo(outputFile)) {
            tmp.copyTo(outputFile, overwrite = true)
            tmp.delete()
        }
    }

    // --- Decrypt ---

    /**
     * Decrypts straight into [output] (export, share, "save to gallery") using the same manual
     * cipher loop as [decryptFileToTemp] — no plain temp file in between.
     *
     * This replaced a `CipherInputStream`-based reader: handing GCM ciphertext to the caller
     * through CipherInputStream made every export cost time quadratic in the file size, so
     * exporting a video effectively never finished. See [decryptFileToTemp] for the measurements.
     */
    fun decryptFileToStream(encryptedFile: File, output: java.io.OutputStream) {
        if (!encryptedFile.exists()) throw InvalidVaultFileException()
        BufferedInputStream(FileInputStream(encryptedFile), BUFFER).use { raw ->
            val header = EncryptedFileHeader.readFrom(raw)
            val cipher = cipherForDecrypt(header)
            streamThroughCipher(raw, output, cipher)
            output.flush()
        }
    }

    /**
     * One-shot decrypt (photos, thumbnails, documents). Reads the ciphertext and hands it to the
     * cipher in a single call — same reason as [decryptFileToTemp]: streaming GCM through
     * CipherInputStream costs time quadratic in the file size.
     */
    fun decryptFileToBytes(encryptedFile: File): ByteArray {
        if (!encryptedFile.exists()) throw InvalidVaultFileException()
        return BufferedInputStream(FileInputStream(encryptedFile), BUFFER).use { raw ->
            val header = EncryptedFileHeader.readFrom(raw)
            val cipher = cipherForDecrypt(header)
            cipher.doFinal(raw.readBytes())
        }
    }

    /**
     * Decrypts to a temp file with a manual cipher loop instead of [CipherInputStream].
     *
     * Why: CipherInputStream pulls GCM through a tiny internal buffer, and the provider cannot
     * release plaintext until the trailing tag verifies — the repeated buffer growth makes cost
     * grow with the *square* of the file size. Measured on device: 0.5 MB took 4 s, 3 MB took
     * 35 s, and a 16 MB video several minutes. Feeding the cipher large blocks ourselves keeps
     * it linear, which turns those same files into a fraction of a second.
     */
    fun decryptFileToTemp(encryptedFile: File, purpose: DecryptPurpose, extension: String = "tmp"): File {
        if (!encryptedFile.exists()) throw InvalidVaultFileException()
        val temp = secureCacheManager.createTempDecryptedFile(extension)
        BufferedInputStream(FileInputStream(encryptedFile), BUFFER).use { raw ->
            val header = EncryptedFileHeader.readFrom(raw)
            val cipher = cipherForDecrypt(header)
            BufferedOutputStream(FileOutputStream(temp), BUFFER).use { out ->
                streamThroughCipher(raw, out, cipher)
            }
        }
        return temp
    }

    /**
     * Pumps [input] through [cipher] into [output] in large blocks. Works for both directions;
     * the final [Cipher.doFinal] writes the GCM tag (encrypt) or verifies it (decrypt).
     */
    private fun streamThroughCipher(input: InputStream, output: java.io.OutputStream, cipher: Cipher) {
        val buffer = ByteArray(BUFFER)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            cipher.update(buffer, 0, read)?.let { if (it.isNotEmpty()) output.write(it) }
        }
        cipher.doFinal()?.let { if (it.isNotEmpty()) output.write(it) }
    }

    /** Fully decrypts (verifying the GCM tag) to confirm the file is intact and readable. */
    fun verifyEncryptedFile(encryptedFile: File): Boolean = try {
        BufferedInputStream(FileInputStream(encryptedFile), BUFFER).use { raw ->
            val header = EncryptedFileHeader.readFrom(raw)
            val cipher = cipherForDecrypt(header)
            // Discarding output; reaching doFinal without throwing validates the GCM tag.
            streamThroughCipher(raw, NullOutputStream, cipher)
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Sink for verification passes: the plaintext is not needed, only the tag check. */
    private object NullOutputStream : java.io.OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private fun cipherForDecrypt(header: EncryptedFileHeader): Cipher {
        if (!keyManager.hasVaultKey()) throw VaultKeyUnavailableException()
        val cipher = Cipher.getInstance(transformation)
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyManager.keyForVersion(header.keyVersion),
                GCMParameterSpec(EncryptedFileHeader.GCM_TAG_BITS, header.iv)
            )
        } catch (e: Exception) {
            throw VaultKeyUnavailableException(e)
        }
        return cipher
    }

    // --- Helpers ---

    fun calculateChecksum(file: File): String = sha256(file)

    fun secureDeletePlainFile(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(BUFFER)
            var read = input.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val BUFFER = 256 * 1024

        /** Import diagnostics only — mime types and error names, never paths or content. */
        const val TAG = "VaultImport"
    }
}
