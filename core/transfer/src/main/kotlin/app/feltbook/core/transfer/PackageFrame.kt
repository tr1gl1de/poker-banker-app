package app.feltbook.core.transfer

import app.feltbook.core.domain.transfer.Sha256
import java.io.InputStream

/**
 * Кадр пакета для Nearby STREAM (ADR 0007: `Payload.fromBytes` ограничен ~32 КБ, игра бывает больше):
 * `"FBK1"` (4 байта) · длина тела (8 байт, big-endian) · тело (UTF-8 текст `FeltbookCodec`) · SHA-256 тела (32 байта).
 *
 * Кадр проверяет только доставку (обрыв, подмену байтов в пути); формат, версию и хеш содержимого проверяет
 * `FeltbookCodec.decode`. Хеш тела в hex — [Encoded.token]: его же получатель шлёт в ACK, так отправитель
 * понимает, что принят именно его пакет.
 */
object PackageFrame {
    const val PROTOCOL_VERSION: Int = 1
    private val MAGIC = byteArrayOf('F'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte(), '1'.code.toByte())
    private const val LENGTH_BYTES = 8
    const val HEADER_SIZE: Int = 4 + LENGTH_BYTES
    const val DIGEST_SIZE: Int = 32

    /** Больше не бывает: игра за вечер — десятки КБ, резервная копия за годы — единицы МБ. */
    const val MAX_BODY_BYTES: Int = 32 * 1024 * 1024
    private const val READ_BUFFER = 8 * 1024
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    class Encoded(val bytes: ByteArray, val token: String)

    class Decoded(val text: String, val token: String)

    enum class Problem { BAD_MAGIC, TOO_LARGE, TRUNCATED, TRAILING_BYTES, CHECKSUM, NOT_UTF8 }

    class FrameException(val problem: Problem) : Exception(problem.name)

    fun encode(text: String): Encoded {
        val body = text.encodeToByteArray()
        require(body.size <= MAX_BODY_BYTES) { "package is ${body.size} bytes, max $MAX_BODY_BYTES" }
        val digest = Sha256.digest(body)
        val out = ByteArray(HEADER_SIZE + body.size + DIGEST_SIZE)
        MAGIC.copyInto(out, 0)
        val length = body.size.toLong()
        for (i in 0 until LENGTH_BYTES) {
            out[MAGIC.size + i] = (length ushr (BITS_PER_BYTE * (LENGTH_BYTES - 1 - i))).toByte()
        }
        body.copyInto(out, HEADER_SIZE)
        digest.copyInto(out, HEADER_SIZE + body.size)
        return Encoded(out, digest.toHex())
    }

    /** Размер кадра для тела из [bodyBytes] байт (прогресс отправителя). */
    fun frameSize(bodyBytes: Int): Long = HEADER_SIZE.toLong() + bodyBytes + DIGEST_SIZE

    /**
     * Читает кадр из потока до конца (EOF). [onProgress] (прочитано, всего кадра или -1) — после каждого куска.
     * Бросает [FrameException].
     */
    fun read(input: InputStream, onProgress: (Long, Long) -> Unit = { _, _ -> }): Decoded {
        val reader = Reader()
        val buffer = ByteArray(READ_BUFFER)
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            if (n > 0) {
                reader.feed(buffer, 0, n)
                onProgress(reader.bytesRead, reader.frameSize ?: -1L)
            }
        }
        return reader.finish()
    }

    /** Пошаговая сборка кадра из кусков любого размера. */
    class Reader {
        private val header = ByteArray(HEADER_SIZE)
        private var body: ByteArray? = null
        private val digest = ByteArray(DIGEST_SIZE)
        private var headerFilled = 0
        private var bodyFilled = 0
        private var digestFilled = 0

        var bytesRead: Long = 0L
            private set

        /** Размер всего кадра, когда заголовок прочитан. */
        var frameSize: Long? = null
            private set

        val isComplete: Boolean get() = body != null && bodyFilled == body!!.size && digestFilled == DIGEST_SIZE

        fun feed(buffer: ByteArray, offset: Int, length: Int) {
            var pos = offset
            val end = offset + length
            while (pos < end) {
                pos += when {
                    headerFilled < HEADER_SIZE -> fillHeader(buffer, pos, end)
                    bodyFilled < body!!.size -> fillBody(buffer, pos, end)
                    digestFilled < DIGEST_SIZE -> fillDigest(buffer, pos, end)
                    else -> throw FrameException(Problem.TRAILING_BYTES)
                }
            }
            bytesRead += length
        }

        @Suppress("ThrowsCount") // По исключению на каждую причину отказа кадра.
        fun finish(): Decoded {
            if (!isComplete) throw FrameException(Problem.TRUNCATED)
            val bytes = body!!
            val actual = Sha256.digest(bytes)
            if (!actual.contentEquals(digest)) throw FrameException(Problem.CHECKSUM)
            val text = try {
                bytes.decodeToString(throwOnInvalidSequence = true)
            } catch (_: CharacterCodingException) {
                throw FrameException(Problem.NOT_UTF8)
            }
            return Decoded(text, actual.toHex())
        }

        private fun fillHeader(buffer: ByteArray, pos: Int, end: Int): Int {
            val n = minOf(HEADER_SIZE - headerFilled, end - pos)
            buffer.copyInto(header, headerFilled, pos, pos + n)
            headerFilled += n
            if (headerFilled == HEADER_SIZE) onHeader()
            return n
        }

        private fun onHeader() {
            for (i in MAGIC.indices) if (header[i] != MAGIC[i]) throw FrameException(Problem.BAD_MAGIC)
            var length = 0L
            for (i in 0 until LENGTH_BYTES) {
                length = (length shl BITS_PER_BYTE) or (header[MAGIC.size + i].toLong() and BYTE_MASK.toLong())
            }
            if (length < 0 || length > MAX_BODY_BYTES) throw FrameException(Problem.TOO_LARGE)
            body = ByteArray(length.toInt())
            frameSize = frameSize(length.toInt())
        }

        private fun fillBody(buffer: ByteArray, pos: Int, end: Int): Int {
            val target = body!!
            val n = minOf(target.size - bodyFilled, end - pos)
            buffer.copyInto(target, bodyFilled, pos, pos + n)
            bodyFilled += n
            return n
        }

        private fun fillDigest(buffer: ByteArray, pos: Int, end: Int): Int {
            val n = minOf(DIGEST_SIZE - digestFilled, end - pos)
            buffer.copyInto(digest, digestFilled, pos, pos + n)
            digestFilled += n
            return n
        }
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it.toInt() and BYTE_MASK) }
}
