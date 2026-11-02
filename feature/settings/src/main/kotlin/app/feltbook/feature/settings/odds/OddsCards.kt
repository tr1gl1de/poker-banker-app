package app.feltbook.feature.settings.odds

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.odds.Card
import app.feltbook.core.odds.Rank
import app.feltbook.core.odds.Suit
import app.feltbook.core.ui.R as UiR
import app.feltbook.core.ui.component.dashedBorder
import app.feltbook.core.ui.component.feltShadow
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.feature.settings.R

/** Масти в порядке сетки выбора и массива `odds_suit_names_genitive`: ♠ ♥ ♦ ♣. */
internal val SuitOrder: List<Suit> = listOf(Suit.SPADES, Suit.HEARTS, Suit.DIAMONDS, Suit.CLUBS)

/** Ранги сетки сверху вниз: туз … двойка. */
internal val RankOrder: List<Rank> = Rank.entries.reversed()

@get:StringRes
internal val Suit.glyph: Int
    get() = when (this) {
        Suit.SPADES -> UiR.string.suit_spades
        Suit.HEARTS -> UiR.string.suit_hearts
        Suit.DIAMONDS -> UiR.string.suit_diamonds
        Suit.CLUBS -> UiR.string.suit_clubs
    }

internal val Suit.isRed: Boolean get() = this == Suit.HEARTS || this == Suit.DIAMONDS

@Composable
internal fun suitColor(suit: Suit): Color =
    if (suit.isRed) FeltbookTheme.colors.suitRed else FeltbookTheme.colors.suitBlack

/** «A», «K», «10», «9». */
@Composable
internal fun rankSymbol(rank: Rank): String =
    if (rank == Rank.TEN) stringResource(R.string.odds_rank_ten) else rank.symbol.toString()

/** Для TalkBack: «Туз пик», «Семёрка червей». */
@Composable
internal fun cardName(card: Card): String {
    val ranks = stringArrayResource(R.array.odds_rank_names)
    val suits = stringArrayResource(R.array.odds_suit_names_genitive)
    return stringResource(R.string.odds_card_name, ranks[card.rank.ordinal], suits[SuitOrder.indexOf(card.suit)])
}

/** Короткая запись «A♠», «10♥». */
@Composable
internal fun cardShort(card: Card): String =
    stringResource(R.string.odds_card_short, rankSymbol(card.rank), stringResource(card.suit.glyph))

/** Размеры игральной карты (m3-ui-spec §5.14): L — свои карты, S — стол. */
internal enum class PlayingCardSize(val width: Dp, val height: Dp, val radius: Dp) {
    L(width = 52.dp, height = 72.dp, radius = 8.dp),
    S(width = 36.dp, height = 50.dp, radius = 6.dp),
}

/** Кегль карт не растёт с масштабом шрифта: карта фиксированного размера, имя — в TalkBack (как `PlayerAvatar`). */
@Composable
internal fun FixedFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f), content = content)
}

/** Открытая карта: `ivory`, ранг Playfair + масть; нажатие — заменить или убрать карту. */
@Composable
internal fun PlayingCard(card: Card, size: PlayingCardSize, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shape = RoundedCornerShape(size.radius)
    val name = cardName(card)
    val changeLabel = stringResource(R.string.odds_change_card)
    val (rankStyle, suitStyle) = when (size) {
        PlayingCardSize.L -> type.cardRankL to type.suitXL
        PlayingCardSize.S -> type.cardRankBoard to type.suitBoard
    }
    Box(
        modifier =
            modifier
                .then(if (size == PlayingCardSize.L) Modifier.feltShadow(shape, 4.dp, 10.dp, CARD_SHADOW) else Modifier)
                .size(width = size.width, height = size.height)
                .clip(shape)
                .background(color = colors.ivory)
                .clickable(role = Role.Button, onClickLabel = changeLabel, onClick = onClick)
                .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        CardFace(card = card, rankStyle = rankStyle, suitStyle = suitStyle)
    }
}

@Composable
internal fun CardFace(card: Card, rankStyle: TextStyle, suitStyle: TextStyle, modifier: Modifier = Modifier) {
    val ink = suitColor(card.suit)
    FixedFontScale {
        Column(
            modifier = modifier.clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = rankSymbol(card.rank), style = rankStyle, color = ink, maxLines = 1)
            Text(text = stringResource(card.suit.glyph), style = suitStyle, color = ink, maxLines = 1)
        }
    }
}

/**
 * Пустое место под карту: пунктир 1,5. [enabled] — «SlotNext» (`placeholderBorder`, «+» `inkSoft`);
 * иначе «SlotLater» (бледный пунктир, недоступен).
 */
@Composable
internal fun CardSlotBox(
    width: Dp,
    height: Dp,
    radius: Dp,
    label: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = FeltbookTheme.typography.suitTab,
) {
    val colors = FeltbookTheme.colors
    val shape: Shape = RoundedCornerShape(radius)
    Box(
        modifier =
            modifier
                .size(width = width, height = height)
                .dashedBorder(
                    color = if (enabled) colors.placeholderBorder else colors.placeholderBorderDisabled,
                    radius = radius,
                    width = 1.5.dp,
                ).clip(shape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FixedFontScale {
            Text(
                text = label,
                style = labelStyle,
                color = if (enabled) colors.inkSoft else colors.inkSoftDisabled,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

private const val CARD_SHADOW = 0.35f
