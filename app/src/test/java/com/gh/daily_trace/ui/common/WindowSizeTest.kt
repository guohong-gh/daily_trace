package com.gh.daily_trace.ui.common

import org.junit.Assert.assertEquals
import org.junit.Test

class WindowSizeTest {
    @Test
    fun `官方断点划分窗口宽度`() {
        assertEquals(AppWindowSize.COMPACT, AppWindowSize.fromWidthDp(0f))
        assertEquals(AppWindowSize.COMPACT, AppWindowSize.fromWidthDp(599f))
        assertEquals(AppWindowSize.MEDIUM, AppWindowSize.fromWidthDp(600f))
        assertEquals(AppWindowSize.MEDIUM, AppWindowSize.fromWidthDp(839f))
        assertEquals(AppWindowSize.EXPANDED, AppWindowSize.fromWidthDp(840f))
        assertEquals(AppWindowSize.EXPANDED, AppWindowSize.fromWidthDp(1280f))
    }

    @Test
    fun `内容宽度达到 640dp 后使用双列`() {
        assertEquals(1, adaptiveGridColumns(0f))
        assertEquals(1, adaptiveGridColumns(639f))
        assertEquals(2, adaptiveGridColumns(640f))
        assertEquals(2, adaptiveGridColumns(1000f))
    }
}
