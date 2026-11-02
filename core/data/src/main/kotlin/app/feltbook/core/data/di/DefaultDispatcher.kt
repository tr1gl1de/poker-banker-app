package app.feltbook.core.data.di

import javax.inject.Qualifier

/** `Dispatchers.Default`: CPU-работа — кодирование и разбор резервной копии, SHA-256. В тестах — тестовый диспетчер. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
