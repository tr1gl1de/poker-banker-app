package app.feltbook.core.domain.transfer

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {
    private fun hex(text: String) = Sha256.hex(text.encodeToByteArray())

    @Test
    fun `NIST — пустая строка`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(""))
    }

    @Test
    fun `NIST — abc`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex("abc"))
        assertEquals(32, Sha256.digest("abc".encodeToByteArray()).size)
    }

    @Test
    fun `NIST — 448-битное сообщение`() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun `NIST — 896-битное сообщение`() {
        assertEquals(
            "cf5b16a778af8380036ce59e7b0492370b249b11e8f07a51afac45037afee9d1",
            hex(
                "abcdefghbcdefghicdefghijdefghijkefghijklfghijklmghijklmn" +
                    "hijklmnoijklmnopjklmnopqklmnopqrlmnopqrsmnopqrstnopqrstu",
            ),
        )
    }

    @Test
    fun `NIST — миллион букв a`() {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            Sha256.hex(ByteArray(1_000_000) { 'a'.code.toByte() }),
        )
    }

    @Test
    fun `границы блока — 55, 56 и 64 байта, кириллица в UTF-8`() {
        assertEquals("9f4390f8d30c2dd92ec9f095b65e2b9ae9b0a925a5258e241c9f1e910f734318", hex("a".repeat(55)))
        assertEquals("b35439a4ac6f0948b6d6f9e3c6af0f5f590ce20f1bde7090ef7970686ec6738a", hex("a".repeat(56)))
        assertEquals("ffe054fe7ae0cb6dc65c3af9b61d5209f439851db43d0ba5997337df154668eb", hex("a".repeat(64)))
        assertEquals(64, hex("Влад должен 1 053 ₽").length)
    }
}
