package app.feltbook.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Печатает версию для CI: `./gradlew -q :app:printVersion`. */
@DisableCachingByDefault(because = "Только печать")
abstract class PrintVersionTask : DefaultTask() {
    @get:Input
    abstract val versionCode: Property<Int>

    @get:Input
    abstract val releaseVersionName: Property<String>

    @get:Input
    abstract val devVersionName: Property<String>

    @TaskAction
    fun print() {
        println("versionCode=${versionCode.get()}")
        println("versionName=${releaseVersionName.get()}")
        println("devVersionName=${devVersionName.get()}")
    }
}
