package com.gh.daily_trace.domain.search

/** 搜索日期范围预设（M9-1）。 */
enum class SearchDatePreset { ALL, LAST_7, LAST_30, CUSTOM }

/**
 * 搜索日期范围（逻辑日 epochDay，闭区间 `[from, to]`）。
 * null 表示该侧不限；两侧皆 null 即「不限」。
 */
data class SearchDateRange(val from: Long? = null, val to: Long? = null) {
    val active: Boolean get() = from != null || to != null
}

/**
 * 起止归一：两端都有且起 > 止时自动交换；单侧为空时保留单侧。
 * 自定义范围在 UI 确认日期时即调用，保证按钮展示与查询口径一致。
 */
fun normalizeSearchRange(from: Long?, to: Long?): SearchDateRange =
    if (from != null && to != null && from > to) {
        SearchDateRange(to, from)
    } else {
        SearchDateRange(from, to)
    }

/**
 * 预设 → 实际范围。
 * - ALL：不限
 * - LAST_7：`today-6 ~ today`（含今天共 7 天）
 * - LAST_30：`today-29 ~ today`（含今天共 30 天）
 * - CUSTOM：自定义起止，经 [normalizeSearchRange] 归一
 */
fun resolveSearchRange(
    preset: SearchDatePreset,
    today: Long,
    customFrom: Long? = null,
    customTo: Long? = null,
): SearchDateRange = when (preset) {
    SearchDatePreset.ALL -> SearchDateRange()
    SearchDatePreset.LAST_7 -> SearchDateRange(today - 6, today)
    SearchDatePreset.LAST_30 -> SearchDateRange(today - 29, today)
    SearchDatePreset.CUSTOM -> normalizeSearchRange(customFrom, customTo)
}
