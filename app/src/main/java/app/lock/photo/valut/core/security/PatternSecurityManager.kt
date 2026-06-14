package app.lock.photo.valut.core.security

import app.lock.photo.valut.core.common.Constants
import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.domain.model.SecurityResult
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Master pattern authority. A pattern is a list of grid node indices (0..8).
 * It is serialized deterministically and stored only as a salted, peppered hash.
 */
@Singleton
class PatternSecurityManager @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val hasher: CredentialHasher,
    private val weakChecker: WeakCredentialChecker
) {

    suspend fun isPatternCreated(): Boolean = dataStore.patternEnabled.first()

    suspend fun createPattern(nodes: List<Int>): SecurityResult {
        if (nodes.size < Constants.MIN_PATTERN_NODES || weakChecker.isWeakPattern(nodes)) {
            return SecurityResult.WeakCredential
        }
        val hashed = hasher.hash(serialize(nodes))
        dataStore.savePattern(hash = hashed.hash, salt = hashed.salt)
        return SecurityResult.Success
    }

    suspend fun verifyPattern(nodes: List<Int>): Boolean {
        if (nodes.size < Constants.MIN_PATTERN_NODES) return false
        val hash = dataStore.patternHash.first()
        val salt = dataStore.patternSalt.first()
        return hasher.verify(serialize(nodes), hash, salt)
    }

    suspend fun clearPattern() = dataStore.clearPattern()

    /** Deterministic serialization, e.g. [0,4,8] -> "0-4-8". */
    private fun serialize(nodes: List<Int>): CharArray =
        nodes.joinToString(separator = "-").toCharArray()
}
