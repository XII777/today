package `as`.today.missyou.domain.repository

import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.domain.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    /** The rollover time, exposed separately because the journal engine depends on it. */
    val dayRolloverMinutes: Flow<Int>

    val appLockMode: Flow<AppLockMode>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun reset()
}
