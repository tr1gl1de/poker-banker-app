# Feltbook — правила R8 для release (и benchmarkRelease плагина baselineprofile).
# kotlinx.serialization, Room, Hilt, Compose, Navigation и Play Services поставляют собственные consumer-правила;
# здесь — страховка для кода приложения: то, что вызывается рефлексией и не должно пропасть при минификации.
# Проверка: CI job release-smoke (build.yml) ставит минифицированный release на эмулятор и проходит
# Главная → Новая игра → стол; любое «Serializer for class … is not found» роняет процесс на первом же экране.

# Номера строк для читаемых stack trace из отчётов пользователей (mapping.txt — в GitHub Release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---------- kotlinx.serialization ----------
# Пакет .feltbook (DTO :core:domain/transfer, ControlMessage :core:transfer), сохранение ввода стола и
# типобезопасные маршруты Navigation (data object/data class в app и feature/*). Navigation находит сериализатор
# маршрута через KClass.serializer() — рефлексией: Companion.serializer() у классов, INSTANCE.serializer() у
# object. Имена классов могут обфусцироваться: route-строка берётся из serialName дескриптора, не из имени класса.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,Signature,EnclosingMethod

-keepclassmembers @kotlinx.serialization.Serializable class app.feltbook.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# serializer() на companion (в т. ч. именованном) @Serializable-класса.
-if @kotlinx.serialization.Serializable class app.feltbook.**
-keepclassmembers class <1>$* {
    kotlinx.serialization.KSerializer serializer(...);
}
# Сгенерированные сериализаторы ($$serializer) и их дескрипторы.
-keepclassmembers class app.feltbook.**$$serializer {
    *** INSTANCE;
    *** descriptor;
}
-keepclasseswithmembers,allowobfuscation class app.feltbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- enum ----------
# Room-конвертеры (Converters.kt: GameStatus/EntryType/BankerFeeMode/RakeMode.valueOf) и enum в DTO.
-keepclassmembers enum app.feltbook.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static kotlin.enums.EnumEntries getEntries();
}

# ---------- Room ----------
# Room.databaseBuilder(FeltbookDatabase::class.java) находит FeltbookDatabase_Impl по имени класса.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keepnames class * extends androidx.room.RoomDatabase

# ---------- Hilt ----------
# Сгенерированные компоненты Hilt и @HiltViewModel покрыты правилами dagger/hilt-android; своих не нужно.

# ---------- Nearby Connections ----------
# play-services-nearby/basement поставляют consumer-правила. Payload передаётся байтами (наш формат .feltbook),
# Parcelable/рефлексии в нашем коде нет.

# ---------- kotlin.uuid / kotlin.time ----------
# Uuid и Instant — обычные классы stdlib без рефлексии; сериализуются собственными сериализаторами (строкой).
