package app.feltbook

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.LocalActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.feltbook.core.domain.model.ThemeMode
import app.feltbook.core.ui.component.BottomBarIcon
import app.feltbook.core.ui.component.BottomBarItem
import app.feltbook.core.ui.component.FeltbookBottomBar
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.game.navigation.HomeRoute
import app.feltbook.feature.game.navigation.TableRoute
import app.feltbook.feature.history.DebtsRoute
import app.feltbook.feature.history.HistoryRoute
import app.feltbook.feature.settings.OddsRoute
import app.feltbook.feature.settings.SettingsRoute
import app.feltbook.navigation.FeltbookNavHost
import app.feltbook.navigation.HistoryGraph
import app.feltbook.navigation.SettingsGraph
import app.feltbook.navigation.Tab
import app.feltbook.navigation.openTab
import kotlinx.coroutines.launch

/** Корень окна: тема из настроек, edge-to-edge, «экран не гаснет», навигация, нижняя панель, пасхалка. */
@Composable
fun FeltbookRoot(viewModel: AppViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeGameId by viewModel.activeGameId.collectAsStateWithLifecycle()
    val coldStart by viewModel.coldStart.collectAsStateWithLifecycle()
    val dark = when (settings?.themeMode ?: ThemeMode.DARK) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    SystemBars(dark = dark)
    KeepScreenOn(enabled = settings?.keepScreenOn != false && activeGameId != null)
    FeltbookTheme(
        darkTheme = dark,
        dynamicColor = settings?.dynamicColor == true,
        confirmHaptics = settings?.haptics != false,
    ) {
        when (val start = coldStart) {
            ColdStart.Loading -> Box(Modifier.fillMaxSize().feltBackground())

            is ColdStart.Ready ->
                AppScaffold(
                    coldStartGameId = start.activeGameId,
                    oddsUnlocked = settings?.oddsCalculatorVisible == true,
                    onUnlockOdds = viewModel::unlockOdds,
                )
        }
    }
}

@Composable
private fun SystemBars(dark: Boolean) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(dark) {
        val transparent = androidx.compose.ui.graphics.Color.Transparent.toArgb()
        val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}

@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    SideEffect { view.keepScreenOn = enabled }
}

private fun NavDestination?.tab(): Tab? = when {
    this == null -> null
    hierarchy.any { it.hasRoute<HistoryGraph>() } -> Tab.HISTORY
    hierarchy.any { it.hasRoute<SettingsGraph>() } -> Tab.SETTINGS
    else -> Tab.GAME
}

/** Где видна нижняя панель (m3-ui-spec §8.2). */
private fun NavDestination?.showsBottomBar(): Boolean = this == null ||
    hasRoute<HomeRoute>() ||
    hasRoute<TableRoute>() ||
    hasRoute<DebtsRoute>() ||
    hasRoute<HistoryRoute>() ||
    hasRoute<SettingsRoute>()

/** Порог прокрутки, после которого панель на столе прячется/возвращается. */
private const val SCROLL_HIDE_THRESHOLD = 6f
private const val BAR_ANIMATION_MILLIS = 200

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppScaffold(coldStartGameId: String?, oddsUnlocked: Boolean, onUnlockOdds: () -> Unit) {
    val navController = rememberNavController()
    val entry by navController.currentBackStackEntryAsState()
    val destination = entry?.destination
    val tab = destination.tab()
    val snackbar = remember { SnackbarHostState() }
    val unlockSnackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val counter = remember { EasterEggCounter() }
    val resources = LocalResources.current

    // Холодный старт: игра идёт — Стол поверх Главной (один раз за процесс, не при повороте).
    var coldStartHandled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(coldStartGameId) {
        if (!coldStartHandled && coldStartGameId != null) {
            navController.navigate(TableRoute(coldStartGameId)) { launchSingleTop = true }
        }
        coldStartHandled = true
    }

    // Стол: панель прячется при прокрутке вниз и возвращается при прокрутке вверх.
    var hiddenByScroll by remember { mutableStateOf(false) }
    LaunchedEffect(destination?.id) { hiddenByScroll = false }
    val scrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -SCROLL_HIDE_THRESHOLD -> hiddenByScroll = true
                    available.y > SCROLL_HIDE_THRESHOLD -> hiddenByScroll = false
                }
                return Offset.Zero
            }
        }
    }
    val onTable = destination?.hasRoute<TableRoute>() == true
    val showBar = destination.showsBottomBar() && !(onTable && hiddenByScroll) && !WindowInsets.isImeVisible

    val items = listOf(
        BottomBarItem(stringResource(R.string.nav_game), BottomBarIcon.Glyph(stringResource(R.string.nav_game_glyph))),
        BottomBarItem(stringResource(R.string.nav_history), BottomBarIcon.Vector(FeltbookIcons.History)),
        BottomBarItem(stringResource(R.string.nav_settings), BottomBarIcon.Vector(FeltbookIcons.Settings)),
    )
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = FeltbookTheme.colors.feltDeep,
        snackbarHost = {
            // «Ещё N…» — короткие подсказки; «Секретный стол открыт · Открыть» — обычные 5 с (+ a11y).
            Box {
                FeltbookSnackbarHost(hostState = snackbar, durationMillis = EASTER_HINT_MILLIS)
                FeltbookSnackbarHost(hostState = unlockSnackbar)
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(animationSpec = tween(BAR_ANIMATION_MILLIS)) { it },
                exit = slideOutVertically(animationSpec = tween(BAR_ANIMATION_MILLIS)) { it },
            ) {
                FeltbookBottomBar(
                    items = items,
                    selectedIndex = (tab ?: Tab.GAME).ordinal,
                    onItemClick = { index ->
                        val target = Tab.entries[index]
                        if (target == Tab.SETTINGS) {
                            when (val result = counter.tap(SystemClock.uptimeMillis(), oddsUnlocked)) {
                                is EasterEggTap.Hint -> scope.launch {
                                    snackbar.showUndoSnackbar(
                                        resources.getString(R.string.easter_more_taps, result.remaining),
                                        actionLabel = null,
                                    )
                                }

                                EasterEggTap.Unlocked -> {
                                    onUnlockOdds()
                                    scope.launch {
                                        snackbar.currentSnackbarData?.dismiss()
                                        val result = unlockSnackbar.showUndoSnackbar(
                                            resources.getString(R.string.easter_unlocked),
                                            actionLabel = resources.getString(R.string.easter_open),
                                        )
                                        if (result == SnackbarResult.ActionPerformed) navController.navigate(OddsRoute)
                                    }
                                }

                                EasterEggTap.Silent -> Unit
                            }
                        }
                        navController.openTab(target, current = tab)
                    },
                )
            }
        },
    ) { padding ->
        FeltbookNavHost(
            navController = navController,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding).nestedScroll(scrollConnection),
        )
    }
}

/** Подсказки пасхалки «Ещё N…» — короткие, без действия. */
private const val EASTER_HINT_MILLIS = 2_000L
