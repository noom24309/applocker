package app.lock.photo.valut.domain.usecase

import app.lock.photo.valut.core.datastore.AppSettingsDataStore
import app.lock.photo.valut.data.local.dao.AppLockStatsDao
import app.lock.photo.valut.data.local.entity.AppLockStatsEntity
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Records minimal, local-only App Lock counters (success/failed unlocks, locked-app
 * opens). Nothing is uploaded, shared or used for ads; no foreground-app history is kept.
 * No-op when the user has turned off local stats.
 */
class RecordLocalAppLockStatsUseCase @Inject constructor(
    private val statsDao: AppLockStatsDao,
    private val dataStore: AppSettingsDataStore
) {

    enum class Event { SUCCESS, FAILURE, LOCKED_APP_OPEN }

    suspend operator fun invoke(event: Event) {
        if (!dataStore.localStatsEnabled.first()) return
        val date = today()
        if (statsDao.getForDate(date) == null) {
            statsDao.upsert(AppLockStatsEntity(date = date))
        }
        when (event) {
            Event.SUCCESS -> statsDao.incrementSuccess(date)
            Event.FAILURE -> statsDao.incrementFailure(date)
            Event.LOCKED_APP_OPEN -> statsDao.incrementLockedOpen(date)
        }
    }

    /** Adds elapsed protection time to today's row (no-op if stats are disabled). */
    suspend fun recordProtectionMillis(millis: Long) {
        if (millis <= 0L || !dataStore.localStatsEnabled.first()) return
        val date = today()
        if (statsDao.getForDate(date) == null) statsDao.upsert(AppLockStatsEntity(date = date))
        statsDao.addProtectionTime(date, millis)
    }

    private fun today(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
}
