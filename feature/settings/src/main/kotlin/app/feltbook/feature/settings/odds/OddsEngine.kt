package app.feltbook.feature.settings.odds

import app.feltbook.core.odds.EquityCalculator
import app.feltbook.core.odds.EquityRequest
import app.feltbook.core.odds.EquityResult
import app.feltbook.core.odds.EquitySettings
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

/**
 * Обёртка над [EquityCalculator] для ViewModel: расчёт вне главного потока ([dispatcher], по умолчанию
 * `Dispatchers.Default`), 150 000 раздач пачками по 10 000. В тестах — фиксированное зерно и тестовый диспетчер.
 */
class OddsEngine(val settings: EquitySettings, val dispatcher: CoroutineDispatcher) {
    @Inject
    constructor() : this(EquitySettings(), Dispatchers.Default)

    fun equity(request: EquityRequest): Flow<EquityResult> = EquityCalculator.equity(request, settings, dispatcher)
}
