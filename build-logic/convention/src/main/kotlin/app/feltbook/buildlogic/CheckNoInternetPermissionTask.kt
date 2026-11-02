package app.feltbook.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/** Железное правило №4: в итоговом (merged) манифесте нет `android.permission.INTERNET`. */
@CacheableTask
abstract class CheckNoInternetPermissionTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    @get:OutputFile
    abstract val report: RegularFileProperty

    @TaskAction
    fun check() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
        val permissions =
            listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
                val nodes = document.getElementsByTagName(tag)
                (0 until nodes.length).map { index ->
                    (nodes.item(index) as Element).getAttributeNS(ANDROID_NS, "name")
                }
            }
        if (FORBIDDEN in permissions) {
            throw GradleException(
                "В итоговом манифесте есть $FORBIDDEN (${mergedManifest.get().asFile}). " +
                    "Приложение офлайн: найдите библиотеку, которая его добавляет, и уберите её.",
            )
        }
        report.get().asFile.writeText("OK: $FORBIDDEN отсутствует\n" + permissions.joinToString("\n") + "\n")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
        const val FORBIDDEN = "android.permission.INTERNET"
    }
}
