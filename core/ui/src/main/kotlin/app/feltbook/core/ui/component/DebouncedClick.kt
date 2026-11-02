package app.feltbook.core.ui.component

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Окно защиты от двойного нажатия (ТЗ §5.1): повтор < 600 мс игнорируется. */
const val DEBOUNCE_WINDOW_MILLIS: Long = 600L

/** Монотонные часы для [rememberDebouncedClick]; в тестах подменяются. */
fun interface ClickClock {
    fun nowMillis(): Long
}

/** Часы debounce. По умолчанию — `SystemClock.uptimeMillis()`. */
val LocalClickClock = staticCompositionLocalOf { ClickClock { SystemClock.uptimeMillis() } }

/** Чистая логика debounce: первое нажатие принимается, следующие в пределах [windowMillis] — нет. */
class ClickDebouncer(private val windowMillis: Long = DEBOUNCE_WINDOW_MILLIS) {
    private var lastAccepted: Long? = null

    /** `true`, если нажатие в момент [nowMillis] нужно обработать. */
    fun tryAccept(nowMillis: Long): Boolean {
        val last = lastAccepted
        if (last != null && nowMillis - last in 0 until windowMillis) return false
        lastAccepted = nowMillis
        return true
    }
}

/**
 * `DebouncedClick`: обёртка над [onClick], которая игнорирует повтор в пределах [windowMillis].
 * Блокировку «до завершения операции» даёт `enabled = false` у кнопки — это состояние экрана.
 */
@Composable
fun rememberDebouncedClick(windowMillis: Long = DEBOUNCE_WINDOW_MILLIS, onClick: () -> Unit): () -> Unit {
    val clock = LocalClickClock.current
    val current by rememberUpdatedState(onClick)
    val debouncer = remember(windowMillis) { ClickDebouncer(windowMillis) }
    return remember(debouncer, clock) {
        { if (debouncer.tryAccept(clock.nowMillis())) current() }
    }
}
