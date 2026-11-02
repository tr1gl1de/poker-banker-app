package app.feltbook.core.transfer.file

import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.transfer.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Файл `.feltbook`, готовый к Sharesheet. [uri] — `content://` FileProvider строкой, чтобы ViewModel тестировались
 * без Android.
 */
data class SharedFile(val uri: String, val fileName: String)

/** Запасной путь (ТЗ §6): пакет пишется в файл и уходит через «Поделиться». */
interface PackageFileStore {
    /** Пишет [text] в `cache/handover/<baseName>.feltbook` (прошлые файлы удаляются). */
    suspend fun writeForShare(text: String, baseName: String): SharedFile
}

class AndroidPackageFileStore internal constructor(private val context: Context, private val io: CoroutineDispatcher) :
    PackageFileStore {
    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Dispatchers.IO)

    override suspend fun writeForShare(text: String, baseName: String): SharedFile = withContext(io) {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val name = fileName(baseName)
        val file = File(dir, name)
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, FeltbookShare.authority(context), file)
        SharedFile(uri.toString(), name)
    }

    companion object {
        /** Совпадает с `res/xml/feltbook_shared_files.xml`. */
        const val DIR: String = "handover"
        private const val MAX_BASE_NAME = 60
        private val UNSAFE = Regex("""[\\/:*?"<>|\u0000-\u001F]""")

        /** «Пятничный стол» → «Пятничный стол.feltbook»; запрещённые в именах символы — «_». */
        fun fileName(baseName: String): String {
            val safe = baseName.replace(UNSAFE, "_").trim().trim('.').take(MAX_BASE_NAME).ifBlank { "feltbook" }
            return "$safe.${FeltbookCodec.FILE_EXTENSION}"
        }
    }
}

/** Провайдер только для `cache/handover/` (манифест `:core:transfer`). */
class HandoverFileProvider : FileProvider(R.xml.feltbook_shared_files)

/** Sharesheet для файла `.feltbook`. */
object FeltbookShare {
    /** `${applicationId}.feltbook.files` — как в манифесте `:core:transfer`. */
    fun authority(context: Context): String = context.packageName + ".feltbook.files"

    /** Главная Activity приложения: её нет в Sharesheet — отправить игру самому себе нельзя по ошибке. */
    private const val MAIN_ACTIVITY = "app.feltbook.MainActivity"

    fun intent(context: Context, file: SharedFile, chooserTitle: String): Intent {
        val uri = file.uri.toUri()
        val send = Intent(Intent.ACTION_SEND).apply {
            type = FeltbookCodec.MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.fileName)
            clipData = ClipData.newRawUri(file.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val self = arrayOf(ComponentName(context.packageName, MAIN_ACTIVITY))
        return Intent.createChooser(send, chooserTitle)
            .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, self)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
