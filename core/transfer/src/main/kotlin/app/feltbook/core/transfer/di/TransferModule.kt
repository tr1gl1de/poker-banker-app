package app.feltbook.core.transfer.di

import android.content.Context
import app.feltbook.core.transfer.AndroidTransferEnvironment
import app.feltbook.core.transfer.DefaultTransferSession
import app.feltbook.core.transfer.GmsNearbyTransport
import app.feltbook.core.transfer.TransferEnvironment
import app.feltbook.core.transfer.TransferSession
import app.feltbook.core.transfer.file.AndroidPackageFileStore
import app.feltbook.core.transfer.file.PackageFileStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Module
@InstallIn(SingletonComponent::class)
internal interface TransferBindings {
    @Binds
    fun environment(impl: AndroidTransferEnvironment): TransferEnvironment

    @Binds
    fun fileStore(impl: AndroidPackageFileStore): PackageFileStore
}

/** Сессия — своя у каждой ViewModel (без scope): ViewModel закрывает её в `onCleared`. */
@Module
@InstallIn(ViewModelComponent::class)
internal object TransferSessionModule {
    @Provides
    fun session(@ApplicationContext context: Context): TransferSession = DefaultTransferSession(
        transport = GmsNearbyTransport(context),
        parentScope = CoroutineScope(Dispatchers.Main.immediate),
    )
}
