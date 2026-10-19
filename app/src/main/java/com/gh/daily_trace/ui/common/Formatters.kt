package com.gh.daily_trace.ui.common

import com.gh.daily_trace.data.model.CheckItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** 记录时间展示（设备时区） */
val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

/** 整数省略小数点：3.0 → "3"，2.5 → "2.5" */
fun formatValue(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.CHINA, "%.1f", v)

/** 分钟数友好展示：90 → "1 小时 30 分"，45 → "45 分钟"（不足 1 小时保留原始数值） */
fun formatMinutes(v: Double): String {
    if (v < 60) return "${formatValue(v)} 分钟"
    val total = v.roundToInt()
    val hours = total / 60
    val minutes = total % 60
    return if (minutes == 0) "$hours 小时" else "$hours 小时 $minutes 分"
}

/** 「目标 30分钟」；无目标时显示「目标 -」 */
fun formatTarget(item: CheckItem): String {
    val target = item.targetValue?.let { formatValue(it) } ?: "-"
    return "目标 $target${item.unit}"
}
