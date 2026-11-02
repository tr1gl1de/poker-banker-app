package app.feltbook.core.transfer.file

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Открытый файл `.feltbook`/`.pokerbank`, ждущий экрана приёма. [result] — уже проверенный `FeltbookCodec.decode`
 * (формат → версия → хеш → структура → журнал).
 */
data class IncomingFile(val fileName: String?, val result: DecodeResult) {
    /** `HANDOVER`/`TEMPLATE`/`BACKUP` или null, если файл не прочитан. */
    val kind: String? get() = (result as? DecodeResult.Ok)?.let { FeltbookCodec.kindOf(it.pkg) }
}

/** Файл, открытый снаружи, до экрана «Принять игру». В памяти: после смерти процесса файл открывают заново. */
@Singleton
class IncomingPackageStore @Inject constructor() {
    private val mutable = MutableStateFlow<IncomingFile?>(null)
    val pending: StateFlow<IncomingFile?> = mutable.asStateFlow()

    fun put(file: IncomingFile) {
        mutable.value = file
    }

    fun clear() {
        mutable.value = null
    }
}

/**
 * Вход «открыть файл» (ACTION_VIEW / ACTION_SEND из Telegram, Quick Share, почты, «Файлов»): читает файл, проверяет
 * кодеком и кладёт в [IncomingPackageStore]. Дальше `:app` открывает экран «Принять игру» в режиме файла.
 * Читать сразу, пока у Activity есть временный доступ к `content://`.
 */
@Singleton
class HandoverImportHandler internal constructor(
    private val context: Context,
    private val store: IncomingPackageStore,
    private val io: CoroutineDispatcher,
) {
    @Inject
    constructor(@ApplicationContext context: Context, store: IncomingPackageStore) :
        this(context, store, Dispatchers.IO)

    /** Читает [uri] и кладёт результат в хранилище. Нечитаемый файл — `DecodeResult.NotFeltbook`. */
    suspend fun open(uri: Uri): IncomingFile = withContext(io) {
        val resolver = context.contentResolver
        val name = displayName(resolver, uri)
        val text = try {
            openStream(resolver, uri)?.use { readLimited(it) }
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        }
        val result = text?.let(FeltbookCodec::decode) ?: DecodeResult.NotFeltbook
        IncomingFile(name, result).also(store::put)
    }

    /**
     * Только `content://`: путь `file://` из чужого приложения дал бы прочитать любой файл, доступный Feltbook
     * (его базу), — такие URI отклоняются.
     */
    private fun openStream(resolver: ContentResolver, uri: Uri): InputStream? =
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) resolver.openInputStream(uri) else null

    private fun displayName(resolver: ContentResolver, uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    } catch (_: SecurityException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } ?: uri.lastPathSegment

    companion object {
        /** Файл из входящего интента, если это «открыть»/«поделиться» файлом. */
        fun uriFrom(intent: Intent?): Uri? = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            else -> null
        }

        /** Файл передачи — десятки КБ; больше 4 МБ — не наш (или чужой огромный файл). */
        const val MAX_FILE_BYTES: Int = 4 * 1024 * 1024

        internal fun readLimited(input: InputStream): String? {
            val bytes = input.readNBytesCompat(MAX_FILE_BYTES + 1)
            if (bytes.size > MAX_FILE_BYTES) return null
            return try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                null
            }
        }

        private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(BUFFER)
            var total = 0
            while (total < limit) {
                val n = read(buffer, 0, minOf(buffer.size, limit - total))
                if (n < 0) break
                out.write(buffer, 0, n)
                total += n
            }
            return out.toByteArray()
        }

        private const val BUFFER = 8 * 1024
    }
}
