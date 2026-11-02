package app.feltbook.buildlogic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder

/**
 * Unit-тесты (в т. ч. Roborazzi) — только на debug: release отличается лишь R8. То же для сборок плагина
 * baselineprofile (`nonMinifiedRelease`, `benchmarkRelease`) — иначе `./gradlew test` гонял бы тесты :app трижды.
 */
internal fun disableReleaseUnitTests(components: AndroidComponentsExtension<*, *, *>) {
    components.beforeVariants(components.selector().all()) { variant ->
        if (variant.buildType == "debug") return@beforeVariants
        (variant as? HasHostTestsBuilder)
            ?.hostTests
            ?.get(HostTestBuilder.UNIT_TEST_TYPE)
            ?.enable = false
    }
}
