package app.feltbook.core.data.db

import androidx.room.TypeConverter
import app.feltbook.core.domain.model.BankerFeeMode
import app.feltbook.core.domain.model.EntryType
import app.feltbook.core.domain.model.GameStatus
import app.feltbook.core.domain.model.RakeMode
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * UUID ↔ TEXT (`Uuid.toString()`), Instant ↔ INTEGER (epoch ms), перечисления ↔ TEXT (имя константы).
 * Перечисления — явным маппингом по имени: переименование константы в домене требует миграции схемы.
 */
@Suppress("TooManyFunctions") // По паре функций на каждый хранимый тип.
internal object Converters {
    @TypeConverter
    fun uuidToText(value: Uuid): String = value.toString()

    @TypeConverter
    fun textToUuid(value: String): Uuid = Uuid.parse(value)

    @TypeConverter
    fun instantToMillis(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter
    fun millisToInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @TypeConverter
    fun gameStatusToText(value: GameStatus): String = value.name

    @TypeConverter
    fun textToGameStatus(value: String): GameStatus = GameStatus.valueOf(value)

    @TypeConverter
    fun entryTypeToText(value: EntryType): String = value.name

    @TypeConverter
    fun textToEntryType(value: String): EntryType = EntryType.valueOf(value)

    @TypeConverter
    fun bankerFeeModeToText(value: BankerFeeMode): String = value.name

    @TypeConverter
    fun textToBankerFeeMode(value: String): BankerFeeMode = BankerFeeMode.valueOf(value)

    @TypeConverter
    fun rakeModeToText(value: RakeMode): String = value.name

    @TypeConverter
    fun textToRakeMode(value: String): RakeMode = RakeMode.valueOf(value)
}
