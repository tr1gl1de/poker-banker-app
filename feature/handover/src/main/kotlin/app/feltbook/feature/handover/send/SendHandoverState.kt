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

    data class Done(val peerName: String) : SendPhase

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
}

@Immutable
data class SendUiState(
    val phase: SendPhase = SendPhase.Loading,
    val kind: SendKind = SendKind.HANDOVER,
    val gameTitle: String = "",
    val act: HandoverAct? = null,
    val keepIncome: Boolean = false,
    val rules: GameRules? = null,
    val templateNames: Int = 0,
    val dialog: SendDialog? = null,
    val busy: Boolean = false,
) {
    /** Переключатель «Оставить доход себе» доступен, только если есть что забрать (m3-ui-spec §5.10). */
    val keepIncomeEnabled: Boolean get() = (act?.incomeTakeable ?: 0L) > 0L

    val cashToHandOver: Long get() = act?.cashToHandOver(keepIncome && keepIncomeEnabled) ?: 0L

    /** «Отправить файлом» — после любой неудачи и при отказе в разрешениях. */
    val canSendFile: Boolean
        get() = (phase is SendPhase.Failed && phase.reason != SendFailure.STALE) || phase == SendPhase.NeedsPermission
}

sealed interface SendAction {
    /** Вкладка «Отдать игру» стала видимой / скрытой. */
    data object Activate : SendAction

    data object Deactivate : SendAction

    data object AllowPermissions : SendAction

    data class PermissionsResult(val allGranted: Boolean) : SendAction

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

    /** «На главную» после передачи. */
    data object Finish : SendAction
}

sealed interface SendEvent {
    data class RequestPermissions(val permissions: List<String>) : SendEvent

    data class Share(val file: SharedFile) : SendEvent

    data object Finished : SendEvent
}
