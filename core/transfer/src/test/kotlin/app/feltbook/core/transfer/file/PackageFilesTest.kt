package app.feltbook.core.transfer.file

import android.content.Intent
import android.net.Uri
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.transfer.TestGames
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Файл-фолбэк: имя файла, открытие `.feltbook`/`.pokerbank`, разбор входящего интента. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PackageFilesTest {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `имя файла — название игры без запрещённых символов и с расширением feltbook`() {
        assertEquals("Пятничный стол.feltbook", AndroidPackageFileStore.fileName("Пятничный стол"))
        assertEquals("Дача _ баня_ «финал».feltbook", AndroidPackageFileStore.fileName("Дача / баня: «финал»"))
        assertEquals("feltbook.feltbook", AndroidPackageFileStore.fileName("  ...  "))
    }

    @Test
    fun `открытие файла кладёт проверенный пакет в хранилище — оба расширения`() = runTest {
        val store = IncomingPackageStore()
        val handler = HandoverImportHandler(context, store, Dispatchers.Unconfined)
        val text = TestGames(Random(3)).handoverPackageText()
        listOf("Пятничный стол.feltbook", "старое.pokerbank").forEach { name ->
            val file = File(context.cacheDir, name).apply { writeText(text) }
            val incoming = handler.open(Uri.fromFile(file))
            assertTrue(incoming.result is DecodeResult.Ok)
            assertEquals(FeltbookCodec.KIND_HANDOVER, incoming.kind)
            assertEquals(name, incoming.fileName)
            assertEquals(incoming, store.pending.value)
        }
    }

    @Test
    fun `чужой или отсутствующий файл — NotFeltbook, повреждённый — ChecksumMismatch`() = runTest {
        val store = IncomingPackageStore()
        val handler = HandoverImportHandler(context, store, Dispatchers.Unconfined)
        val photo = File(context.cacheDir, "photo.jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, -32)) }
        assertEquals(DecodeResult.NotFeltbook, handler.open(Uri.fromFile(photo)).result)
        assertEquals(DecodeResult.NotFeltbook, handler.open(Uri.fromFile(File(context.cacheDir, "нет"))).result)

        val text = TestGames(Random(4)).handoverPackageText()
        // payload — JSON-строка внутри конверта: кавычки ключей экранированы.
        val broken = File(context.cacheDir, "broken.feltbook")
            .apply { writeText(text.replaceFirst("\\\"amount\\\":", "\\\"amount\\\":9")) }
        assertEquals(DecodeResult.ChecksumMismatch, handler.open(Uri.fromFile(broken)).result)
    }

    @Test
    fun `больше 32 МБ не читается целиком`() {
        val big = ByteArray(32 * 1024 * 1024 + 10) { 'a'.code.toByte() }
        assertNull(HandoverImportHandler.readLimited(ByteArrayInputStream(big)))
        assertEquals("ok", HandoverImportHandler.readLimited(ByteArrayInputStream("ok".toByteArray())))
    }

    @Test
    fun `uri берётся из ACTION_VIEW и ACTION_SEND, остальные интенты — null`() {
        val uri = Uri.parse("content://telegram/file/1")
        assertEquals(uri, HandoverImportHandler.uriFrom(Intent(Intent.ACTION_VIEW, uri)))
        assertEquals(uri, HandoverImportHandler.uriFrom(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uri)))
        assertNull(HandoverImportHandler.uriFrom(Intent(Intent.ACTION_MAIN)))
        assertNull(HandoverImportHandler.uriFrom(null))
    }
}
