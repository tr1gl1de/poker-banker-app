package app.feltbook.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Генератор Baseline Profile (ТЗ §9: холодный старт ≤ 1 с, 60 fps на столе с 20 игроками).
 *
 *     ./gradlew :app:generateBaselineProfile
 *
 * Нужен телефон или эмулятор с API 33+ (или root на API 28+): иначе BaselineProfileRule профиль не соберёт.
 * Результат — app/src/release/generated/
 * baselineProfiles/{baseline-prof,startup-prof}.txt: коммитятся вместе с кодом, попадают в release APK/AAB.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Before
    fun cleanData() = clearTargetData()

    /** Только холодный старт → Главная: он же — startup profile (раскладка dex, `dexLayoutOptimization`). */
    @Test
    fun startup() = rule.collect(packageName = targetPackage, includeInStartupProfile = true) {
        startToHome()
    }

    /**
     * Главная → новая игра → стол; 20 игроков с закупом (первая итерация создаёт стол, следующие добавляют
     * по одному) и прокрутка списка — горячий путь банкира во время игры.
     */
    @Test
    fun tableJourney() {
        var iteration = 0
        rule.collect(packageName = targetPackage, includeInStartupProfile = false) {
            startToHome()
            openTable()
            val toAdd = if (iteration == 0) SEED_PLAYERS else 1
            repeat(toAdd) { addPlayer(name = "Игрок ${iteration * SEED_PLAYERS + it + 1}") }
            scrollPlayers()
            iteration++
        }
    }

    private companion object {
        const val SEED_PLAYERS = 20
    }
}
