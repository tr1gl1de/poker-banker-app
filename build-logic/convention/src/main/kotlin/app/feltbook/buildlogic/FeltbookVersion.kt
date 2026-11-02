package app.feltbook.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Версия приложения.
 * - versionCode = число коммитов в HEAD (`git rev-list --count HEAD`). Без git (архив исходников) — 1,
 *   но только для локальных/debug-сборок: при сборке с тега нужен git и полная (не shallow) история,
 *   иначе GradleException — versionCode релиза обязан быть монотонным.
 * - versionName (release) = `feltbook.versionName` из gradle.properties; при сборке с тега
 *   (`-Pfeltbook.releaseTag=vX.Y.Z`) тег обязан совпадать с базой.
 * - versionName (debug) = `<база>-dev.<versionCode>+<короткий sha>`.
 * - versionName (release автосборки из main, `-Pfeltbook.autoBuild=true`) = `<база>-build.<versionCode>`;
 *   как и релиз с тега, требует полной истории git.
 */
internal data class FeltbookVersion(
    val base: String,
    val code: Int,
    val shortSha: String,
    val autoBuild: Boolean = false,
) {
    val devSuffix: String get() = "-dev.$code+$shortSha"
    val devName: String get() = base + devSuffix

    /** versionName release-сборки: база (тег vX.Y.Z) или `<база>-build.<N>` (автосборка из main). */
    val releaseName: String get() = if (autoBuild) "$base-build.$code" else base
}

internal fun Project.feltbookVersion(): FeltbookVersion {
    val base =
        providers.gradleProperty("feltbook.versionName").orNull
            ?: throw GradleException("feltbook.versionName не задан в gradle.properties")
    val tag = providers.gradleProperty("feltbook.releaseTag").orNull?.takeIf { it.isNotBlank() }
    val autoBuild = providers.gradleProperty("feltbook.autoBuild").orNull.toBoolean()
    if (tag != null && autoBuild) {
        throw GradleException("feltbook.releaseTag и feltbook.autoBuild взаимоисключающие")
    }
    val label = tag ?: if (autoBuild) "автосборка" else null
    if (tag != null && tag.removePrefix("v") != base) {
        throw GradleException("Тег $tag не совпадает с feltbook.versionName=$base (gradle.properties)")
    }
    if (label != null) {
        when (git("rev-parse", "--is-shallow-repository")) {
            "false" -> Unit
            "true" -> throw GradleException(
                "Релиз $label из shallow-клона: versionCode неверен. Нужен полный клон (actions/checkout fetch-depth: 0)",
            )
            else -> throw GradleException("Релиз $label: git недоступен или каталог не git-репозиторий")
        }
    }
    val count = git("rev-list", "--count", "HEAD")?.toIntOrNull()
    if (label != null && count == null) {
        throw GradleException("Релиз $label: не удалось посчитать коммиты (git rev-list --count HEAD)")
    }
    val code = count ?: 1
    val sha = git("rev-parse", "--short=7", "HEAD") ?: "nogit"
    return FeltbookVersion(base = base, code = code, shortSha = sha, autoBuild = autoBuild)
}

private fun Project.git(vararg args: String): String? =
    runCatching {
        providers
            .exec {
                workingDir = rootDir
                commandLine(listOf("git") + args)
                isIgnoreExitValue = true
            }.standardOutput.asText
            .get()
            .trim()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
