package app.feltbook.core.ui.format

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.feltbook.core.ui.R

/**
 * Суммы для TalkBack со знаком и словом (m3-ui-spec §11.17): «353 рубля», «минус 3 400 рублей»,
 * «Банкир выплачивает 353 рубля». Цвет никогда не единственный носитель смысла.
 */
fun Resources.a11yRubles(amount: Long): String {
    val abs = MoneyFormat.number(amount).removePrefix(MoneyFormat.MINUS.toString())
    val words = getQuantityString(R.plurals.a11y_rubles, pluralQuantity(amount), abs)
    return if (amount < 0) getString(R.string.a11y_minus, words) else words
}

/** «Банкир выплачивает 353 рубля». */
fun Resources.a11yBankerPays(amount: Long): String = getString(R.string.a11y_banker_pays, a11yRubles(amount))

/** «Влад доплачивает 1 053 рубля». */
fun Resources.a11yPlayerPays(name: String, amount: Long): String =
    getString(R.string.a11y_player_pays, name, a11yRubles(amount))

/** «Долг 1 053 рубля». */
fun Resources.a11yDebt(amount: Long): String = getString(R.string.a11y_debt, a11yRubles(amount))

/** «Влад должен 1 053 рубля». */
fun Resources.a11yOwesBanker(name: String, amount: Long): String =
    getString(R.string.a11y_owes_banker, name, a11yRubles(amount))

/** Сумма со знаком словом: «плюс 353 рубля», «минус 500 рублей», «0 рублей». */
fun Resources.a11ySignedRubles(amount: Long): String =
    if (amount > 0) getString(R.string.a11y_plus, a11yRubles(amount)) else a11yRubles(amount)

@Composable
fun a11yRubles(amount: Long): String {
    val abs = MoneyFormat.number(amount).removePrefix(MoneyFormat.MINUS.toString())
    val words = pluralStringResource(R.plurals.a11y_rubles, pluralQuantity(amount), abs)
    return if (amount < 0) stringResource(R.string.a11y_minus, words) else words
}

@Composable
fun a11ySignedRubles(amount: Long): String =
    if (amount > 0) stringResource(R.string.a11y_plus, a11yRubles(amount)) else a11yRubles(amount)

@Composable
fun a11yBankerPays(amount: Long): String = stringResource(R.string.a11y_banker_pays, a11yRubles(amount))

@Composable
fun a11yPlayerPays(name: String, amount: Long): String =
    stringResource(R.string.a11y_player_pays, name, a11yRubles(amount))

@Composable
fun a11yDebt(amount: Long): String = stringResource(R.string.a11y_debt, a11yRubles(amount))

@Composable
fun a11yOwesBanker(name: String, amount: Long): String =
    stringResource(R.string.a11y_owes_banker, name, a11yRubles(amount))

@Composable
fun a11yEven(): String = stringResource(R.string.a11y_even)
