// Чистый Kotlin (KMP, сейчас jvm): модель, правила, движок расчёта. Без Android (ADR 0001).
plugins {
    alias(libs.plugins.feltbook.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.core)
            // Только Flow в интерфейсах репозиториев.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

// Критерий M1: покрытие домена ≥ 95 % (koverVerify входит в check).
kover {
    reports {
        verify {
            rule {
                minBound(95)
            }
        }
    }
}
