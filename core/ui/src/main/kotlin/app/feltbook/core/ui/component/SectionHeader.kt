package app.feltbook.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.feltbook.core.ui.theme.FeltbookTheme
import app.feltbook.core.ui.theme.feltBackground

/** Отступы заголовка-строки: `p(18 20 8)`; на Главной — `p(22 20 8)`. */
val SectionHeaderPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 8.dp)

/**
 * Заголовок-строка (m3-ui-spec §4.16): слева `sectionTitle`, справа подпись [trailing] (`caption muted`)
 * или ссылка ([trailing] + [onTrailingClick], `chip brass`, нажата — `brassLight`).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    onTrailingClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = SectionHeaderPadding,
) {
    val colors = FeltbookTheme.colors
    val type = FeltbookTheme.typography
    Row(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(space = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = type.sectionTitle,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(weight = 1f).semantics { heading() },
        )
        if (trailing != null) {
            if (onTrailingClick != null) {
                val interaction = remember { MutableInteractionSource() }
                val pressed by interaction.collectIsPressedAsState()
                Text(
                    text = trailing,
                    style = type.chip,
                    color = if (pressed) colors.brassLight else colors.brass,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClick = onTrailingClick,
                            ),
                )
            } else {
                Text(text = trailing, style = type.caption, color = colors.muted, maxLines = 1)
            }
        }
    }
}

/** Надзаголовок-гроссбух «ОКТЯБРЬ 2026»: `ledgerCaps ledger`, uppercase; отступ сверху 12 / 14 / 18. */
@Composable
fun LedgerHeader(text: String, modifier: Modifier = Modifier, topPadding: Dp = 18.dp) {
    Text(
        text = text.uppercase(),
        style = FeltbookTheme.typography.ledgerCaps,
        color = FeltbookTheme.colors.ledger,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
            modifier
                .padding(start = 20.dp, end = 20.dp, top = topPadding, bottom = 8.dp)
                .semantics { heading() },
    )
}

@Preview
@Composable
private fun SectionHeaderPreview() {
    FeltbookTheme {
        Column(Modifier.feltBackground().width(width = 390.dp)) {
            SectionHeader(title = "Прошлые игры", trailing = "Вся история", onTrailingClick = {})
            SectionHeader(title = "Игроки", trailing = "5 за столом · 1 вышел")
            LedgerHeader(text = "Октябрь 2026", topPadding = 14.dp)
        }
    }
}
