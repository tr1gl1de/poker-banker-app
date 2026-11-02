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
