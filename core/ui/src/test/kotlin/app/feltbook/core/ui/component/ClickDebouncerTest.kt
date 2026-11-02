package app.feltbook.core.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickDebouncerTest {
    @Test
    fun repeatWithinWindowIsIgnored() {
        val debouncer = ClickDebouncer(windowMillis = 600)
        assertTrue(debouncer.tryAccept(1_000))
        assertFalse(debouncer.tryAccept(1_000))
        assertFalse(debouncer.tryAccept(1_599))
        assertTrue(debouncer.tryAccept(1_600))
        assertFalse(debouncer.tryAccept(2_100))
    }

    @Test
    fun ignoredClicksDoNotExtendWindow() {
        val debouncer = ClickDebouncer(windowMillis = 600)
        assertTrue(debouncer.tryAccept(0))
        assertFalse(debouncer.tryAccept(500))
        assertTrue(debouncer.tryAccept(600))
    }

    @Test
    fun clockGoingBackwardsIsAccepted() {
        val debouncer = ClickDebouncer(windowMillis = 600)
        assertTrue(debouncer.tryAccept(10_000))
        assertTrue(debouncer.tryAccept(5_000))
    }
}
