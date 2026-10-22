package com.gh.daily_trace.data.model

/** 近期使用次数（B19「近期常用」）：最近一段日期区间内某选项的记录条数 */
data class ItemUsageCount(
    val itemId: Long,
    val useCount: Int,
)
