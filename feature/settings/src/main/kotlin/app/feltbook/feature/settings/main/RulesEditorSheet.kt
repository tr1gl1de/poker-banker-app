package app.feltbook.feature.settings.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.GameRules
import app.feltbook.core.domain.model.RakeMode
import app.feltbook.core.domain.model.RulesProblem
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.AmountKeypad
import app.feltbook.core.ui.component.AmountText
import app.feltbook.core.ui.component.BrassButton
import app.feltbook.core.ui.component.BrassButtonStyle
import app.feltbook.core.ui.component.FeltbookSheet
import app.feltbook.core.ui.component.LocalCompactSheet
import app.feltbook.core.ui.component.SegmentedToggle
import app.feltbook.core.ui.component.SheetTitle
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.format.pluralQuantity
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.settings.R

/**
 * Шторка правила по умолчанию (m3-ui-spec §5.13): сбор — 4 режима + значение, рейк — «Процент / Фикс» + значение,
 * минимум и время — клавиатура, оплата — 2 варианта. Проблемы `problems()` — текстом, «Сохранить» недоступна.
 */
@Composable
fun RulesEditorSheet(editor: RulesEditorUi, onAction: (SettingsAction) -> Unit) {
    FeltbookSheet(
        onDismiss = { onAction(SettingsAction.DismissEditor) },
        hasInput = editor.draft != editor.base,
        discardBody = stringResource(R.string.settings_rules_discard_body),
        contentSpacing = 12.dp,
        // «Сохранить» закреплена внизу: на низких экранах (Redmi 6A) она иначе уходит под навигацию.
        bottomBar = { RulesSaveButton(editor = editor, onAction = onAction) },
    ) {
        RulesEditorContent(editor = editor, onAction = onAction, showSave = false)
    }
}

@Composable
internal fun ColumnScope.RulesEditorContent(
    editor: RulesEditorUi,
    onAction: (SettingsAction) -> Unit,
    showSave: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    SheetTitle(title = stringResource(editor.field.title()))
    when (editor.field) {
        RuleField.BANKER_FEE -> Column(modifier = Modifier.selectableGroup()) {
            BankerFeeMode.entries.forEach { mode ->
                RadioRow(
                    text = stringResource(mode.label()),
                    selected = editor.feeMode == mode,
                    onClick = { onAction(SettingsAction.EditorFeeMode(mode)) },
                )
            }
        }

        RuleField.RAKE -> SegmentedToggle(
            options = listOf(
                stringResource(R.string.settings_rules_percent),
                stringResource(R.string.settings_rules_fixed),
            ),
            selectedIndex = if (editor.rakeMode == RakeMode.PERCENT) 0 else 1,
            onSelect = { index ->
                onAction(SettingsAction.EditorRakeMode(if (index == 0) RakeMode.PERCENT else RakeMode.FIXED))
            },
            modifier = Modifier.fillMaxWidth(),
        )

        RuleField.PAYMENT -> Column(modifier = Modifier.selectableGroup()) {
            RadioRow(
                text = stringResource(R.string.settings_payment_single_option),
                selected = !editor.split,
                onClick = { onAction(SettingsAction.EditorSplit(false)) },
            )
            RadioRow(
                text = stringResource(R.string.settings_payment_split_option),
                subtitle = stringResource(R.string.settings_payment_split_hint),
                selected = editor.split,
                onClick = { onAction(SettingsAction.EditorSplit(true)) },
            )
        }

        RuleField.MIN_BUY_IN, RuleField.MIN_PLAY_MINUTES -> Unit
    }
    if (editor.usesKeypad) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AmountText(
                text = editorValue(editor),
                style = if (LocalCompactSheet.current) type.amountInput else type.amountHero,
                color = if (editor.entry.isEmpty) colors.muted else colors.ink,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        editor.problems.forEach { problem ->
            Text(text = stringResource(problem.text()), style = type.small, color = colors.owe)
        }
        AmountKeypad(
            entry = editor.entry,
            onEntryChange = { onAction(SettingsAction.EditorEntry(it)) },
            maxValue = editor.maxValue,
        )
    }
    if (showSave) RulesSaveButton(editor = editor, onAction = onAction)
}

@Composable
private fun RulesSaveButton(editor: RulesEditorUi, onAction: (SettingsAction) -> Unit) {
    BrassButton(
        text = stringResource(UiR.string.common_save),
        onClick = { onAction(SettingsAction.SaveRules) },
        style = BrassButtonStyle.Hero64,
        enabled = editor.canSave,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** «100 ₽», «10 %», «500 фишек», «60 мин». */
@Composable
private fun editorValue(editor: RulesEditorUi): String {
    val value = editor.entry.value
    return when (editor.field) {
        RuleField.BANKER_FEE -> feeAmount(editor.feeMode, value)

        RuleField.RAKE -> rakeAmount(editor.rakeMode, value)

        RuleField.MIN_BUY_IN ->
            pluralStringResource(UiR.plurals.chips_count, pluralQuantity(value), MoneyFormat.number(value))

        RuleField.MIN_PLAY_MINUTES -> stringResource(UiR.string.duration_minutes, value.toInt())

        RuleField.PAYMENT -> ""
    }
}

private fun RuleField.title(): Int = when (this) {
    RuleField.BANKER_FEE -> R.string.settings_fee
    RuleField.RAKE -> R.string.settings_rake
    RuleField.MIN_BUY_IN -> R.string.settings_min_buy_in
    RuleField.MIN_PLAY_MINUTES -> R.string.settings_min_play_time
    RuleField.PAYMENT -> R.string.settings_payment
}

private fun BankerFeeMode.label(): Int = when (this) {
    BankerFeeMode.ONCE_FIXED -> R.string.settings_rules_fee_once_fixed
    BankerFeeMode.ONCE_PERCENT -> R.string.settings_rules_fee_once_percent
    BankerFeeMode.EACH_FIXED -> R.string.settings_rules_fee_each_fixed
    BankerFeeMode.EACH_PERCENT -> R.string.settings_rules_fee_each_percent
}

private fun RulesProblem.text(): Int = when (this) {
    RulesProblem.BANKER_FEE_NEGATIVE -> R.string.settings_rules_problem_fee_negative
    RulesProblem.BANKER_FEE_PERCENT_ABOVE_MAX -> R.string.settings_rules_problem_fee_percent
    RulesProblem.BANKER_FEE_ABOVE_MAX_AMOUNT -> R.string.settings_rules_problem_fee_amount
    RulesProblem.RAKE_NEGATIVE -> R.string.settings_rules_problem_rake_negative
    RulesProblem.RAKE_PERCENT_ABOVE_MAX -> R.string.settings_rules_problem_rake_percent
    RulesProblem.RAKE_ABOVE_MAX_AMOUNT -> R.string.settings_rules_problem_rake_amount
    RulesProblem.MIN_BUY_IN_NOT_POSITIVE -> R.string.settings_rules_problem_min_buy_in
    RulesProblem.MIN_BUY_IN_ABOVE_MAX_AMOUNT -> R.string.settings_rules_problem_min_buy_in_amount
    RulesProblem.MIN_PLAY_MINUTES_OUT_OF_RANGE -> R.string.settings_rules_problem_minutes
}

@Preview(widthDp = 390)
@Composable
private fun FeeEditorPreview() {
    FeltbookTheme {
        Column(modifier = Modifier.padding(all = 20.dp), verticalArrangement = Arrangement.spacedBy(space = 12.dp)) {
            RulesEditorContent(editor = RulesEditorUi.open(RuleField.BANKER_FEE, GameRules.DEFAULT), onAction = {})
        }
    }
}

@Preview(widthDp = 390)
@Composable
private fun MinBuyInProblemPreview() {
    FeltbookTheme {
        Column(modifier = Modifier.padding(all = 20.dp), verticalArrangement = Arrangement.spacedBy(space = 12.dp)) {
            val editor = RulesEditorUi.open(RuleField.MIN_BUY_IN, GameRules.DEFAULT)
            RulesEditorContent(editor = editor.copy(entry = editor.entry.copy(value = 0L)), onAction = {})
        }
    }
}
