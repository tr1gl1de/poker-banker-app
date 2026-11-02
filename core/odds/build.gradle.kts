import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Пасхалка «Калькулятор шансов»: чистый Kotlin (KMP, сейчас jvm), без Android (ADR 0001).
plugins {
    alias(libs.plugins.feltbook.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

// Как у домена: ≥ 95 % строк (koverVerify входит в check). Ветвления — с появлением кода на M6.
kover {
    reports {
        verify {
            rule("Покрытие строк") {
                minBound(95, CoverageUnit.LINE)
            }
        }
    }
}
