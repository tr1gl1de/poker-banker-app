# Feltbook — правила R8 для release.
# kotlinx.serialization, Room, Hilt и Compose поставляют собственные consumer-правила в своих артефактах;
# здесь — только то, что относится к коду приложения.

# Сохраняем номера строк для читаемых stack trace из отчётов пользователей.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: сериализаторы @Serializable-классов проекта (пакет передачи .feltbook, маршруты).
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class app.feltbook.** {
    static <fields>;
    *** Companion;
}
-keepclasseswithmembers class app.feltbook.** {
    kotlinx.serialization.KSerializer serializer(...);
}
