package app.feltbook.core.data.handover

import android.content.Context
import app.feltbook.core.data.di.IoDispatcher
import app.feltbook.core.domain.model.GameChange
import app.feltbook.core.domain.model.GameId
import app.feltbook.core.domain.model.GameSnapshot
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.transfer.DecodeResult
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.domain.transfer.FeltbookPackage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Неподтверждённая передача у отправителя: пакет ушёл (по воздуху или файлом), а ACK / «Файл отправлен» / «Да, принял»
 * ещё не было. Хранится на диске, чтобы после закрытия экрана или смерти процесса стол спросил «Новый банкир <имя>
 * принял игру?» и записал **ту же** передачу (те же id), а не оставил игру активной на двух телефонах.
 *
 * @property packageText отправленный пакет `FeltbookCodec` (HANDOVER): из него восстанавливается `senderChange`.
 * @property baseSeq `lastSeq` игры, когда передача подготовлена: журнал ушёл дальше — передачу записать нельзя.
 */
@Suppress("ReturnCount") // Каждая проверка — причина не записывать.
@Serializable
data class PendingHandover(
    val gameId: String,
    val handoverId: String,
    val toBanker: String,
    val cashHandedOver: Long,
    val incomeTaken: Long,
    val baseSeq: Long,
    val packageText: String,
) {
    /**
     * Изменение отправителя по этой передаче: INCOME_TAKEN? + акт + HANDED_OVER (как `PreparedHandover.senderChange`).
     * null — пакет не читается, игра другая или её журнал ушёл вперёд (записывать нельзя).
     */
    fun senderChange(local: GameSnapshot): GameChange? {
        if (local.game.id.value.toString() != gameId || local.lastSeq != baseSeq || !local.game.status.isActive) {
            return null
        }
        val pkg = (FeltbookCodec.decode(packageText) as? DecodeResult.Ok)?.pkg as? FeltbookPackage.HandoverPackage
            ?: return null
        if (pkg.handover.id.value.toString() != handoverId) return null
        val outgoing = pkg.game
        val prefix = outgoing.entries.size >= local.entries.size &&
            outgoing.entries.subList(0, local.entries.size) == local.entries
        if (!prefix) return null
        return GameChange(
            gameId = local.game.id,
            baseSeq = local.lastSeq,
            game = local.game.copy(status = GameStatus.HANDED_OVER),
            entries = outgoing.entries.drop(local.entries.size),
            handovers = listOf(pkg.handover),
        )
    }
}

/**
 * Файловое хранилище [PendingHandover] (одна запись на игру). Без Room: пишется редко, читается столом.
 * Конструктор с файлом — для тестов; приложение получает экземпляр через Hilt.
 */
@Singleton
class PendingHandoverStore(private val file: File, private val io: CoroutineDispatcher) {
    @Inject
    constructor(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher) :
        this(File(File(context.filesDir, DIR), FILE), io)

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<List<PendingHandover>?>(null)

    /** null — ещё не прочитано с диска (см. [load]). */
    val pending: StateFlow<List<PendingHandover>?> = mutable.asStateFlow()

    suspend fun load(): List<PendingHandover> = mutex.withLock { current() }

    suspend fun get(gameId: GameId): PendingHandover? = load().firstOrNull { it.gameId == gameId.value.toString() }

    suspend fun save(handover: PendingHandover) =
        update { list -> list.filterNot { it.gameId == handover.gameId } + handover }

    suspend fun remove(gameId: GameId) = update { list -> list.filterNot { it.gameId == gameId.value.toString() } }

    private suspend fun update(transform: (List<PendingHandover>) -> List<PendingHandover>) {
        mutex.withLock {
            val next = transform(current())
            withContext(io) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$FILE.tmp")
                tmp.writeText(json.encodeToString(ListSerializer(PendingHandover.serializer()), next))
                if (!tmp.renameTo(file)) {
                    file.delete()
                    tmp.renameTo(file)
                }
            }
            mutable.value = next
        }
    }

    private suspend fun current(): List<PendingHandover> = mutable.value ?: withContext(io) {
        try {
            if (file.exists()) {
                json.decodeFromString(ListSerializer(PendingHandover.serializer()), file.readText())
            } else {
                emptyList()
            }
        } catch (_: IOException) {
            emptyList()
        } catch (_: SerializationException) {
            emptyList()
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }.also { mutable.value = it }

    private companion object {
        const val DIR = "handover"
        const val FILE = "pending.json"
    }
}
