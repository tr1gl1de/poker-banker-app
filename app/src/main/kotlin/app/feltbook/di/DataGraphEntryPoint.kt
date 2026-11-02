package app.feltbook.di

import app.feltbook.core.domain.repository.BackupRepository
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Запрашивает все репозитории :core:data, чтобы Dagger проверял их граф при сборке :app ещё до появления
 * ViewModel'ей (M3). Когда репозитории начнут инжектить экраны, точку входа можно удалить.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DataGraphEntryPoint {
    fun gameRepository(): GameRepository

    fun playerNameRepository(): PlayerNameRepository

    fun settingsRepository(): SettingsRepository

    fun backupRepository(): BackupRepository
}
