package app.feltbook.feature.game.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.component.SheetDragHandle
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.game.common.LocalTimeZone
import java.time.ZoneId

/** Содержимое шторки без окна (для превью и Roborazzi: окно `ModalBottomSheet` в снимок экрана не попадает). */
@Composable
internal fun SheetFrame(spacing: Dp = 14.dp, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalTimeZone provides ZoneId.of("Europe/Moscow")) {
        Column(
            modifier =
                Modifier
                    .width(width = 390.dp)
                    .background(color = FeltbookTheme.colors.sheet, shape = FeltbookTheme.shapes.sheet)
                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(space = spacing),
        ) {
            SheetDragHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
            content()
        }
    }
}

/** Содержимое шторки по состоянию стола (для превью и скриншотов). */
@Composable
internal fun SheetPreviewContent(state: TableUiState) {
    when (val sheet = state.sheet) {
        is PlayerCardUi -> SheetFrame(spacing = 16.dp) {
            PlayerCardContent(card = sheet, now = state.now, onAction = {})
        }

        // Содержимое + закреплённые внизу действия (в шторке они вне прокрутки).
        is BuyInUi -> SheetFrame(spacing = 12.dp) {
            BuyInContent(ui = sheet, onAction = {})
            IssueButton(quote = sheet.quote, payment = sheet.payment, enabled = sheet.canConfirm, onAction = {})
        }

        is NewPlayerUi -> SheetFrame {
            NewPlayerContent(ui = sheet, onAction = {})
            NewPlayerActions(ui = sheet, onAction = {})
        }

        is PayUi -> SheetFrame(spacing = 12.dp) {
            PayContent(ui = sheet, onAction = {})
            PayActions(ui = sheet, onAction = {})
        }

        is CashOutUi -> SheetFrame {
            CashOutContent(ui = sheet, onAction = {})
            CashOutActions(ui = sheet, onAction = {})
        }

        is BreakdownUi -> SheetFrame(spacing = 12.dp) { BreakdownContent(sheet) }

        null -> Unit
    }
}

@Preview(heightDp = 1400)
@Composable
private fun PlayerCardPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.card()) }
}

@Preview(heightDp = 1200)
@Composable
private fun BuyInPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.buyIn()) }
}

@Preview(heightDp = 1200)
@Composable
private fun BuyInSuspiciousPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.suspiciousBuyIn()) }
}

@Preview(heightDp = 900)
@Composable
private fun NewPlayerPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.newPlayer()) }
}

@Preview(heightDp = 900)
@Composable
private fun NewPlayerDebtPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.newPlayerWithDebt()) }
}

@Preview(heightDp = 1000)
@Composable
private fun PayPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.pay()) }
}

@Preview(heightDp = 1200)
@Composable
private fun CashOutPayPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.cashOutPay()) }
}

@Preview(heightDp = 1200)
@Composable
private fun CashOutOwePreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.cashOutOwe()) }
}

@Preview(heightDp = 1200)
@Composable
private fun CashOutKeypadPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.cashOutKeypad()) }
}

@Preview(heightDp = 800)
@Composable
private fun BreakdownPreview() {
    FeltbookTheme { SheetPreviewContent(PreviewTableData.breakdown()) }
}
