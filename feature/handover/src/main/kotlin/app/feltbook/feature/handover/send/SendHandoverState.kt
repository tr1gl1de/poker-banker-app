package app.feltbook.feature.handover.send

import androidx.compose.runtime.Immutable
import app.feltbook.core.domain.engine.HandoverAct
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.transfer.Endpoint
import app.feltbook.core.transfer.file.SharedFile

/** Что передаём: игру посреди игры (сценарий 1) или шаблон после расчёта (сценарий 2, QUESTIONS №5). */
enum class SendKind { HANDOVER, TEMPLATE }

enum class SendFailure {
    /** Нет Google Play Services — сразу файл. */
    NO_GMS,
    PERMISSION_DENIED,

    /** Отказано навсегда («Больше не спрашивать») — только через настройки приложения. */
    PERMISSION_BLOCKED,

    /** Android 12: дана приблизительная геолокация, а Nearby нужна точная. */
    APPROXIMATE_LOCATION,
    LOCATION_OFF,

    /** Nearby не соединился, оборвался, не ответил. */
    CONNECTION,
    DECLINED,

    /** Имя нового банкира пустое или слишком длинное. */
    BAD_NAME,

    /** Игра изменилась между подготовкой и записью. */
    STALE,
}

sealed interface SendPhase {
    data object Loading : SendPhase

    /** Вкладка не активна или поиск остановлен — ничего не идёт. */
    data object Idle : SendPhase

    /** Нечего передавать: игры нет. */
    data object NoGame : SendPhase

    /** Игра уже передана — только чтение. */
    data object AlreadyHandedOver : SendPhase

    /** «Нужен доступ к Bluetooth и Wi-Fi рядом» + «Разрешить». */
    data object NeedsPermission : SendPhase

    data object Searching : SendPhase

    /** Найдено несколько телефонов — выбрать. */
    data class Choose(val endpoints: List<Endpoint>) : SendPhase

    data class Connecting(val peerName: String) : SendPhase

    /** Код, акт и hold видны (m3-ui-spec §5.10). [waitingPeer] — здесь подтверждено, ждём второй телефон. */
    data class Code(val peerName: String, val digits: String, val waitingPeer: Boolean) : SendPhase

    data class Sending(val peerName: String, val fraction: Float?) : SendPhase

    data class AwaitingAccept(val peerName: String) : SendPhase

    /** [cash] — «Отдайте N ₽ наличными» по записанному акту (null — шаблон). */
    data class Done(val peerName: String, val cash: Long? = null) : SendPhase

    /** [ackLost] — пакет ушёл, ACK нет: «Новый банкир уже принял игру?». */
    data class Failed(val reason: SendFailure, val ackLost: Boolean = false) : SendPhase
}

sealed interface SendDialog {
    /** Файл: «Кому передаёте игру?» — имени конечной точки Nearby нет. */
    data class FileRecipient(val name: String, val error: Boolean = false) : SendDialog

    /** «Файл отправлен?» (QUESTIONS №13). */
    data object FileSent : SendDialog

    /** «Новый банкир принял игру?» — ACK потерян. */
    data object AckLost : SendDialog

    /** «Прервать передачу?» — уход с экрана, пока передача не подтверждена. */
    data object Leave : SendDialog
}

/** Пакет ушёл: акт зафиксирован (доход не переключить), показывается то, что реально отправлено. */
@Immutable
data class SentHandover(val toBanker: String, val cashHandedOver: Long, val incomeTaken: Long, val entries: Int)

@Immutable
data class SendUiState(
    val phase: SendPhase = SendPhase.Loading,
    val kind: SendKind = SendKind.HANDOVER,
    val gameTitle: String = "",
    val act: HandoverAct? = null,
    val keepIncome: Boolean = false,
    val rules: GameRules? = null,
    val templateNames: Int = 0,
    /** Все игроки игры (не только за столом) — «Передаётся: N игроков». */
    val players: Int = 0,
    val sent: SentHandover? = null,
    val dialog: SendDialog? = null,
    val busy: Boolean = false,
) {
    /** После отправки «Оставить доход себе» не меняется: пакет с этим актом мог дойти. */
    val incomeLocked: Boolean get() = sent != null

    /** Переключатель «Оставить доход себе» доступен, только если есть что забрать (m3-ui-spec §5.10). */
    val keepIncomeEnabled: Boolean get() = (act?.incomeTakeable ?: 0L) > 0L

    val keepIncomeShown: Boolean get() = sent?.let { it.incomeTaken > 0 } ?: (keepIncome && keepIncomeEnabled)

    val cashToHandOver: Long
        get() = sent?.cashHandedOver ?: act?.cashToHandOver(keepIncome && keepIncomeEnabled) ?: 0L

    /** Записей в пакете: журнал + INCOME_TAKEN, если доход остаётся у отправителя. */
    val payloadEntries: Int
        get() = sent?.entries ?: ((act?.entryCount ?: 0) + if (keepIncome && keepIncomeEnabled) 1 else 0)

    /** «Отправить файлом» — после любой неудачи и при отказе в разрешениях. */
    val canSendFile: Boolean
        get() = (phase is SendPhase.Failed && phase.reason != SendFailure.STALE) || phase == SendPhase.NeedsPermission

    /** Уход с экрана спрашивает подтверждение: пакет мог дойти, а запись ещё не сделана. */
    val leaveNeedsConfirm: Boolean
        get() = phase is SendPhase.Sending ||
            phase is SendPhase.AwaitingAccept ||
            (phase is SendPhase.Failed && phase.ackLost) ||
            dialog == SendDialog.FileSent

    /** Вкладку «Принять игру» не включить, пока идёт передача. */
    val modeLocked: Boolean
        get() = busy ||
            leaveNeedsConfirm ||
            phase is SendPhase.Done ||
            (phase is SendPhase.Code && phase.waitingPeer)
}

sealed interface SendAction {
    /** Вкладка «Отдать игру» стала видимой. Повторный Activate посреди передачи ничего не делает. */
    data object Activate : SendAction

    /** Вкладка скрыта: поиск останавливается, но идущая передача — нет. */
    data object Deactivate : SendAction

    /** Экран снова на переднем плане (вернулись из настроек). */
    data object Resumed : SendAction

    data object AllowPermissions : SendAction

    /** [permanentlyDenied] — отказ «больше не спрашивать» (shouldShowRequestPermissionRationale = false). */
    data class PermissionsResult(val allGranted: Boolean, val permanentlyDenied: Boolean = false) : SendAction

    data object OpenSettings : SendAction

    data object Retry : SendAction

    data class SelectDevice(val endpointId: String) : SendAction

    data class KeepIncome(val keep: Boolean) : SendAction

    /** Hold «Код совпадает — удерживайте»: подтвердить код и отправить. */
    data object ConfirmAndSend : SendAction

    /** «Код другой — отмена». */
    data object RejectCode : SendAction

    data object SendFile : SendAction

    data class FileRecipientChanged(val name: String) : SendAction

    data object FileRecipientConfirmed : SendAction

    data object FileSentConfirmed : SendAction

    data object OpenAckLost : SendAction

    data object AckLostConfirmed : SendAction

    data object DismissDialog : SendAction

    /** «Назад» (кнопка шапки или системная). */
    data object Back : SendAction

    /** «Уйти» в «Прервать передачу?». */
    data object ConfirmLeave : SendAction

    /** «На главную» после передачи. */
    data object Finish : SendAction
}

sealed interface SendEvent {
    data class RequestPermissions(val permissions: List<String>) : SendEvent

    data object OpenAppSettings : SendEvent

    data class Share(val file: SharedFile) : SendEvent

    data object Leave : SendEvent

    data object Finished : SendEvent
}
