package app.feltbook.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

// Пользовательские сценарии для профиля и бенчмарков. Селекторы — по русским текстам из strings.xml
// (:feature:game); поменялся текст — правьте константы ниже.

private const val TIMEOUT_MS = 5_000L
private const val SHORT_TIMEOUT_MS = 1_500L

/** Пауза > 600 мс: защита от двойного нажатия (CLAUDE.md, правило 6) игнорирует быстрые повторы. */
private const val DEBOUNCE_MS = 700L

private const val HOME_NEW_GAME = "Новая игра"
private const val HOME_BACK_TO_TABLE = "Вернуться за стол"
private const val HOME_ALREADY_RUNNING_GO = "К столу"
private const val NEW_GAME_START = "Начать игру"
private const val TABLE_PLAYERS = "Игроки"
private const val TABLE_ADD_PLAYER_DESC = "Добавить игрока"
private const val TABLE_FAB_PLAYER = "Игрок"
private const val NEW_PLAYER_TITLE = "Новый игрок"
private const val NEW_PLAYER_FIRST_BUY_IN = "Первый закуп"
private const val BUY_IN_ISSUE = "Выдать"
private const val BANKER_NAME = "Андрей"

/** Холодный старт до первого кадра Главной. */
fun MacrobenchmarkScope.startToHome() {
    pressHome()
    startActivityAndWait()
    device.wait(Until.hasObject(By.textContains(HOME_NEW_GAME)), TIMEOUT_MS)
}

/** Главная → стол: идущая игра — «Вернуться за стол», иначе «Новая игра» → «Начать игру». */
fun MacrobenchmarkScope.openTable() {
    // Карточка идущей игры появляется после чтения БД — короткое ожидание.
    device.wait(Until.findObject(By.textContains(HOME_BACK_TO_TABLE)), SHORT_TIMEOUT_MS)?.let {
        it.click()
        device.waitForTable()
        return
    }
    device.require(By.textContains(HOME_NEW_GAME)).click()
    // Игра уже идёт (данные остались с прошлого прогона) — диалог «Уже идёт игра» → «К столу».
    if (device.wait(Until.hasObject(By.text(HOME_ALREADY_RUNNING_GO)), SHORT_TIMEOUT_MS)) {
        device.require(By.text(HOME_ALREADY_RUNNING_GO)).click()
        device.waitForTable()
        return
    }
    device.wait(Until.hasObject(By.textContains(NEW_GAME_START)), TIMEOUT_MS)
    // Поля: «Название» (заполнено по умолчанию) и «Банкир» (пусто на чистых данных).
    device.findObjects(By.clazz("android.widget.EditText"))
        .firstOrNull { it.text.isNullOrBlank() }
        ?.let { it.text = BANKER_NAME }
    device.scrollTo(By.textContains(NEW_GAME_START)).click()
    device.waitForTable()
}

/** Новый игрок с первым закупом 1 000 фишек: имя → «Первый закуп» → клавиатура «1», «000» → «Выдать…». */
fun MacrobenchmarkScope.addPlayer(name: String) {
    val fab = device.findObject(By.desc(TABLE_ADD_PLAYER_DESC)) ?: device.require(By.text(TABLE_FAB_PLAYER))
    fab.click()
    device.wait(Until.hasObject(By.text(NEW_PLAYER_TITLE)), TIMEOUT_MS)
    device.require(By.clazz("android.widget.EditText")).text = name
    device.require(By.textContains(NEW_PLAYER_FIRST_BUY_IN)).click()
    device.key(tag = "keypad_1", label = "1").click()
    device.key(tag = "keypad_000", label = "000").click()
    Thread.sleep(DEBOUNCE_MS)
    device.require(By.textContains(BUY_IN_ISSUE)).click()
    // Шторка закрылась — снова виден стол.
    device.wait(Until.gone(By.text(NEW_PLAYER_TITLE)), TIMEOUT_MS)
    Thread.sleep(DEBOUNCE_MS)
}

/** Прокрутка списка игроков вниз и обратно (флинг — как пальцем; кадры стола с 20 игроками). */
fun MacrobenchmarkScope.scrollPlayers(times: Int = 2) {
    val list = device.findObjects(By.scrollable(true)).maxByOrNull { it.visibleBounds.height() } ?: return
    // Отступ жеста от краёв: не задеть системную навигацию и не открыть шторку уведомлений.
    list.setGestureMargin(device.displayHeight / GESTURE_MARGIN_DIVISOR)
    repeat(times) {
        list.fling(Direction.DOWN)
        device.waitForIdle()
        list.fling(Direction.UP)
        device.waitForIdle()
    }
}

private const val GESTURE_MARGIN_DIVISOR = 5

private fun UiDevice.waitForTable() {
    check(wait(Until.hasObject(By.textStartsWith(TABLE_PLAYERS)), TIMEOUT_MS)) { "Стол не открылся" }
}

private fun UiDevice.require(selector: BySelector): UiObject2 =
    wait(Until.findObject(selector), TIMEOUT_MS) ?: error("Не найден элемент: $selector")

/**
 * Клавиша AmountKeypad: по testTag (если в корне включён `testTagsAsResourceId`), иначе по тексту клавиши.
 */
private fun UiDevice.key(tag: String, label: String): UiObject2 = findObject(By.res(tag)) ?: require(By.text(label))

/** Элемент ниже экрана (кнопка под формой, поднятая клавиатура) — прокрутить к нему. */
private fun UiDevice.scrollTo(selector: BySelector): UiObject2 = wait(Until.findObject(selector), SHORT_TIMEOUT_MS)
    ?: findObjects(By.scrollable(true)).firstNotNullOfOrNull {
        it.scrollUntil(Direction.DOWN, Until.findObject(selector))
    }
    ?: require(selector)
