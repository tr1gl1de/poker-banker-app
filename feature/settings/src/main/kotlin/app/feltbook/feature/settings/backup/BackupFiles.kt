package app.feltbook.feature.settings.backup

import android.content.Context
import androidx.core.net.toUri
import app.feltbook.core.data.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Файл резервной копии, выбранный через SAF (`CreateDocument` / `OpenDocument`). [uri] — строка `content://…`:
 * ViewModel не знает об Android-классах, тесты подменяют реализацию.
 */
interface BackupFiles {
    /** Записать [text] (UTF-8) поверх содержимого документа. */
    suspend fun write(uri: String, text: String)

    /** Прочитать документ целиком (UTF-8); больше [MAX_BYTES] — [IOException]. */
    suspend fun read(uri: String): String

    companion object {
        /** Предел размера копии: защита от случайно выбранного огромного файла. */
        const val MAX_BYTES: Int = 64 * 1024 * 1024
    }
}

class ContentResolverBackupFiles @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : BackupFiles {
    override suspend fun write(uri: String, text: String) = withContext(io) {
        val stream = context.contentResolver.openOutputStream(uri.toUri(), "wt")
            ?: throw IOException("cannot open document for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override suspend fun read(uri: String): String = withContext(io) {
        val stream = context.contentResolver.openInputStream(uri.toUri())
            ?: throw IOException("cannot open document for reading")
        stream.use { input ->
            val bytes = input.readNBytesCompat(BackupFiles.MAX_BYTES + 1)
            if (bytes.size > BackupFiles.MAX_BYTES) throw IOException("backup file is too large")
            bytes.toString(Charsets.UTF_8)
        }
    }

    /** `InputStream.readNBytes` есть только с API 33 — читаем сами не больше [limit] байт. */
    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (total < limit) {
            val read = read(buffer, 0, minOf(buffer.size, limit - total))
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal interface BackupFilesModule {
    @Binds
    fun backupFiles(impl: ContentResolverBackupFiles): BackupFiles
}
