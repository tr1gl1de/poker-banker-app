package app.feltbook.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.format.MoneyFormat
import app.feltbook.core.ui.icons.FeltbookIcons
import app.feltbook.core.ui.icons.FeltbookMark
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Эталоны компонентов `:core:ui` (src/test/screenshots): тёмная тема, ширина 390 dp; ключевые — ещё и при
 * шрифте 200 % (строки сумм, табло, клавиатура, кнопки).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ru-w390dp-h844dp-xxhdpi")
class ComponentsScreenshotTest {
    @Test
    fun amountRows() = capture { AmountRows() }

    @Test
    fun amountRowsFontScale200() = capture(fontScale = 2f) { AmountRows() }

    @Test
    fun scoreboard() = capture { Scoreboard() }

    @Test
    fun scoreboardFontScale200() = capture(fontScale = 2f) { Scoreboard() }

    @Test
    fun scoreboardLight() = capture(darkTheme = false) { Scoreboard() }

    @Test
    fun holdButtonLight() = capture(darkTheme = false) {
        HoldToConfirmButton(title = "Выплатить 353 ₽", onConfirm = {})
        HoldToConfirmButton(title = "Закрыть игру", onConfirm = {}, size = HoldButtonSize.H56, enabled = false)
        BrassButton(text = "Начать игру", onClick = {}, modifier = Modifier.fillMaxWidth())
    }

    @Test
    fun keypad() = capture { Keypad() }

    @Test
    fun keypadFontScale200() = capture(fontScale = 2f) { Keypad() }

    @Test
    fun buttons() = capture { Buttons() }

    @Test
    fun buttonsFontScale200() = capture(fontScale = 2f) { Buttons() }

    @Test
    fun chipsAndAvatars() = capture {
        ChipDenominationRow(currentAmount = 2_000L, lastAmount = 1_500L, onSelect = {})
        ChipDenominationRow(currentAmount = 1_500L, lastAmount = 1_500L, onSelect = {})
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FeltbookMark()
            AvatarSize.entries.forEachIndexed { index, size ->
                PlayerAvatar(
                    name = listOf("Дима", "Борис", "Андрей", "Егор")[index],
                    suit = Suit.forSeat(index),
                    size = size,
                )
            }
            PlayerAvatar(name = "Саша", suit = Suit.Spades, active = false)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BankerBadge()
            RebuyBadge(count = 3)
        }
    }

    @Test
    fun togglesChipsFields() = capture {
        SegmentedToggle(options = listOf("В долг", "Оплачено сейчас"), selectedIndex = 1, onSelect = {})
        SegmentedToggle(
            options = listOf("Нет", "С закупа", "С игрока"),
            selectedIndex = 0,
            onSelect = {},
            style = SegmentedToggleStyle.Compact,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
            FeltFilterChip(text = "Нет", selected = true, onClick = {})
            FeltFilterChip(text = "до 100", selected = false, onClick = {})
            SmallToggle(text = "Банкиру", selected = true, onClick = {})
            DropdownChip(text = "За 3 месяца ▾", onClick = {})
        }
        SwitchRow(title = "Не гасить экран", checked = true, onCheckedChange = {})
        SwitchRow(title = "Вибрация при подтверждении", checked = false, onCheckedChange = {})
        FeltTextField(value = "Влад", onValueChange = {
        }, label = "Имя", large = true, error = "Такой игрок уже за столом")
        AmountField(label = "Фишки на руках", amount = 2_700L, focused = true, onClick = {})
        ValueBox(value = "10", unit = "%", onClick = {}, contentDescription = "Рейк 10 %")
    }

    @Test
    fun panelsAndBanners() = capture { Panels() }

    @Test
    fun panelsAndBannersFontScale200() = capture(fontScale = 2f) { Panels() }

    @Test
    fun navigationDialogSnackbar() = capture(padded = false) {
        Column(modifier = Modifier.padding(all = 16.dp), verticalArrangement = Arrangement.spacedBy(space = 16.dp)) {
            SectionHeader(title = "Прошлые игры", trailing = "Вся история", onTrailingClick = {})
            FeltbookDialogCard(
                title = "Отменить ввод?",
                body = "Введённая сумма 2 000 не сохранится.",
                safeLabel = "Продолжить ввод",
                onSafe = {},
                confirmLabel = "Отменить ввод",
                onConfirm = {},
            )
            UndoSnackbar(message = "Докуп 1 000 — Дима", actionLabel = "Отменить", onAction = {})
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                BrassFab(text = "Игрок", onClick = {}, contentDescription = "Добавить игрока")
            }
        }
        FeltbookBottomBar(items = PreviewBottomBarItems, selectedIndex = 2, onItemClick = {})
    }

    private fun capture(
        fontScale: Float = 1f,
        padded: Boolean = true,
        darkTheme: Boolean = true,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        captureRoboImage {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                FeltbookTheme(darkTheme = darkTheme) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .feltBackground()
                                .then(if (padded) Modifier.padding(all = 20.dp) else Modifier),
                        verticalArrangement = Arrangement.spacedBy(space = 12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountRows() {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    listOf(
        Triple("Дима", "закуп 3 000 · 1 ч 52 мин", -125_400L),
        Triple("Константин Константинопольский", "вышел · рассчитан", 2_700L),
        Triple("Егор", "оплачено", 0L),
    ).forEachIndexed { index, (name, meta, amount) ->
        AmountRowLayout(
            leading = { PlayerAvatar(name = name, suit = Suit.forSeat(index), active = amount != 2_700L) },
            trailing = {
                AmountText(
                    amount = amount,
                    style = type.amountRow,
                    color =
                        when {
                            amount < 0 -> colors.owe
                            amount > 0 -> colors.pay
                            else -> colors.ink
                        },
                    signed = true,
                )
            },
        ) {
            Column {
                NameWithBadges(name = name) {
                    if (index == 0) {
                        BankerBadge()
                        RebuyBadge(count = 2)
                    }
                }
                Text(
                    text = meta,
                    style = type.ledgerTight,
                    color = colors.ledger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    AmountText(text = MoneyFormat.signedRub(-125_400L), style = type.amountHero, color = colors.owe)
}

@Composable
private fun Scoreboard() {
    TableScoreboard(state = PreviewScoreboardState.copy(bankerOwes = 300L), onOvalClick = {}, onCellClick = {})
}

@Composable
private fun Keypad() {
    ChipDenominationRow(currentAmount = 1_000L, onSelect = {})
    AmountKeypad(onKey = {})
}

@Composable
private fun Buttons() {
    IssueChipsButton(
        title = "Выдать 2 000 фишек",
        subtitle = "2 200 ₽ записать в долг",
        chipColor = FeltbookTheme.colors.chips.chip2000,
        onClick = {},
    )
    HoldToConfirmButton(title = "Выплатить 353 ₽", onConfirm = {})
    BrassButton(text = "Начать игру", onClick = {}, modifier = Modifier.fillMaxWidth())
    BrassButton(
        text = "Вернуться за стол",
        onClick = {},
        style = BrassButtonStyle.Flat52,
        trailingIcon = FeltbookIcons.ArrowForward,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        OutlineButton(text = "Отмена", onClick = {
        }, icon = FeltbookIcons.Close, modifier = Modifier.weight(weight = 1f))
        DangerOutlineButton(text = "Не заплатил", onClick = {}, modifier = Modifier.weight(weight = 1f))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BrassButton(text = "Погасить", onClick = {}, style = BrassButtonStyle.Flat48)
        BrassButton(text = "Недоступно", onClick = {}, style = BrassButtonStyle.Flat48, enabled = false)
        FeltIconButton(icon = FeltbookIcons.MoreVert, contentDescription = "Меню игры", onClick = {})
        ShareButton(contentDescription = "Поделиться", onClick = {})
        DeleteActionChip(text = "Удалить", onClick = {})
    }
}

@Composable
private fun Panels() {
    ResultPanel(
        label = "Банкир выплачивает",
        amount = MoneyFormat.signedRub(353L),
        contentDescription = "Банкир выплачивает 353 рубля",
        variant = ResultVariant.Pay,
    )
    ResultPanel(
        label = "Влад доплачивает банкиру",
        amount = MoneyFormat.rub(1_053L),
        contentDescription = "Влад доплачивает 1 053 рубля",
        variant = ResultVariant.Owe,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(space = 8.dp)) {
        MetricTile(
            label = "Долг",
            value = MoneyFormat.rub(3_400L),
            contentDescription = "Долг 3 400 рублей",
            variant = MetricVariant.Owe,
            modifier = Modifier.weight(weight = 1f),
        )
        MetricTile(
            label = "♥ Банкиру",
            value = MoneyFormat.rub(800L),
            footnote = "сборы 300 · чаевые 53",
            contentDescription = "Банкиру 800 рублей",
            variant = MetricVariant.Brass,
            size = MetricSize.XL,
            modifier = Modifier.weight(weight = 1f),
        )
    }
    InfoBanner(text = "Фишки сошлись", variant = BannerVariant.Ok)
    InfoBanner(
        text = "${MoneyFormat.number(20_000)} фишек — точно? Обычно здесь берут ${MoneyFormat.number(2_000)}",
        variant = BannerVariant.Warn,
    )
    BreakdownList(
        rows =
            listOf(
                BreakdownRow("Фишки", MoneyFormat.number(2_000L)),
                BreakdownRow("Рейк 10 %", MoneyFormat.number(200L)),
                BreakdownRow("Сбор банкира", MoneyFormat.number(100L)),
            ),
        total = BreakdownRow("Итого", MoneyFormat.rub(2_300L)),
    )
}
