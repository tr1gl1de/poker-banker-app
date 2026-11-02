package app.feltbook.buildlogic

import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder

/** Unit-тесты (в т. ч. Roborazzi) — только на debug: release отличается лишь R8. */
internal fun disableReleaseUnitTests(components: AndroidComponentsExtension<*, *, *>) {
    components.beforeVariants(components.selector().withBuildType("release")) { variant ->
        (variant as? com.android.build.api.variant.HasHostTestsBuilder)
            ?.hostTests
            ?.get(HostTestBuilder.UNIT_TEST_TYPE)
            ?.enable = false
    }
}
