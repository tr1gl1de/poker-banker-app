package app.feltbook.core.data.di

import javax.inject.Qualifier

/** `Dispatchers.IO`: файлы, DataStore, кодирование резервной копии. В тестах подменяется тестовым диспетчером. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
