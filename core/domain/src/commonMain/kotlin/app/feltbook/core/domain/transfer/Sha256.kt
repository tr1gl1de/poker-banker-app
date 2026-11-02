package app.feltbook.core.domain.transfer

/**
 * SHA-256 на чистом Kotlin (FIPS 180-4), без java.security (ADR 0001). Тесты — векторы NIST: "", "abc", 448-битный,
 * 1 000 000 × 'a'.
 */
@Suppress("MagicNumber") // Константы и сдвиги стандарта FIPS 180-4.
object Sha256 {
    private const val BLOCK_BYTES = 64
    private const val ROUNDS = 64

    private val K: IntArray = longArrayOf(
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    ).toIntArray()

    private val H0: IntArray = longArrayOf(
        0x6a09e667,
        0xbb67ae85,
        0x3c6ef372,
        0xa54ff53a,
        0x510e527f,
        0x9b05688c,
        0x1f83d9ab,
        0x5be0cd19,
    ).toIntArray()

    /** 32 байта хеша. */
    fun digest(input: ByteArray): ByteArray {
        val state = H0.copyOf()
        val padded = pad(input)
        val w = IntArray(ROUNDS)
        for (offset in padded.indices step BLOCK_BYTES) compress(state, padded, offset, w)
        return ByteArray(32) { i -> (state[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }

    /** Хеш `input` в hex (lowercase, 64 символа) — поле `sha256` конверта. */
    fun hex(input: ByteArray): String =
        digest(input).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun pad(input: ByteArray): ByteArray {
        val length = ((input.size + 9 + BLOCK_BYTES - 1) / BLOCK_BYTES) * BLOCK_BYTES
        val padded = input.copyOf(length)
        padded[input.size] = 0x80.toByte()
        val bits = input.size.toLong() * 8
        for (i in 0 until 8) padded[length - 1 - i] = (bits ushr (8 * i)).toByte()
        return padded
    }

    private fun compress(state: IntArray, block: ByteArray, offset: Int, w: IntArray) {
        for (t in 0 until 16) {
            val i = offset + 4 * t
            w[t] = (block[i].toInt() and 0xff shl 24) or (block[i + 1].toInt() and 0xff shl 16) or
                (block[i + 2].toInt() and 0xff shl 8) or (block[i + 3].toInt() and 0xff)
        }
        for (t in 16 until ROUNDS) {
            val s0 = w[t - 15].rotateRight(7) xor w[t - 15].rotateRight(18) xor (w[t - 15] ushr 3)
            val s1 = w[t - 2].rotateRight(17) xor w[t - 2].rotateRight(19) xor (w[t - 2] ushr 10)
            w[t] = w[t - 16] + s0 + w[t - 7] + s1
        }
        val v = state.copyOf()
        for (t in 0 until ROUNDS) {
            val e = v[4]
            val a = v[0]
            val t1 = v[7] + (e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)) +
                ((e and v[5]) xor (e.inv() and v[6])) + K[t] + w[t]
            val t2 = (a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)) +
                ((a and v[1]) xor (a and v[2]) xor (v[1] and v[2]))
            for (i in 7 downTo 1) v[i] = v[i - 1]
            v[4] += t1
            v[0] = t1 + t2
        }
        for (i in 0 until 8) state[i] += v[i]
    }

    private fun LongArray.toIntArray(): IntArray = IntArray(size) { this[it].toInt() }
}
