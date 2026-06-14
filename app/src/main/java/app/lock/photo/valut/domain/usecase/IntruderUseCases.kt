package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.core.intruder.IntruderAttemptHandler
import app.lock.photo.valut.core.intruder.IntruderCameraManager
import app.lock.photo.valut.data.local.entity.IntruderAttemptEntity
import app.lock.photo.valut.domain.model.IntruderTriggerContext
import app.lock.photo.valut.domain.repository.IntruderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Entry point for unlock screens: report a wrong PIN/Pattern attempt for possible capture. */
class HandleIntruderWrongAttemptUseCase @Inject constructor(
    private val handler: IntruderAttemptHandler
) {
    operator fun invoke(context: IntruderTriggerContext) = handler.onWrongAttempt(context)
}

class ObserveIntruderAttemptsUseCase @Inject constructor(
    private val repository: IntruderRepository
) {
    operator fun invoke(): Flow<List<IntruderAttemptEntity>> = repository.observeAttempts()
}

class DeleteIntruderAttemptUseCase @Inject constructor(
    private val repository: IntruderRepository
) {
    suspend operator fun invoke(ids: List<Long>) = repository.deleteAttempts(ids)
    suspend fun clearAll() = repository.clearAllAttempts()
}

class ExportIntruderPhotoUseCase @Inject constructor(
    private val repository: IntruderRepository
) {
    suspend operator fun invoke(id: Long): Boolean = repository.exportAttemptPhoto(id)
}

class CleanupOldIntruderAttemptsUseCase @Inject constructor(
    private val repository: IntruderRepository
) {
    suspend operator fun invoke() {
        repository.autoDeleteOldAttempts()
        repository.enforceMaxRecordLimit()
    }
}

class CheckIntruderPermissionUseCase @Inject constructor(
    private val cameraManager: IntruderCameraManager
) {
    fun hasPermission(): Boolean = cameraManager.hasCameraPermission()
    fun isCameraAvailable(): Boolean = cameraManager.isCameraAvailable()
}
