package app.feltbook.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.CoroutineScope

/** Файл настроек и его создание — одно место для приложения (Hilt) и тестов. */
internal object SettingsDataStore {
    const val FILE_NAME: String = "feltbook_settings"

    /** Испорченный файл настроек — к значениям по умолчанию, а не падение приложения при каждом запуске. */
    fun corruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
        ReplaceFileCorruptionHandler { emptyPreferences() }

    fun create(scope: CoroutineScope, produceFile: () -> File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = corruptionHandler(),
            scope = scope,
            produceFile = produceFile,
        )
}
