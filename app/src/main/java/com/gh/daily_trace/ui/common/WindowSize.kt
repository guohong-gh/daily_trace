package com.gh.daily_trace.ui.common

import androidx.compose.ui.unit.dp

/**
 * 按 Material 3 官方断点划分窗口宽度：
 * compact < 600dp，medium 600–839dp，expanded >= 840dp。
 *
 * 直接用 [androidx.compose.foundation.layout.BoxWithConstraints] 计算，
 * 不引入额外的 window-size-class 依赖。
 */
enum class AppWindowSize {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        fun fromWidthDp(widthDp: Float): AppWindowSize = when {
            widthDp < 600f -> COMPACT
            widthDp < 840f -> MEDIUM
            else -> EXPANDED
        }
    }
}

/** 内容宽度达到 640dp 后，列表卡片改为双列；手机保持单列。 */
fun adaptiveGridColumns(widthDp: Float): Int = if (widthDp >= 640f) 2 else 1

/** 平板上的内容最大宽度，避免单列文本与表单被拉得过长。 */
val ContentMaxWidth = 1000.dp
