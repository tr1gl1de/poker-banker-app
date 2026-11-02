package app.feltbook.core.domain.transfer

import app.feltbook.core.domain.T0
import app.feltbook.core.domain.engine.CloseConfirmation
import app.feltbook.core.domain.engine.Commands
import app.feltbook.core.domain.engine.GameTemplate
import app.feltbook.core.domain.engine.Handovers
import app.feltbook.core.domain.engine.LedgerProblem
import app.feltbook.core.domain.engine.LedgerProblemCode
import app.feltbook.core.domain.engine.example81
import app.feltbook.core.domain.engine.example84Start
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.PlayerName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class FeltbookCodecTest {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    private fun handoverPackage(): FeltbookPackage.HandoverPackage {
        val table = example84Start()
        table.later()
        table.cashOut("Егор", 1_500)
        val prepared = Handovers.prepare(table.snapshot, "Влад", keepIncome = true, stamp = table.clock.stamp())
        return FeltbookPackage.HandoverPackage(prepared.outgoing, prepared.handover)
    }

    private fun backupPackage(): FeltbookPackage.BackupPackage {
        val closed = example81(vladPays = false).apply {
            apply(Commands.finish(snapshot))
            apply(Commands.close(snapshot, true, setOf(CloseConfirmation.OPEN_DEBTS), clock.stamp()))
            reverse(lastOp("Андрей", app.feltbook.core.domain.model.EntryType.PAYMENT), "проверка")
        }.snapshot
        val names = listOf(PlayerName("Влад", T0), PlayerName("Владик", T0 + 1.days, mergedInto = "Влад"))
        return FeltbookPackage.BackupPackage(T0 + 2.days, listOf(closed, handoverPackage().game), names)
    }

    private fun envelope(
        kind: String,
        payload: String,
        version: Int = 1,
        sha: String = Sha256.hex(payload.encodeToByteArray()),
    ) = json.encodeToString(EnvelopeDto.serializer(), EnvelopeDto("feltbook", version, kind, 0, sha, payload))

    private fun payloadOf(text: String): String = json.decodeFromString(EnvelopeDto.serializer(), text).payload

    @Test
    fun `вид пакета — значение поля kind`() {
        assertEquals(FeltbookCodec.KIND_HANDOVER, FeltbookCodec.kindOf(handoverPackage()))
        assertEquals(FeltbookCodec.KIND_BACKUP, FeltbookCodec.kindOf(backupPackage()))
        val template = FeltbookPackage.TemplatePackage(GameTemplate("Олег", GameRules.DEFAULT, listOf("Влад")))
        assertEquals("TEMPLATE", FeltbookCodec.kindOf(template))
    }

    @Test
    fun `передача — encode, decode дают равный снимок и акт`() {
        val pkg = handoverPackage()
        val text = FeltbookCodec.encode(pkg, T0)
        val ok = assertIs<DecodeResult.Ok>(FeltbookCodec.decode(text))
        assertEquals(pkg, ok.pkg)
        assertEquals(FeltbookCodec.SCHEMA_VERSION, ok.schemaVersion)
        val envelope = json.decodeFromString(EnvelopeDto.serializer(), text)
        assertEquals("HANDOVER", envelope.kind)
        assertEquals(T0.toEpochMilliseconds(), envelope.createdAt)
        assertEquals(Sha256.hex(envelope.payload.encodeToByteArray()), envelope.sha256)
        assertTrue(!text.contains("\n"), "без pretty print")
    }

    @Test
    fun `резервная копия и шаблон — encode, decode равны`() {
        val backup = backupPackage()
        assertEquals(backup, (FeltbookCodec.decode(FeltbookCodec.encode(backup, T0)) as DecodeResult.Ok).pkg)
        val template = FeltbookPackage.TemplatePackage(
            GameTemplate("Олег", GameRules(rakeValue = 5), listOf("Андрей", "Борис")),
        )
        assertEquals(template, (FeltbookCodec.decode(FeltbookCodec.encode(template, T0)) as DecodeResult.Ok).pkg)
    }

    @Test
    fun `изменённый байт payload — файл повреждён`() {
        val text = FeltbookCodec.encode(handoverPackage(), T0)
        val tampered = text.replace("\\\"amount\\\":1000", "\\\"amount\\\":9000")
        assertTrue(tampered != text)
        assertEquals(DecodeResult.ChecksumMismatch, FeltbookCodec.decode(tampered))
    }

    @Test
    fun `версия 2 — обновите приложение, версия 0 и без версии — повреждён`() {
        val payload = payloadOf(FeltbookCodec.encode(handoverPackage(), T0))
        assertEquals(
            DecodeResult.UnsupportedVersion(2, 1),
            FeltbookCodec.decode(envelope("HANDOVER", payload, version = 2)),
        )
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(envelope("HANDOVER", payload, version = 0)))
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode("""{"format":"feltbook"}"""))
    }

    @Test
    fun `не JSON или чужой формат — это не файл Feltbook`() {
        assertEquals(DecodeResult.NotFeltbook, FeltbookCodec.decode("PK\u0003\u0004 zip"))
        assertEquals(DecodeResult.NotFeltbook, FeltbookCodec.decode("[1,2,3]"))
        assertEquals(DecodeResult.NotFeltbook, FeltbookCodec.decode("""{"format":"pokerbank","schemaVersion":1}"""))
        assertEquals(DecodeResult.NotFeltbook, FeltbookCodec.decode("""{"schemaVersion":1}"""))
    }

    @Test
    fun `неизвестный тип записи, битый UUID, неизвестный вид, лишний ключ — Malformed`() {
        val payload = payloadOf(FeltbookCodec.encode(handoverPackage(), T0))
        val unknownType = payload.replace("\"type\":\"RAKE\"", "\"type\":\"BONUS\"")
        assertTrue(unknownType != payload)
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(envelope("HANDOVER", unknownType)))
        val dto = json.decodeFromString(HandoverPayloadDto.serializer(), payload)
        val badUuid = json.encodeToString(
            HandoverPayloadDto.serializer(),
            dto.copy(game = dto.game.copy(id = "not-a-uuid")),
        )
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(envelope("HANDOVER", badUuid)))
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(envelope("SNAPSHOT", payload)))
        val missingAct = json.encodeToString(
            HandoverPayloadDto.serializer(),
            dto.copy(game = dto.game.copy(handovers = emptyList())),
        )
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(envelope("HANDOVER", missingAct)))
        val extra =
            JsonObject(
                json.parseToJsonElement(envelope("HANDOVER", payload)).jsonObject + ("extra" to JsonPrimitive(1)),
            )
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(extra.toString()))
        assertIs<DecodeResult.Malformed>(FeltbookCodec.decode(envelope("HANDOVER", "{\"game\":1}")))
    }

    @Test
    fun `битый журнал — InvalidLedger`() {
        val payload = payloadOf(FeltbookCodec.encode(handoverPackage(), T0))
        val dto = json.decodeFromString(HandoverPayloadDto.serializer(), payload)
        val broken = dto.copy(game = dto.game.copy(entries = dto.game.entries.drop(1)))
        val result = assertIs<DecodeResult.InvalidLedger>(
            FeltbookCodec.decode(envelope("HANDOVER", json.encodeToString(HandoverPayloadDto.serializer(), broken))),
        )
        assertEquals(dto.game.id, result.gameId)
        assertTrue(result.problems.all { it.code == LedgerProblemCode.SEQ_NOT_CONTIGUOUS })
        assertTrue(result.problems.first() is LedgerProblem)
    }

    @Test
    fun `статус CREATED читается как RUNNING`() {
        val payload = payloadOf(FeltbookCodec.encode(handoverPackage(), T0))
        val dto = json.decodeFromString(HandoverPayloadDto.serializer(), payload)
        val created = json.encodeToString(
            HandoverPayloadDto.serializer(),
            dto.copy(game = dto.game.copy(status = "CREATED")),
        )
        val ok = assertIs<DecodeResult.Ok>(FeltbookCodec.decode(envelope("HANDOVER", created)))
        assertEquals(GameStatus.RUNNING, (ok.pkg as FeltbookPackage.HandoverPackage).game.game.status)
    }

    @Test
    fun `расширения и тип файла`() {
        assertEquals("feltbook", FeltbookCodec.FORMAT)
        assertEquals("feltbook", FeltbookCodec.FILE_EXTENSION)
        assertEquals(setOf("feltbook", "pokerbank"), FeltbookCodec.ACCEPTED_EXTENSIONS)
        assertEquals("application/json", FeltbookCodec.MIME_TYPE)
    }
}
