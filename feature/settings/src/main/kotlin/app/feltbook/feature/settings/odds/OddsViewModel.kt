package app.feltbook.feature.settings.odds

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.EquityRequest
import app.feltbook.core.odds.EquityResult
import app.feltbook.core.odds.Positions
import app.feltbook.core.ui.component.AmountEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/** Открытые шторки — не часть ввода, после пересоздания не восстанавливаются. */
internal data class OddsOverlay(val picker: PickerState? = null, val amount: AmountEditorUi? = null)

internal data class PickerState(val slot: CardSlot?, val deadMode: Boolean)

/** Ход расчёта эквити: последний (возможно, промежуточный или прежний) итог и идёт ли счёт. */
internal data class EquityProgress(val result: EquityResult? = null, val calculating: Boolean = false)

/**
 * Калькулятор шансов (ТЗ §4 «Пасхалка», m3-ui-spec §5.14). Ввод — в [SavedStateHandle], на диск ничего не пишется.
 * Эквити пересчитывается при смене карт, игроков или мёртвых карт: `collectLatest` отменяет прежний расчёт,
 * промежуточные итоги Монте-Карло сразу попадают в состояние. Ауты, шансы банка и префлоп — мгновенно.
 */
@HiltViewModel
class OddsViewModel @Inject constructor(private val savedState: SavedStateHandle, private val engine: OddsEngine) :
    ViewModel() {
    private val input = MutableStateFlow(OddsInput.restore(savedState))
    private val overlay = MutableStateFlow(OddsOverlay())
    private val progress = MutableStateFlow(EquityProgress())

    val uiState: StateFlow<OddsUiState> =
        combine(input, overlay, progress) { i, o, p -> buildOddsState(i, o, p) }
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                buildOddsState(input.value, overlay.value, progress.value),
            )

    init {
        viewModelScope.launch {
            input.map { it.equityRequest() }.distinctUntilChanged().collectLatest { calculate(it) }
        }
    }

    private suspend fun calculate(request: EquityRequest?) {
        if (request == null) {
            progress.value = EquityProgress()
            return
        }
        // Прежний итог остаётся (приглушённо), пока не придёт первая пачка нового расчёта.
        progress.update { it.copy(calculating = true) }
        engine.equity(request)
            .catch { error ->
                if (error !is IllegalArgumentException) throw error
                progress.value = EquityProgress()
            }.collect { result -> progress.value = EquityProgress(result, calculating = !result.isFinal) }
    }

    @Suppress("CyclomaticComplexMethod") // Плоский разбор действий экрана.
    fun onAction(action: OddsAction) {
        when (action) {
            is OddsAction.OpenSlot -> overlay.update { it.copy(picker = PickerState(action.slot, deadMode = false)) }

            OddsAction.OpenDeadCards -> overlay.update { it.copy(picker = PickerState(slot = null, deadMode = true)) }

            is OddsAction.PickerTab -> overlay.update { current ->
                val picker = current.picker
                if (picker?.slot == null) current else current.copy(picker = picker.copy(deadMode = action.dead))
            }

            is OddsAction.PickCard -> pick(action.card)

            OddsAction.RemoveCard -> removeCard()

            OddsAction.ClearDead -> updateInput { it.copy(dead = emptySet()) }

            OddsAction.ClosePicker -> overlay.update { it.copy(picker = null) }

            OddsAction.FewerPlayers -> updateInput { it.withPlayers(it.players - 1) }

            OddsAction.MorePlayers -> updateInput { it.withPlayers(it.players + 1) }

            is OddsAction.SelectPosition -> updateInput { current ->
                val exists = Positions.forPlayers(current.players).any { it.label == action.label }
                if (exists) current.copy(position = action.label) else current
            }

            is OddsAction.OpenAmount -> openAmount(action.field)

            is OddsAction.AmountInput -> overlay.update { it.copy(amount = it.amount?.copy(entry = action.entry)) }

            OddsAction.ConfirmAmount -> confirmAmount()

            OddsAction.CloseAmount -> overlay.update { it.copy(amount = null) }

            OddsAction.Reset -> {
                overlay.value = OddsOverlay()
                updateInput { OddsInput() }
            }
        }
    }

    private fun pick(card: Card) {
        val picker = overlay.value.picker ?: return
        if (picker.deadMode) toggleDead(card) else placeCard(picker, card)
    }

    /** Карта в слот [PickerState.slot]; занятая другим слотом — игнорируется, затем курсор на следующий пустой слот. */
    private fun placeCard(picker: PickerState, card: Card) {
        val slot = picker.slot
        val current = input.value
        val next = slot
            ?.takeUnless { card in current.used && card != current.cardAt(it) }
            ?.let { current.place(it, card) }
            ?: return
        updateInput { next }
        overlay.update { it.copy(picker = next.nextSlotAfter(slot)?.let { nextSlot -> picker.copy(slot = nextSlot) }) }
    }

    private fun removeCard() {
        val slot = overlay.value.picker?.takeUnless { it.deadMode }?.slot ?: return
        updateInput { it.remove(slot) }
        overlay.update { it.copy(picker = null) }
    }

    private fun toggleDead(card: Card) {
        updateInput { current ->
            when {
                card in current.dead -> current.copy(dead = current.dead - card)
                card in current.used -> current
                else -> current.copy(dead = current.dead + card)
            }
        }
    }

    private fun openAmount(field: OddsAmountField) {
        val value = when (field) {
            OddsAmountField.POT -> input.value.pot
            OddsAmountField.CALL -> input.value.call
        }
        val entry = if (value > 0) AmountEntry.preset(value) else AmountEntry()
        overlay.update { it.copy(amount = AmountEditorUi(field, entry)) }
    }

    private fun confirmAmount() {
        val editor = overlay.value.amount ?: return
        updateInput { current ->
            when (editor.field) {
                OddsAmountField.POT -> current.copy(pot = editor.entry.value)
                OddsAmountField.CALL -> current.copy(call = editor.entry.value)
            }
        }
        overlay.update { it.copy(amount = null) }
    }

    private fun updateInput(transform: (OddsInput) -> OddsInput) {
        input.updateAndGet(transform).save(savedState)
    }
}
