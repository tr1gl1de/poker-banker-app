package app.feltbook.core.data.handover

import app.feltbook.core.domain.engine.GameTemplate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Принятый шаблон новой игры (ТЗ §6 сценарий 2, QUESTIONS №5): правила уже записаны в `rulesForNewGame`, имена —
 * подсказка «Новой игры», пока игра не начата. Пишет экран приёма (`:feature:handover`), читает «Новая игра»
 * (`:feature:game`). В памяти: после перезапуска приложения подсказка пропадает, правила остаются.
 */
@Singleton
class ReceivedTemplateStore @Inject constructor() {
    private val mutable = MutableStateFlow<GameTemplate?>(null)
    val template: StateFlow<GameTemplate?> = mutable.asStateFlow()

    fun put(template: GameTemplate) {
        mutable.value = template
    }

    /** Игра начата — подсказка больше не нужна. */
    fun clear() {
        mutable.value = null
    }
}
