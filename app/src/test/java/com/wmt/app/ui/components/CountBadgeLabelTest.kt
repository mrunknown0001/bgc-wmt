package com.wmt.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class CountBadgeLabelTest {

    @Test
    fun `counts up to 99 are shown as they are`() {
        assertEquals("1", countBadgeLabel(1))
        assertEquals("42", countBadgeLabel(42))
        assertEquals("99", countBadgeLabel(99))
    }

    @Test
    fun `anything over 99 reads 99+, not a plain 99`() {
        assertEquals("99+", countBadgeLabel(100))
        assertEquals("99+", countBadgeLabel(1_250))
    }
}
