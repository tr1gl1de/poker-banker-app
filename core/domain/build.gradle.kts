import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Чистый Kotlin (KMP, сейчас jvm): модель, правила, движок расчёта. Без Android (ADR 0001).
plugins {
    alias(libs.plugins.feltbook.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions {
        // Uuid и kotlin.time.Instant — opt-in на уровне модуля (docs/plan/m1-domain.md §0).
        optIn.add("kotlin.uuid.ExperimentalUuidApi")
        optIn.add("kotlin.time.ExperimentalTime")
    }
    sourceSets {
        commonMain.dependencies {
            // Пакет .feltbook (ADR 0007).
            api(libs.kotlinx.serialization.json)
            // Только Flow в интерфейсах репозиториев.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }
    }
}

// Критерий M1: покрытие домена ≥ 95 % строк и ≥ 90 % ветвлений (koverVerify входит в check).
kover {
    reports {
        verify {
            rule("Покрытие строк") {
                minBound(95, CoverageUnit.LINE)
            }
            rule("Покрытие ветвлений") {
                minBound(90, CoverageUnit.BRANCH)
            }
        }
    }
}
