package app.feltbook.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * Версия приложения.
 * - versionCode = число коммитов в HEAD (`git rev-list --count HEAD`), иначе `GITHUB_RUN_NUMBER`, иначе 1.
 * - versionName (release) = `feltbook.versionName` из gradle.properties; при сборке с тега
 *   (`-Pfeltbook.releaseTag=vX.Y.Z`) тег обязан совпадать с базой.
 * - versionName (debug) = `<база>-dev.<versionCode>+<короткий sha>`.
 */
internal data class FeltbookVersion(
    val base: String,
    val code: Int,
    val shortSha: String,
) {
    val devSuffix: String get() = "-dev.$code+$shortSha"
    val devName: String get() = base + devSuffix
}

internal fun Project.feltbookVersion(): FeltbookVersion {
    val base =
        providers.gradleProperty("feltbook.versionName").orNull
            ?: throw GradleException("feltbook.versionName не задан в gradle.properties")
    val tag = providers.gradleProperty("feltbook.releaseTag").orNull?.takeIf { it.isNotBlank() }
    if (tag != null && tag.removePrefix("v") != base) {
        throw GradleException("Тег $tag не совпадает с feltbook.versionName=$base (gradle.properties)")
    }
    val code =
        git("rev-list", "--count", "HEAD")?.toIntOrNull()
            ?: providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
            ?: 1
    val sha = git("rev-parse", "--short=7", "HEAD") ?: "nogit"
    return FeltbookVersion(base = base, code = code, shortSha = sha)
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
