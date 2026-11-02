package app.feltbook.core.transfer

import kotlinx.coroutines.flow.StateFlow

/** Телефон рядом. [name] — имя, которое он рекламирует (получатель — `AppSettings.lastBankerName`). */
data class Endpoint(val id: String, val name: String)

/** Сколько байт пакета прошло. [total] = -1 — размер ещё неизвестен (получатель до заголовка кадра). */
data class TransferProgress(val bytes: Long, val total: Long) {
    /** 0..1 или null, пока размер неизвестен. */
    val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
}

/** Почему передача по воздуху не удалась. На любой причине экран сразу предлагает файл (ТЗ §6 «Запасной путь»). */
enum class FailureReason {
    /** Нет Google Play Services (или они устарели/отключены). */
    NEARBY_UNAVAILABLE,

    /** Пользователь не дал разрешения Bluetooth/Wi-Fi рядом/геолокации. */
    PERMISSION_DENIED,

    /** Не удалось начать рекламу/поиск (Bluetooth выключен, радио занято, нет разрешения на уровне системы). */
    START_FAILED,

    /** Второй телефон нажал «Код другой». */
    CONNECTION_REJECTED,

    /** Соединение не установилось или пакет не ушёл. */
    CONNECTION_FAILED,

    /** Связь прервалась. */
    DISCONNECTED,

    /** Ничего не найдено / не ответили вовремя. */
    TIMEOUT,

    /** Пакет пришёл повреждённым (кадр не сошёлся по длине или SHA-256). */
    CORRUPTED,

    /** Получатель отказался принимать. */
    DECLINED,
}

/**
 * Машина состояний передачи (одна сессия — один экран).
 *
 * Отправитель: Idle → Discovering ⇄ Found → Connecting → AwaitingCodeConfirm → Connected → Sending → AwaitingAck
 *   → Done.
 * Получатель: Idle → Advertising → AwaitingCodeConfirm → Connected → Receiving → AwaitingAccept → Done.
 * Любое состояние → Failed. Пакет — текст `FeltbookCodec` (кадр [PackageFrame] потоком, без лимита BYTES ~32 КБ).
 */
sealed interface TransferState {
    data object Idle : TransferState

    /** Получатель виден рядом под именем [localName]. */
    data class Advertising(val localName: String) : TransferState

    /** Отправитель ищет, пока никого нет. */
    data object Discovering : TransferState

    /** Отправитель нашёл телефоны (по порядку обнаружения). */
    data class Found(val endpoints: List<Endpoint>) : TransferState

    data class Connecting(val endpoint: Endpoint) : TransferState

    /**
     * Оба экрана показывают [digits] (`ConnectionInfo.authenticationDigits`, 4 цифры).
     * [confirmedLocally] — здесь уже нажали «Код совпадает», ждём второй телефон.
     */
    data class AwaitingCodeConfirm(val endpoint: Endpoint, val digits: String, val confirmedLocally: Boolean) :
        TransferState

    /** Оба подтвердили код — канал открыт. */
    data class Connected(val endpoint: Endpoint) : TransferState

    data class Sending(val endpoint: Endpoint, val progress: TransferProgress) : TransferState

    /** Пакет доставлен, ждём «Принять» на втором телефоне (ACK). */
    data class AwaitingAck(val endpoint: Endpoint) : TransferState

    data class Receiving(val endpoint: Endpoint, val progress: TransferProgress) : TransferState

    /**
     * Получатель: пакет цел по кадру (длина + SHA-256), [packageText] — текст для `FeltbookCodec.decode`.
     * [peerConnected] = false — связь уже прервалась: принять можно, но ACK отправителю не дойдёт.
     */
    data class AwaitingAccept(val endpoint: Endpoint, val packageText: String, val peerConnected: Boolean = true) :
        TransferState

    /** Отправитель получил ACK / получатель отправил ACK. */
    data class Done(val endpoint: Endpoint) : TransferState

    /**
     * @property packageSent пакет уже ушёл к получателю, а ACK не пришёл: игра могла открыться на втором телефоне —
     *   экран спрашивает «Новый банкир принял игру?», а не молча оставляет игру активной.
     * @property statusCode код Nearby (`ConnectionsStatusCodes`) для журнала отладки, если есть.
     */
    data class Failed(val reason: FailureReason, val packageSent: Boolean = false, val statusCode: Int? = null) :
        TransferState
}

/**
 * Передача по воздуху. Команды не блокируют: результат — в [state]. Команда не к месту (например, [send] до
 * [TransferState.Connected]) игнорируется. После [close] сессия не используется.
 */
interface TransferSession : AutoCloseable {
    val state: StateFlow<TransferState>

    /** Получатель: «Принять игру» — рекламировать себя под [localName]. */
    fun advertise(localName: String)

    /** Отправитель: «Передать игру» — искать телефоны рядом. [localName] видит получатель. */
    fun discover(localName: String)

    /** Отправитель выбрал телефон из [TransferState.Found]. */
    fun connect(endpointId: String)

    /** «Код совпадает». */
    fun confirmCode()

    /** «Код другой — отмена»: соединение отклоняется, поиск/реклама начинаются заново. */
    fun rejectCode()

    /** Отправитель: пакет (текст `FeltbookCodec.encode`). Только в [TransferState.Connected]. */
    fun send(packageText: String)

    /** Получатель: «Принять» нажато и импорт записан — отправить ACK. */
    fun acknowledge()

    /** Получатель отказался: отправителю уходит отказ. */
    fun decline()

    /** Остановить всё и вернуться в [TransferState.Idle]. */
    fun cancel()

    /** Остановить всё и освободить ресурсы (ViewModel.onCleared). */
    override fun close()
}
