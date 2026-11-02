package app.feltbook.feature.settings.main

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.feltbook.core.domain.model.AppSettings
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.ThemeMode
import app.feltbook.core.domain.transfer.FeltbookCodec
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.FeltTextField
import app.feltbook.core.ui.component.FeltbookDialog
import app.feltbook.core.ui.component.FeltbookSnackbarHost
import app.feltbook.core.ui.component.FeltbookSwitch
import app.feltbook.core.ui.component.LedgerHeader
import app.feltbook.core.ui.component.SwitchRow
import app.feltbook.core.ui.component.showUndoSnackbar
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.resolve
import app.feltbook.core.ui.theme.FeltbookDimens
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import app.feltbook.feature.settings.R
import kotlinx.coroutines.launch

/** Диалог над нижней панелью: 16 dp над ней (m3-ui-spec §4.14, `bottom 96`). */
internal val DialogAboveBar = FeltbookDimens.bottomBarHeight + 16.dp

/** Все файлы: `.feltbook` у разных проводников — `application/json` или `application/octet-stream`. */
private val OpenDocumentTypes = arrayOf("*/*")

/**
 * Маршрут настроек: состояние и события [SettingsViewModel], SAF `CreateDocument` (`.feltbook`,
 * `application/json`) и `OpenDocument`, snackbar «Отменить».
 */
@Composable
fun SettingsRouteScreen(
    versionName: String,
    onOpenOdds: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val undoLabel = stringResource(UiR.string.common_undo)
    val odds by rememberUpdatedState(onOpenOdds)
    val noPicker = stringResource(R.string.settings_no_file_picker)
    val createContract = remember { ActivityResultContracts.CreateDocument(FeltbookCodec.MIME_TYPE) }
    val create = rememberLauncherForActivityResult(createContract) {
        viewModel.onAction(SettingsAction.ExportTo(it?.toString()))
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        viewModel.onAction(SettingsAction.ImportFrom(it?.toString()))
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                // Нет приложения для выбора файла (ActivityNotFoundException) — сообщение, не падение.
                is SettingsEvent.CreateBackupDocument -> runCatching { create.launch(event.fileName) }.onFailure {
                    scope.launch { snackbar.showUndoSnackbar(noPicker, actionLabel = null) }
                }

                SettingsEvent.OpenBackupDocument -> runCatching { open.launch(OpenDocumentTypes) }.onFailure {
                    scope.launch { snackbar.showUndoSnackbar(noPicker, actionLabel = null) }
                }

                SettingsEvent.OpenOdds -> odds()

                is SettingsEvent.Undoable -> scope.launch {
                    val result = snackbar.showUndoSnackbar(event.message.resolve(resources), undoLabel)
                    if (result == SnackbarResult.ActionPerformed) viewModel.onAction(SettingsAction.Undo(event.token))
                }

                is SettingsEvent.Message -> scope.launch {
                    snackbar.showUndoSnackbar(event.text.resolve(resources), actionLabel = null)
                }
            }
        }
    }
    SettingsScreen(
        state = state,
        onAction = viewModel::onAction,
        versionName = versionName,
        onOpenHistory = onOpenHistory,
        snackbarHostState = snackbar,
    )
}

/** Настройки (m3-ui-spec §5.13): нижняя панель видна. */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    versionName: String,
    modifier: Modifier = Modifier,
    onOpenHistory: () -> Unit = {},
    dynamicColorAvailable: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Box(modifier = modifier.fillMaxSize().feltBackground()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = type.displayL,
                color = colors.ink,
                modifier =
                    Modifier
                        .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)
                        .semantics { heading() },
            )
            if (!state.loading) {
                RulesGroup(rules = state.rules, onAction = onAction)
                LookGroup(settings = state.settings, dynamicColorAvailable = dynamicColorAvailable, onAction = onAction)
                DataGroup(busy = state.busy, onAction = onAction)
                if (state.settings.oddsCalculatorVisible) SecretGroup(onAction = onAction)
                AboutGroup()
            }
            Text(
                text = stringResource(R.string.settings_footer, versionName),
                style = type.ledgerS,
                color = colors.ledger,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
            )
        }
        FeltbookSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp),
        )
    }
    state.editor?.let { RulesEditorSheet(editor = it, onAction = onAction) }
    if (state.themeDialog) ThemeDialog(current = state.settings.themeMode, onAction = onAction)
    state.deleteAllDialog?.let { DeleteAllDialog(dialog = it, onAction = onAction) }
    state.importResult?.let { ImportResultDialog(result = it, onAction = onAction, onOpenHistory = onOpenHistory) }
}

/** Группа строк: `Column(m(0 16), p(0 14), bg card, r18)`, между строками — верхняя черта `hairline`. */
@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(size = 18.dp))
                .background(color = FeltbookTheme.colors.card)
                .padding(horizontal = 14.dp),
        content = content,
    )
}

/** Верхняя черта строки группы `hairline` (у первой строки её нет). */
private fun Modifier.rowDivider(first: Boolean, color: Color): Modifier =
    if (first) this else drawBehind { drawRect(color, size = Size(size.width, 1.dp.toPx())) }

/** «♥ Сбор банкира | разово 100 ₽ ›». */
@Composable
private fun ValueRow(
    label: String,
    value: String?,
    onClick: (() -> Unit)?,
    first: Boolean = false,
    labelColor: Color = FeltbookTheme.colors.ink,
    valueColor: Color = FeltbookTheme.colors.brassBright,
    enabled: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .rowDivider(first, colors.hairline)
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = type.bodyL,
            color = if (enabled) labelColor else colors.inkDisabled,
            modifier = Modifier.weight(weight = 1f),
        )
        if (value != null) {
            Text(
                text = value,
                style = type.labelSemi,
                color = valueColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RulesGroup(rules: GameRules, onAction: (SettingsAction) -> Unit) {
    LedgerHeader(text = stringResource(R.string.settings_section_rules), topPadding = 12.dp)
    Group {
        ValueRow(
            label = stringResource(R.string.settings_fee),
            value = feeValue(rules),
            onClick = { onAction(SettingsAction.EditRule(RuleField.BANKER_FEE)) },
            first = true,
        )
        ValueRow(
            label = stringResource(R.string.settings_rake),
            value = stringResource(R.string.settings_rake_value, rakeAmount(rules.rakeMode, rules.rakeValue)),
            onClick = { onAction(SettingsAction.EditRule(RuleField.RAKE)) },
        )
        ValueRow(
            label = stringResource(R.string.settings_min_buy_in),
            value = stringResource(R.string.settings_min_buy_in_value, MoneyFormat.number(rules.minBuyIn)),
            onClick = { onAction(SettingsAction.EditRule(RuleField.MIN_BUY_IN)) },
        )
        ValueRow(
            label = stringResource(R.string.settings_min_play_time),
            value = stringResource(R.string.settings_min_play_time_value, rules.minPlayMinutes),
            onClick = { onAction(SettingsAction.EditRule(RuleField.MIN_PLAY_MINUTES)) },
        )
        ValueRow(
            label = stringResource(R.string.settings_payment),
            value = stringResource(
                if (rules.splitPayment) R.string.settings_payment_split else R.string.settings_payment_single,
            ),
            onClick = { onAction(SettingsAction.EditRule(RuleField.PAYMENT)) },
        )
    }
}

/** «разово 100 ₽ ›» / «разово 10 % ›» / «с каждого 100 ₽ ›» / «с каждого 10 % ›». */
@Composable
internal fun feeValue(rules: GameRules): String {
    val amount = feeAmount(rules.bankerFeeMode, rules.bankerFeeValue)
    return stringResource(
        when (rules.bankerFeeMode) {
            BankerFeeMode.ONCE_FIXED -> R.string.settings_fee_once_fixed
            BankerFeeMode.ONCE_PERCENT -> R.string.settings_fee_once_percent
            BankerFeeMode.EACH_FIXED -> R.string.settings_fee_each_fixed
            BankerFeeMode.EACH_PERCENT -> R.string.settings_fee_each_percent
        },
        amount,
    )
}

internal fun feeAmount(mode: BankerFeeMode, value: Long): String =
    if (mode.isPercent) MoneyFormat.percent(value.toInt()) else MoneyFormat.rub(value)

internal fun rakeAmount(mode: RakeMode, value: Long): String =
    if (mode == RakeMode.PERCENT) MoneyFormat.percent(value.toInt()) else MoneyFormat.rub(value)

@Composable
private fun LookGroup(settings: AppSettings, dynamicColorAvailable: Boolean, onAction: (SettingsAction) -> Unit) {
    val divided = Modifier.rowDivider(first = false, color = FeltbookTheme.colors.hairline).padding(vertical = 2.dp)
    LedgerHeader(text = stringResource(R.string.settings_section_look))
    Group {
        ValueRow(
            label = stringResource(R.string.settings_theme),
            value = stringResource(
                when (settings.themeMode) {
                    ThemeMode.DARK -> R.string.settings_theme_dark
                    ThemeMode.LIGHT -> R.string.settings_theme_light
                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                },
            ),
            onClick = { onAction(SettingsAction.ThemeClick) },
            first = true,
        )
        if (dynamicColorAvailable) {
            SwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_hint),
                checked = settings.dynamicColor,
                onCheckedChange = { onAction(SettingsAction.SetDynamicColor(it)) },
                modifier = divided,
            )
        } else {
            DisabledSwitchRow(
                title = stringResource(R.string.settings_dynamic_color),
                subtitle = stringResource(R.string.settings_dynamic_color_unavailable),
            )
        }
        SwitchRow(
            title = stringResource(R.string.settings_keep_screen_on),
            subtitle = stringResource(R.string.settings_keep_screen_on_hint),
            checked = settings.keepScreenOn,
            onCheckedChange = { onAction(SettingsAction.SetKeepScreenOn(it)) },
            modifier = divided,
        )
        SwitchRow(
            title = stringResource(R.string.settings_haptics),
            checked = settings.haptics,
            onCheckedChange = { onAction(SettingsAction.SetHaptics(it)) },
            modifier = divided,
        )
    }
}

/** «Цвета обоев» ниже Android 12: недоступно, подпись «доступно с Android 12». */
@Composable
private fun DisabledSwitchRow(title: String, subtitle: String) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .rowDivider(first = false, color = FeltbookTheme.colors.hairline)
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .semantics(mergeDescendants = true) {}
                .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(space = FeltbookDimens.blockGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(text = title, style = type.bodyL, color = colors.inkDisabled)
            Text(text = subtitle, style = type.small, color = colors.ledger)
        }
        FeltbookSwitch(checked = false, onCheckedChange = null)
    }
}

@Composable
private fun DataGroup(busy: Boolean, onAction: (SettingsAction) -> Unit) {
    val chevron = stringResource(UiR.string.chevron).trim()
    LedgerHeader(text = stringResource(R.string.settings_section_data))
    Group {
        ValueRow(
            label = stringResource(R.string.settings_backup),
            value = chevron,
            valueColor = FeltbookTheme.colors.ledger,
            onClick = { onAction(SettingsAction.Export) },
            first = true,
            enabled = !busy,
        )
        ValueRow(
            label = stringResource(R.string.settings_restore),
            value = chevron,
            valueColor = FeltbookTheme.colors.ledger,
            onClick = { onAction(SettingsAction.Import) },
            enabled = !busy,
        )
        ValueRow(
            label = stringResource(R.string.settings_delete_all),
            value = null,
            onClick = { onAction(SettingsAction.DeleteAll) },
            labelColor = FeltbookTheme.colors.owe,
            enabled = !busy,
        )
    }
}

/** Пасхалка открыта (m3-ui-spec §5.13 [без макета]): «Калькулятор шансов ›» и «Скрыть пункт». */
@Composable
private fun SecretGroup(onAction: (SettingsAction) -> Unit) {
    LedgerHeader(text = stringResource(R.string.settings_section_secret))
    Group {
        ValueRow(
            label = stringResource(R.string.settings_odds),
            value = stringResource(UiR.string.chevron).trim(),
            valueColor = FeltbookTheme.colors.ledger,
            onClick = { onAction(SettingsAction.OpenOdds) },
            first = true,
        )
        ValueRow(
            label = stringResource(R.string.settings_odds_hide),
            value = null,
            onClick = { onAction(SettingsAction.HideOdds) },
            labelColor = FeltbookTheme.colors.muted,
        )
    }
}

/** «О приложении»: лицензия MIT и шрифты под SIL OFL 1.1 (CLAUDE.md, правило 8). */
@Composable
private fun AboutGroup() {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    LedgerHeader(text = stringResource(R.string.settings_section_about))
    Group {
        listOf(
            R.string.settings_about_license to R.string.settings_about_license_value,
            R.string.settings_about_fonts to R.string.settings_about_fonts_value,
            R.string.settings_about_offline to R.string.settings_about_offline_value,
        ).forEachIndexed { index, (title, value) ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .rowDivider(first = index == 0, color = FeltbookTheme.colors.hairline)
                        .semantics(mergeDescendants = true) {}
                        .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(space = 2.dp),
            ) {
                Text(text = stringResource(title), style = type.bodyL, color = colors.ink)
                Text(text = stringResource(value), style = type.small, color = colors.ledger)
            }
        }
    }
}

@Composable
private fun ThemeDialog(current: ThemeMode, onAction: (SettingsAction) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(current) }
    FeltbookDialog(
        title = stringResource(R.string.settings_theme),
        body = stringResource(R.string.settings_theme_dialog_body),
        safeLabel = stringResource(UiR.string.common_cancel),
        onSafe = { onAction(SettingsAction.DismissTheme) },
        confirmLabel = stringResource(UiR.string.common_done),
        onConfirm = { onAction(SettingsAction.SetTheme(selected)) },
        destructive = false,
        bottomInset = DialogAboveBar,
    ) {
        Column(modifier = Modifier.selectableGroup()) {
            listOf(
                ThemeMode.DARK to R.string.settings_theme_dialog_dark,
                ThemeMode.LIGHT to R.string.settings_theme_dialog_light,
                ThemeMode.SYSTEM to R.string.settings_theme_dialog_system,
            ).forEach { (mode, label) ->
                RadioRow(text = stringResource(label), selected = selected == mode, onClick = { selected = mode })
            }
        }
    }
}

/** Строка выбора: вся строка — цель касания, роль RadioButton. */
@Composable
internal fun RadioRow(text: String, selected: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FeltbookDimens.minTouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = colors.brass, unselectedColor = colors.muted),
        )
        Column(modifier = Modifier.weight(weight = 1f), verticalArrangement = Arrangement.spacedBy(space = 2.dp)) {
            Text(text = text, style = type.bodyL, color = colors.ink)
            if (subtitle != null) Text(text = subtitle, style = type.small, color = colors.ledger)
        }
    }
}

@Composable
private fun DeleteAllDialog(dialog: DeleteAllDialogUi, onAction: (SettingsAction) -> Unit) {
    FeltbookDialog(
        title = stringResource(R.string.settings_delete_all_title),
        body = stringResource(R.string.settings_delete_all_body),
        safeLabel = stringResource(R.string.settings_delete_all_keep),
        onSafe = { onAction(SettingsAction.DismissDeleteAll) },
        confirmLabel = stringResource(R.string.settings_delete_all_confirm),
        onConfirm = { onAction(SettingsAction.ConfirmDeleteAll) },
        confirmEnabled = dialog.confirmed(),
        bottomInset = DialogAboveBar,
    ) {
        FeltTextField(
            value = dialog.typed,
            onValueChange = { onAction(SettingsAction.DeleteAllInput(it)) },
            label = stringResource(R.string.settings_delete_all_field),
        )
    }
}

@Composable
private fun ImportResultDialog(result: ImportResultUi, onAction: (SettingsAction) -> Unit, onOpenHistory: () -> Unit) {
    when (result) {
        is ImportResultUi.Done -> FeltbookDialog(
            title = stringResource(R.string.settings_restore_title),
            body = importReportText(result),
            safeLabel = stringResource(UiR.string.common_ok),
            onSafe = { onAction(SettingsAction.DismissImportResult) },
            confirmLabel = stringResource(R.string.settings_restore_open_history),
            onConfirm = {
                onAction(SettingsAction.DismissImportResult)
                onOpenHistory()
            },
            destructive = false,
            bottomInset = DialogAboveBar,
        )

        is ImportResultUi.Failed -> FeltbookDialog(
            title = stringResource(R.string.settings_restore_failed_title),
            body = result.message.resolve(),
            safeLabel = stringResource(UiR.string.common_ok),
            onSafe = { onAction(SettingsAction.DismissImportResult) },
            confirmLabel = stringResource(R.string.settings_restore_retry),
            onConfirm = {
                onAction(SettingsAction.DismissImportResult)
                onAction(SettingsAction.Import)
            },
            destructive = false,
            bottomInset = DialogAboveBar,
        )
    }
}

/** «Добавлено 3 игры · дополнено 1 · без изменений 5 · пропущено, на телефоне новее: 1 · …». */
@Composable
internal fun importReportText(result: ImportResultUi.Done): String {
    val separator = stringResource(UiR.string.separator_dot)
    val parts = buildList {
        add(
            stringResource(
                R.string.settings_restore_added,
                pluralStringResource(UiR.plurals.games_count, result.added, result.added),
            ),
        )
        if (result.extended > 0) add(stringResource(R.string.settings_restore_extended, result.extended))
        add(stringResource(R.string.settings_restore_identical, result.identical))
        if (result.localNewer > 0) add(stringResource(R.string.settings_restore_local_newer, result.localNewer))
        if (result.conflicts > 0) add(stringResource(R.string.settings_restore_conflicts, result.conflicts))
        if (result.namesAdded > 0) add(stringResource(R.string.settings_restore_names, result.namesAdded))
    }
    return parts.joinToString(separator)
}

/** Превью-состояния (и скриншоты). */
internal object PreviewSettingsStates {
    fun default(): SettingsUiState = SettingsUiState(loading = false)

    fun secret(): SettingsUiState =
        SettingsUiState(loading = false, settings = AppSettings(oddsCalculatorVisible = true, lastBankerName = "Саша"))

    fun importDone(): ImportResultUi.Done =
        ImportResultUi.Done(added = 3, extended = 1, identical = 5, localNewer = 1, conflicts = 1, namesAdded = 2)
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun SettingsPreview() {
    FeltbookTheme { SettingsScreen(state = PreviewSettingsStates.secret(), onAction = {}, versionName = "1.0") }
}

@Preview(widthDp = 390, heightDp = 1100)
@Composable
private fun SettingsNoDynamicColorPreview() {
    FeltbookTheme {
        SettingsScreen(
            state = PreviewSettingsStates.default(),
            onAction = {},
            versionName = "1.0",
            dynamicColorAvailable = false,
        )
    }
}

@Preview(widthDp = 390)
@Composable
private fun ImportReportPreview() {
    FeltbookTheme {
        Text(
            text = importReportText(PreviewSettingsStates.importDone()),
            style = FeltbookTheme.typography.body,
            color = FeltbookTheme.colors.ink,
            modifier = Modifier.padding(all = 16.dp).height(height = 120.dp),
        )
    }
}
