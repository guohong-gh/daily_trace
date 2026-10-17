package com.gh.daily_trace.ui.common

import com.gh.daily_trace.data.model.CheckItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 记录时间展示（设备时区） */
val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** 整数省略小数点：3.0 → "3"，2.5 → "2.5" */
fun formatValue(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.CHINA, "%.1f", v)

/** 「目标 30分钟」；无目标时显示「目标 -」 */
fun formatTarget(item: CheckItem): String {
    val target = item.targetValue?.let { formatValue(it) } ?: "-"
    return "目标 $target${item.unit}"
}
