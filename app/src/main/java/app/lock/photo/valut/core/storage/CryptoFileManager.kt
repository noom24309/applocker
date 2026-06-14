package app.lock.photo.valut.core.storage

import android.content.Context
import android.net.Uri
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
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
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
        val tempPlain = secureCacheManager.createTempDecryptedFile(ext)
        val copied = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                BufferedOutputStream(FileOutputStream(tempPlain)).use { out -> input.copyTo(out) }
            } ?: run { tempPlain.delete(); return null }
        } catch (e: Exception) {
            tempPlain.delete()
            return null
        }
        if (copied <= 0L && tempPlain.length() == 0L) {
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
            null
        } finally {
            secureDeletePlainFile(tempPlain)
        }
    }

    private fun encryptStreamToFile(input: InputStream, outputFile: File) {
        val key = keyManager.getOrCreateVaultKey()
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
        BufferedOutputStream(FileOutputStream(tmp)).use { raw ->
            header.writeTo(raw)
            CipherOutputStream(raw, cipher).use { cipherOut ->
                input.copyTo(cipherOut, BUFFER)
            }
        }
        if (!tmp.renameTo(outputFile)) {
            tmp.copyTo(outputFile, overwrite = true)
            tmp.delete()
        }
    }

    // --- Decrypt ---

    fun decryptFileToInputStream(encryptedFile: File): InputStream {
        if (!encryptedFile.exists()) throw InvalidVaultFileException()
        val raw = BufferedInputStream(FileInputStream(encryptedFile))
        val header = EncryptedFileHeader.readFrom(raw)
        val cipher = cipherForDecrypt(header)
        return CipherInputStream(raw, cipher)
    }

    fun decryptFileToBytes(encryptedFile: File): ByteArray =
        decryptFileToInputStream(encryptedFile).use { it.readBytes() }

    fun decryptFileToTemp(encryptedFile: File, purpose: DecryptPurpose, extension: String = "tmp"): File {
        val temp = secureCacheManager.createTempDecryptedFile(extension)
        decryptFileToInputStream(encryptedFile).use { input ->
            BufferedOutputStream(FileOutputStream(temp)).use { out -> input.copyTo(out, BUFFER) }
        }
        return temp
    }

    /** Fully decrypts (verifying the GCM tag) to confirm the file is intact and readable. */
    fun verifyEncryptedFile(encryptedFile: File): Boolean = try {
        decryptFileToInputStream(encryptedFile).use { input ->
            val buffer = ByteArray(BUFFER)
            while (input.read(buffer) != -1) { /* discard; reaching EOF validates the GCM tag */ }
        }
        true
    } catch (e: Exception) {
        false
    }

    private fun cipherForDecrypt(header: EncryptedFileHeader): Cipher {
        if (!keyManager.hasVaultKey()) throw VaultKeyUnavailableException()
        val cipher = Cipher.getInstance(transformation)
        try {
            cipher.init(
                Cipher.DECRYPT_MODE,
                keyManager.getOrCreateVaultKey(),
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
        const val BUFFER = 64 * 1024
    }
}
