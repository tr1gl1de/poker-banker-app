package app.feltbook.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.feltbook.core.data.backup.RoomBackupRepository
import app.feltbook.core.data.datastore.DataStoreSettingsRepository
import app.feltbook.core.data.datastore.SettingsDataStore
import app.feltbook.core.data.db.FeltbookDatabase
import app.feltbook.core.data.db.Migrations
import app.feltbook.core.data.repository.RoomGameRepository
import app.feltbook.core.data.repository.RoomPlayerNameRepository
import app.feltbook.core.domain.repository.BackupRepository
import app.feltbook.core.domain.repository.GameRepository
import app.feltbook.core.domain.repository.PlayerNameRepository
import app.feltbook.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Хранилища: Room-база, DataStore, часы и диспетчер ввода-вывода. */
@Module
@InstallIn(SingletonComponent::class)
internal object StorageModule {
    @Provides
    @Singleton
    @Suppress("SpreadOperator") // Один раз при старте; addMigrations принимает только vararg.
    fun database(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): FeltbookDatabase = Room
        .databaseBuilder(context, FeltbookDatabase::class.java, FeltbookDatabase.NAME)
        .addMigrations(*Migrations.ALL)
        .setQueryCoroutineContext(io)
        .build()

    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher io: CoroutineDispatcher,
    ): DataStore<Preferences> = SettingsDataStore.create(CoroutineScope(io + SupervisorJob())) {
        context.preferencesDataStoreFile(SettingsDataStore.FILE_NAME)
    }

    @Provides
    fun clock(): Clock = Clock.System

    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

/** Реализации доменных репозиториев (интерфейсы — :core:domain, ADR 0006). */
@Module
@InstallIn(SingletonComponent::class)
internal interface RepositoryModule {
    @Binds
    @Singleton
    fun gameRepository(impl: RoomGameRepository): GameRepository

    @Binds
    @Singleton
    fun playerNameRepository(impl: RoomPlayerNameRepository): PlayerNameRepository

    @Binds
    @Singleton
    fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    fun backupRepository(impl: RoomBackupRepository): BackupRepository
}
