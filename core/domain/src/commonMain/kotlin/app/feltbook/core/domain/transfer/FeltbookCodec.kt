package app.feltbook.core.domain.transfer

import app.feltbook.core.domain.engine.GameTemplate
import app.feltbook.core.domain.engine.LedgerProblem
import app.feltbook.core.domain.engine.LedgerValidator
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.Handover
import app.feltbook.core.domain.model.PlayerName
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

sealed interface FeltbookPackage {
    data class HandoverPackage(val game: GameSnapshot, val handover: Handover) : FeltbookPackage

    data class TemplatePackage(val template: GameTemplate) : FeltbookPackage

    data class BackupPackage(
        val exportedAt: Instant,
        val games: List<GameSnapshot>,
        val playerNames: List<PlayerName>,
    ) : FeltbookPackage
}

sealed interface DecodeResult {
    data class Ok(val pkg: FeltbookPackage, val schemaVersion: Int) : DecodeResult

    sealed interface Failure : DecodeResult

    /** Не JSON или `format != "feltbook"` — «Это не файл Feltbook». */
    data object NotFeltbook : Failure

    /** `schemaVersion > SCHEMA_VERSION` — «Файл из более новой версии, обновите приложение». */
    data class UnsupportedVersion(val found: Int, val supported: Int) : Failure

    /** Хеш не совпал — «Файл повреждён». */
    data object ChecksumMismatch : Failure

    /** Структура/значения не разбираются (неизвестный enum, битый UUID). */
    data class Malformed(val detail: String) : Failure

    data class InvalidLedger(val gameId: String, val problems: List<LedgerProblem>) : Failure
}

object FeltbookCodec {
    const val FORMAT: String = "feltbook"
    const val SCHEMA_VERSION: Int = 1
    const val FILE_EXTENSION: String = "feltbook"

    /** Импорт принимает оба расширения (ADR 0003). */
    val ACCEPTED_EXTENSIONS: Set<String> = setOf("feltbook", "pokerbank")
    const val MIME_TYPE: String = "application/json"

    private const val KIND_HANDOVER = "HANDOVER"
    private const val KIND_TEMPLATE = "TEMPLATE"
    private const val KIND_BACKUP = "BACKUP"

    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = false
    }

    /** `Json { encodeDefaults = true; explicitNulls = true; ignoreUnknownKeys = false }` — без pretty print. */
    fun encode(pkg: FeltbookPackage, createdAt: Instant): String {
        val (kind, payload) = when (pkg) {
            is FeltbookPackage.HandoverPackage -> KIND_HANDOVER to json.encodeToString(
                HandoverPayloadDto.serializer(),
                HandoverPayloadDto(pkg.game.toDto(), pkg.handover.id.value.toString()),
            )

            is FeltbookPackage.TemplatePackage -> KIND_TEMPLATE to json.encodeToString(
                TemplatePayloadDto.serializer(),
                TemplatePayloadDto(pkg.template.fromBanker, pkg.template.rules.toDto(), pkg.template.playerNames),
            )

            is FeltbookPackage.BackupPackage -> KIND_BACKUP to json.encodeToString(
                BackupPayloadDto.serializer(),
                BackupPayloadDto(
                    exportedAt = pkg.exportedAt.toEpochMilliseconds(),
                    games = pkg.games.map { it.toDto() },
                    playerNames = pkg.playerNames.map { it.toDto() },
                ),
            )
        }
        val envelope = EnvelopeDto(
            format = FORMAT,
            schemaVersion = SCHEMA_VERSION,
            kind = kind,
            createdAt = createdAt.toEpochMilliseconds(),
            sha256 = Sha256.hex(payload.encodeToByteArray()),
            payload = payload,
        )
        return json.encodeToString(EnvelopeDto.serializer(), envelope)
    }

    /** Проверяет формат → версию → хеш → структуру → `LedgerValidator` каждой игры (в этом порядке). */
    fun decode(text: String): DecodeResult = try {
        val root = parseRoot(text)
        val version = checkVersion(root)
        val envelope = json.decodeFromJsonElement(EnvelopeDto.serializer(), root)
        if (Sha256.hex(envelope.payload.encodeToByteArray()) != envelope.sha256) fail(DecodeResult.ChecksumMismatch)
        val pkg = parsePayload(envelope)
        validate(pkg)
        DecodeResult.Ok(pkg, version)
    } catch (failure: DecodeFailure) {
        failure.result
    } catch (error: IllegalArgumentException) {
        // SerializationException — тоже IllegalArgumentException: битая структура, UUID, неизвестный enum.
        DecodeResult.Malformed(error.message ?: error.toString())
    }

    private fun parseRoot(text: String): JsonObject {
        val root = try {
            json.parseToJsonElement(text)
        } catch (notJson: SerializationException) {
            fail(DecodeResult.NotFeltbook, notJson)
        }
        val format = ((root as? JsonObject)?.get("format") as? JsonPrimitive)?.contentOrNull
        if (format != FORMAT) fail(DecodeResult.NotFeltbook)
        return root as JsonObject
    }

    private fun checkVersion(root: JsonObject): Int {
        val version = (root["schemaVersion"] as? JsonPrimitive)?.intOrNull
            ?: fail(DecodeResult.Malformed("schemaVersion is missing"))
        if (version > SCHEMA_VERSION) fail(DecodeResult.UnsupportedVersion(version, SCHEMA_VERSION))
        if (version < 1) fail(DecodeResult.Malformed("schemaVersion $version"))
        return version
    }

    private fun parsePayload(envelope: EnvelopeDto): FeltbookPackage = when (envelope.kind) {
        KIND_HANDOVER -> {
            val dto = json.decodeFromString(HandoverPayloadDto.serializer(), envelope.payload)
            val game = dto.game.toDomain()
            val handoverId = uuid(dto.handoverId)
            val handover = game.handovers.firstOrNull { it.id.value == handoverId }
                ?: fail(DecodeResult.Malformed("handover ${dto.handoverId} is not in the game"))
            FeltbookPackage.HandoverPackage(game, handover)
        }

        KIND_TEMPLATE -> {
            val dto = json.decodeFromString(TemplatePayloadDto.serializer(), envelope.payload)
            FeltbookPackage.TemplatePackage(GameTemplate(dto.fromBanker, dto.rules.toDomain(), dto.playerNames))
        }

        KIND_BACKUP -> {
            val dto = json.decodeFromString(BackupPayloadDto.serializer(), envelope.payload)
            FeltbookPackage.BackupPackage(
                exportedAt = instant(dto.exportedAt),
                games = dto.games.map { it.toDomain() },
                playerNames = dto.playerNames.map { it.toDomain() },
            )
        }

        else -> fail(DecodeResult.Malformed("unknown kind '${envelope.kind}'"))
    }

    private fun validate(pkg: FeltbookPackage) {
        val games = when (pkg) {
            is FeltbookPackage.HandoverPackage -> listOf(pkg.game)
            is FeltbookPackage.TemplatePackage -> emptyList()
            is FeltbookPackage.BackupPackage -> pkg.games
        }
        games.forEach { game ->
            val problems = LedgerValidator.validate(game)
            if (problems.isNotEmpty()) fail(DecodeResult.InvalidLedger(game.game.id.value.toString(), problems))
        }
    }

    private class DecodeFailure(val result: DecodeResult.Failure, cause: Throwable?) :
        Exception(result.toString(), cause)

    private fun fail(result: DecodeResult.Failure, cause: Throwable? = null): Nothing =
        throw DecodeFailure(result, cause)
}
