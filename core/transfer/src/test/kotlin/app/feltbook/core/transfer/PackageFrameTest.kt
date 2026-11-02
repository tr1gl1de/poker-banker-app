package app.feltbook.core.transfer

import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Кадр Nearby-потока: туда-обратно при любой нарезке на куски, отказ при порче/обрыве. */
class PackageFrameTest {
    @Test
    fun `пакет передачи туда-обратно без потерь при случайной нарезке потока`() {
        repeat(ITERATIONS) { seed ->
            val random = Random(seed)
            val games = TestGames(random)
            val prepared = games.prepared()
            val text = games.handoverPackageText(prepared)
            val encoded = PackageFrame.encode(text)

            val decoded = PackageFrame.read(ChunkedInputStream(encoded.bytes, random))

            assertEquals("seed $seed", text, decoded.text)
            assertEquals("seed $seed", encoded.token, decoded.token)
            val result = FeltbookCodec.decode(decoded.text) as DecodeResult.Ok
            val expected = FeltbookPackage.HandoverPackage(prepared.outgoing, prepared.handover)
            assertEquals("seed $seed", expected, result.pkg)
        }
    }

    @Test
    fun `любой текст — в том числе пустой, многобайтовый и больше 32 КБ — проходит пошаговую сборку`() {
        val random = Random(42)
        val samples = listOf("", "♦", "Лёха 🃏 «тест»", "x".repeat(40_000), randomText(random, 70_000))
        samples.forEach { text ->
            val encoded = PackageFrame.encode(text)
            val reader = PackageFrame.Reader()
            var pos = 0
            while (pos < encoded.bytes.size) {
                val n = minOf(random.nextInt(1, 5_000), encoded.bytes.size - pos)
                reader.feed(encoded.bytes, pos, n)
                pos += n
            }
            assertEquals(text, reader.finish().text)
            assertEquals(PackageFrame.frameSize(text.encodeToByteArray().size), reader.frameSize)
        }
    }

    @Test
    fun `испорченный байт тела или хеша — CHECKSUM, заголовка — BAD_MAGIC или TOO_LARGE`() {
        val random = Random(7)
        val text = TestGames(random).handoverPackageText()
        val encoded = PackageFrame.encode(text).bytes
        repeat(ITERATIONS) {
            val index = random.nextInt(encoded.size)
            val copy = encoded.copyOf()
            copy[index] = (copy[index].toInt() xor (1 shl random.nextInt(8))).toByte()
            val problem = problemOf { PackageFrame.read(ByteArrayInputStream(copy)) }
            val expected = when {
                index < 4 -> setOf(PackageFrame.Problem.BAD_MAGIC)

                index < PackageFrame.HEADER_SIZE -> setOf(
                    PackageFrame.Problem.TOO_LARGE,
                    PackageFrame.Problem.TRUNCATED,
                    PackageFrame.Problem.TRAILING_BYTES,
                )

                else -> setOf(PackageFrame.Problem.CHECKSUM, PackageFrame.Problem.NOT_UTF8)
            }
            assertTrue("byte $index → $problem", problem in expected)
        }
    }

    @Test
    fun `обрыв потока — TRUNCATED, лишние байты — TRAILING_BYTES`() {
        val encoded = PackageFrame.encode("Пятничный стол").bytes
        listOf(0, 3, PackageFrame.HEADER_SIZE, encoded.size - 1).forEach { cut ->
            val problem = problemOf { PackageFrame.read(ByteArrayInputStream(encoded.copyOf(cut))) }
            assertEquals("cut $cut", PackageFrame.Problem.TRUNCATED, problem)
        }
        val trailing = problemOf { PackageFrame.read(ByteArrayInputStream(encoded + byteArrayOf(0))) }
        assertEquals(PackageFrame.Problem.TRAILING_BYTES, trailing)
    }

    @Test
    fun `прогресс чтения растёт до размера кадра`() {
        val encoded = PackageFrame.encode("y".repeat(50_000)).bytes
        val seen = mutableListOf<Pair<Long, Long>>()
        PackageFrame.read(ChunkedInputStream(encoded, Random(1))) { bytes, total -> seen += bytes to total }
        assertEquals(encoded.size.toLong(), seen.last().first)
        assertEquals(encoded.size.toLong(), seen.last().second)
        assertEquals(seen.map { it.first }.sorted(), seen.map { it.first })
    }

    private fun problemOf(block: () -> Unit): PackageFrame.Problem? = try {
        block()
        null
    } catch (error: PackageFrame.FrameException) {
        error.problem
    }.also { if (it == null) fail("frame accepted") }

    private fun randomText(random: Random, length: Int): String = buildString {
        val alphabet = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя ABC xyz 0123456789 ₽♦♠♥♣«»—\"\\\n\t"
        // Без суррогатных пар: случайный индекс разорвал бы эмодзи пополам.
        repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    private companion object {
        const val ITERATIONS = 100
    }
}

/** Отдаёт байты кусками случайного размера (1 байт … 9 КБ), как сеть. */
class ChunkedInputStream(private val bytes: ByteArray, private val random: Random) : InputStream() {
    private var pos = 0

    override fun read(): Int = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (pos >= bytes.size) return -1
        val n = minOf(len, bytes.size - pos, random.nextInt(1, 9_000))
        bytes.copyInto(b, off, pos, pos + n)
        pos += n
        return n
    }
}
