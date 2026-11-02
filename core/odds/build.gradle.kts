import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

// Пасхалка «Калькулятор шансов»: чистый Kotlin (KMP, сейчас jvm), без Android (ADR 0001).
plugins {
    alias(libs.plugins.feltbook.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Flow и CoroutineDispatcher — в публичном API EquityCalculator.
            api(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Как у домена: ≥ 95 % строк (koverVerify входит в check).
kover {
    reports {
        verify {
            rule("Покрытие строк") {
                minBound(95, CoverageUnit.LINE)
            }
        }
    }
}
