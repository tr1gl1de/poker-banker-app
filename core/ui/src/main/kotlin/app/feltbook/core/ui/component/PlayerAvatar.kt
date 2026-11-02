package app.feltbook.core.ui.component

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.R
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground
import java.text.BreakIterator

/** Масть: значок раздела и аватара. Красные — ♥ ♦. */
enum class Suit(@StringRes val glyph: Int, val isRed: Boolean) {
    Spades(R.string.suit_spades, isRed = false),
    Hearts(R.string.suit_hearts, isRed = true),
    Clubs(R.string.suit_clubs, isRed = false),
    Diamonds(R.string.suit_diamonds, isRed = true),
    ;

    companion object {
        private val Order = listOf(Spades, Hearts, Clubs, Diamonds)

        /** За столом — по порядку посадки: ♠ ♥ ♣ ♦ (m3-ui-spec §4.1). */
        fun forSeat(index: Int): Suit = Order[Math.floorMod(index, Order.size)]

        /** Вне стола (статистика, долги) — стабильно от канонического имени. */
        fun forName(name: String): Suit = Order[Math.floorMod(name.hashCode(), Order.size)]
    }
}

/** Размеры карты-аватара (m3-ui-spec §4.1). */
enum class AvatarSize(val width: Dp, val height: Dp) {
    /** Шапка карточки игрока. */
    L(width = 44.dp, height = 60.dp),

    /** Строки стола, шторки, долги. */
    M(width = 34.dp, height = 46.dp),

    /** Строки итога. */
    S(width = 30.dp, height = 40.dp),

    /** Статистика. */
    XS(width = 28.dp, height = 38.dp),
}

/** Инициал — первая графема имени в верхнем регистре; пустое имя — пустая строка. */
fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(trimmed)
    val end = iterator.next().takeIf { it != BreakIterator.DONE } ?: trimmed.length
    return trimmed.substring(0, end).uppercase()
}

/**
 * Карта-аватар игрока: `ivory`, инициал Playfair + масть. [active] = false — игрок вышел (`cardInactive`).
 * Декоративный для TalkBack: имя всегда стоит рядом текстом.
 */
@Composable
fun PlayerAvatar(
    name: String,
    suit: Suit,
    modifier: Modifier = Modifier,
    size: AvatarSize = AvatarSize.M,
    active: Boolean = true,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    val shapes = FeltbookTheme.shapes
    val (initialStyle: TextStyle, suitStyle: TextStyle, shape: Shape) =
        when (size) {
            AvatarSize.L -> Triple(type.avatarInitialL, type.suitL, shapes.avatarL)
            AvatarSize.M -> Triple(type.avatarInitialM, type.suitM, shapes.avatarM)
            AvatarSize.S -> Triple(type.avatarInitialS, type.suitS, shapes.avatarS)
            AvatarSize.XS -> Triple(type.avatarInitialXS, type.suitXS, shapes.avatarS)
        }
    val background = if (active) colors.ivory else colors.cardInactive
    val ink =
        when {
            !active -> colors.inkSoft
            suit.isRed -> colors.suitRed
            else -> colors.suitBlack
        }
    Box(
        modifier =
            modifier
                .size(width = size.width, height = size.height)
                .background(color = background, shape = shape)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        // Карта фиксированного размера и декоративная: кегль не растёт с системным масштабом шрифта.
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = initialOf(name), style = initialStyle, color = ink, maxLines = 1)
                Text(text = stringResource(suit.glyph), style = suitStyle, color = ink, maxLines = 1)
            }
        }
    }
}

@Preview
@Composable
private fun PlayerAvatarPreview() {
    FeltbookTheme {
        Row(
            modifier = Modifier.feltBackground().padding(all = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayerAvatar(name = "Дима", suit = Suit.Spades, size = AvatarSize.L)
            PlayerAvatar(name = "Борис", suit = Suit.Hearts)
            PlayerAvatar(name = "андрей", suit = Suit.Clubs, size = AvatarSize.S)
            PlayerAvatar(name = "Егор", suit = Suit.Diamonds, size = AvatarSize.XS)
            PlayerAvatar(name = "Саша", suit = Suit.Spades, active = false)
        }
    }
}
