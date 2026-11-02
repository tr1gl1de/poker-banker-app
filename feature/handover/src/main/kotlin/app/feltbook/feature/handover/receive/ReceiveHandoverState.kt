package app.feltbook.feature.handover.receive

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.model.GameRules

enum class ReceiveFailure {
    NO_GMS,
    PERMISSION_DENIED,

    /** Отказано навсегда — только через настройки приложения. */
    PERMISSION_BLOCKED,

    /** Android 12: дана приблизительная геолокация, а нужна точная. */
    APPROXIMATE_LOCATION,
    LOCATION_OFF,

    /** Nearby не соединился или оборвался до получения пакета. */
    CONNECTION,

    /** Кадр или хеш пакета не сошлись. */
    CORRUPTED,
    NOT_FELTBOOK,

    /** `schemaVersion` новее — «обновите приложение». */
    NEWER_VERSION,

    /** Открыта резервная копия, а не передача. */
    BACKUP,
}

/** Почему «Принять» не прошло (m3-ui-spec §5.10, §11.12). Пакет остаётся на экране. */
enum class AcceptError { ANOTHER_GAME_ACTIVE, CONFLICT, INVALID, STORAGE }

sealed interface ReceivePhase {
    data object Loading : ReceivePhase

    /** `lastBankerName` пуст: имя нужно до рекламы — его увидит отправитель и запишет в акт. */
    data class NeedsName(val name: String, val error: Boolean = false) : ReceivePhase

    data object NeedsPermission : ReceivePhase

    /** «Ждём телефон банкира…» — реклама под [localName]. */
    data class Waiting(val localName: String) : ReceivePhase

    data class Code(val peerName: String, val digits: String, val waitingPeer: Boolean) : ReceivePhase

    data class Receiving(val peerName: String, val fraction: Float?) : ReceivePhase

    /** Сводка и «Принять». */
    data object Review : ReceivePhase

    data object Done : ReceivePhase

    data class Failed(val reason: ReceiveFailure) : ReceivePhase
}

data class PlayerOption(val id: String, val name: String)

/**
 * Сводка входящего пакета: «Игра «Пятничный стол» от Андрея» · игроков за столом · фишек в игре · касса
 * (сколько наличных отдал старый банкир). Для шаблона — правила и число имён.
 */
@Immutable
data class IncomingSummary(
    val isTemplate: Boolean,
    val title: String,
    val fromBanker: String,
    val playersAtTable: Int = 0,
    val chipsInPlay: Long = 0L,
    val cash: Long = 0L,
    val players: List<PlayerOption> = emptyList(),
    val rules: GameRules? = null,
    val templateNames: Int = 0,
    val fileName: String? = null,
)

@Immutable
data class ReceiveUiState(
    val phase: ReceivePhase = ReceivePhase.Loading,
    val fromFile: Boolean = false,
    val summary: IncomingSummary? = null,
    /** «Я играю как…» — id игрока или null («Не играю», по умолчанию). */
    val playAs: String? = null,
    val error: AcceptError? = null,
    val busy: Boolean = false,
    /** На сводке связь с отправителем оборвалась: принять можно, но ACK не дойдёт — предупреждение. */
    val senderDisconnected: Boolean = false,
) {
    /** Вкладку «Отдать игру» не включить, пока идёт приём. */
    val modeLocked: Boolean
        get() = busy || fromFile || phase is ReceivePhase.Receiving || phase == ReceivePhase.Review ||
            phase == ReceivePhase.Done || (phase is ReceivePhase.Code && phase.waitingPeer)
}

sealed interface ReceiveAction {
    data object Activate : ReceiveAction

    data object Deactivate : ReceiveAction

    /** Экран снова на переднем плане (вернулись из настроек). */
    data object Resumed : ReceiveAction

    data object OpenSettings : ReceiveAction

    data class NameChanged(val name: String) : ReceiveAction

    data object NameConfirmed : ReceiveAction

    data object AllowPermissions : ReceiveAction

    data class PermissionsResult(val allGranted: Boolean, val permanentlyDenied: Boolean = false) : ReceiveAction

    data object Retry : ReceiveAction

    data object ConfirmCode : ReceiveAction

    data object RejectCode : ReceiveAction

    data class PlayAs(val playerId: String?) : ReceiveAction

    /** Hold «Принять игру» / «Принять шаблон». */
    data object Accept : ReceiveAction

    data object Decline : ReceiveAction
}

sealed interface ReceiveEvent {
    data class RequestPermissions(val permissions: List<String>) : ReceiveEvent

    data object OpenAppSettings : ReceiveEvent

    /** Игра записана — открыть стол. */
    data class GameAccepted(val gameId: String) : ReceiveEvent

    /** Шаблон принят — открыть «Новую игру». */
    data object TemplateAccepted : ReceiveEvent

    /** «Не принимать» файл — закрыть экран. */
    data object Closed : ReceiveEvent
}
