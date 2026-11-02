package app.feltbook.feature.game.table

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.game.R
import kotlinx.coroutines.launch

/** Ширина окна ≥ 600 dp (Medium+): стол — список + карточка игрока (m3-ui-spec §9). */
@Composable
internal fun isTwoPane(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(TWO_PANE_MIN_WIDTH_DP)

private const val TWO_PANE_MIN_WIDTH_DP = 600

/**
 * Планшет: `ListDetailPaneScaffold`. На Expanded видны обе панели, на Medium — одна с переходом
 * «список → карточка» и возвратом «назад». Закуп/выход/оплата — те же шторки (ширина ≤ 640 dp, по центру).
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun TableTwoPane(
    state: TableUiState,
    onAction: (TableAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val navigator = rememberListDetailPaneScaffoldNavigator<String>()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    BackHandler(enabled = navigator.canNavigateBack()) { scope.launch { navigator.navigateBack() } }
    ListDetailPaneScaffold(
        modifier = modifier.fillMaxSize().feltBackground(),
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                Box(modifier = Modifier.fillMaxSize()) {
                    TableList(
                        state = state,
                        onAction = onAction,
                        onRowClick = { id ->
                            onAction(TableAction.SelectPlayer(id))
                            scope.launch { navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, id) }
                        },
                        listState = listState,
                        selectedId = state.selectedPlayerId,
                    )
                    TableFabAndSnackbar(state = state, onAction = onAction, snackbarHostState = snackbarHostState)
                }
            }
        },
        detailPane = {
            AnimatedPane {
                val card = state.detail
                Box(modifier = Modifier.fillMaxSize().background(color = FeltbookTheme.colors.sheet)) {
                    if (card == null) {
                        Text(
                            text = stringResource(R.string.table_detail_placeholder),
                            style = FeltbookTheme.typography.caption,
                            color = FeltbookTheme.colors.muted,
                            modifier = Modifier.align(Alignment.Center).padding(all = 24.dp),
                        )
                    } else {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .windowInsetsPadding(WindowInsets.statusBars)
                                    .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(space = 16.dp),
                        ) {
                            PlayerCardContent(card = card, now = state.now, onAction = onAction)
                        }
                    }
                }
            }
        },
    )
}
