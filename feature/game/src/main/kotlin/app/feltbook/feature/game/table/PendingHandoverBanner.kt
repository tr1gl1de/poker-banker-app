package app.feltbook.feature.game.table

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.feltbook.core.data.handover.ConfirmOutcome
import app.feltbook.core.data.handover.PendingHandovers
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.OutlineButton
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.R
import app.feltbook.feature.game.common.toGameId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Неподтверждённая передача этой игры: кому и сколько наличных; [stale] — записать уже нельзя. */
@Immutable
data class PendingHandoverUi(
    val toBanker: String,
    val cash: Long,
    val busy: Boolean = false,
    val stale: Boolean = false,
)

/**
 * Стол спрашивает «Новый банкир <имя> принял игру?», если пакет передачи ушёл, а подтверждения не было (экран
 * передачи закрыли, процесс умер, ACK потерялся). «Да, принял» записывает ту же передачу, «Нет, игра у меня» — снимает
 * вопрос. Так игра не остаётся активной на двух телефонах незаметно.
 */
@HiltViewModel
class PendingHandoverViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val pending: PendingHandovers,
) : ViewModel() {
    private val gameId = checkNotNull(savedStateHandle.get<String>(TableViewModel.ARG_GAME_ID)).toGameId()
    private val busy = MutableStateFlow(false)

    /** «Да, принял», а записать нельзя (журнал ушёл вперёд): вопрос снят, показываем почему — до «Понятно». */
    private val stale = MutableStateFlow<PendingHandoverUi?>(null)

    val uiState: StateFlow<PendingHandoverUi?> =
        combine(pending.observe(gameId), busy, stale) { handover, isBusy, staleUi ->
            staleUi ?: handover?.let { PendingHandoverUi(it.toBanker, it.cashHandedOver, busy = isBusy) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun onAccepted() {
        val shown = uiState.value ?: return
        if (busy.value) return
        busy.update { true }
        viewModelScope.launch {
            if (pending.confirm(gameId) == ConfirmOutcome.STALE) stale.update { shown.copy(busy = false, stale = true) }
            busy.update { false }
        }
    }

    /** «Нет, игра у меня» (или «Понятно» после ошибки). */
    fun onKept() {
        stale.update { null }
        viewModelScope.launch { pending.discard(gameId) }
    }
}

@Composable
fun PendingHandoverBanner(modifier: Modifier = Modifier, viewModel: PendingHandoverViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val current = state ?: return
    PendingHandoverCard(
        state = current,
        onAccepted = viewModel::onAccepted,
        onKept = viewModel::onKept,
        modifier = modifier,
    )
}

@Composable
fun PendingHandoverCard(
    state: PendingHandoverUi,
    onAccepted: () -> Unit,
    onKept: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = FeltbookTheme.shapes.card
    Column(
        modifier =
            modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(color = colors.dialog)
                .border(width = 1.dp, color = colors.brassBorderStrong, shape = shape)
                .padding(16.dp)
                .semantics(mergeDescendants = false) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(space = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.table_pending_handover_title, state.toBanker),
            style = type.title,
            color = colors.ink,
        )
        Text(
            text = stringResource(
                if (state.stale) R.string.table_pending_handover_stale else R.string.table_pending_handover_body,
                MoneyFormat.rub(state.cash),
            ),
            style = type.caption,
            color = colors.inkSoft,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            OutlineButton(
                text = stringResource(
                    if (state.stale) R.string.table_pending_handover_ok else R.string.table_pending_handover_keep,
                ),
                onClick = onKept,
                height = FeltbookDimens.button48,
                enabled = !state.busy,
                modifier = Modifier.weight(weight = 1f),
            )
            BrassButton(
                text = stringResource(R.string.table_pending_handover_accepted),
                onClick = onAccepted,
                style = BrassButtonStyle.Flat48,
                enabled = !state.busy && !state.stale,
                modifier = Modifier.weight(weight = 1f),
            )
        }
    }
}
