package com.gh.daily_trace.domain.csv

import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * CSV 导出（M10-1）：全部打卡记录，一行一条，按逻辑日 / 创建时间 / ID 升序。
 *
 * 兼容 Excel：UTF-8 BOM + CRLF；文本按 RFC 4180 转义；对以 = + - @ 开头的
 * 用户文本加单引号，避免打开表格时被当作公式执行。
 */
object CsvExporter {

    private val HEADER = listOf(
        "日期",
        "记录时间",
        "选项",
        "状态",
        "数值",
        "单位",
        "备注",
        "来源",
        "补卡",
        "时区",
    )

    private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val FORMULA_PREFIXES = charArrayOf('=', '+', '-', '@')

    /** @param timezone 用于展示记录创建时刻的时区（与 AppSettings.timezone 一致） */
    fun buildRecordsCsv(records: List<CheckRecord>, timezone: String): String {
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
        val sorted = records.sortedWith(compareBy({ it.date }, { it.createdAt }, { it.id }))

        return buildString {
            append(BOM)
            append(HEADER.joinToString(",") { escape(it) })
            append(CRLF)
            sorted.forEach { record ->
                val fields = listOf(
                    LocalDate.ofEpochDay(record.date).toString(),
                    Instant.ofEpochMilli(record.createdAt).atZone(zone).format(TIME_FORMAT),
                    protect(record.itemNameSnapshot),
                    statusLabel(record.status),
                    formatValue(record.value),
                    protect(record.unit),
                    protect(record.note),
                    sourceLabel(record.source),
                    if (record.isBackfill) "是" else "否",
                    protect(record.timezone),
                )
                append(fields.joinToString(",") { escape(it) })
                append(CRLF)
            }
        }
    }

    private fun statusLabel(status: RecordStatus): String = when (status) {
        RecordStatus.DONE -> "完成"
        RecordStatus.PARTIAL -> "部分完成"
        RecordStatus.SKIPPED -> "跳过"
        RecordStatus.PLANNED -> "计划中"
    }

    private fun sourceLabel(source: RecordSource): String = when (source) {
        RecordSource.MANUAL -> "手动"
        RecordSource.WIDGET -> "小组件"
        RecordSource.NOTIFICATION -> "通知"
        RecordSource.IMPORT -> "导入"
    }

    /** 整数不带 .0；小数原样；空值留空 */
    private fun formatValue(value: Double?): String {
        if (value == null) return ""
        if (!value.isFinite()) return value.toString()
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }

    private fun protect(text: String): String =
        if (text.isNotEmpty() && text[0] in FORMULA_PREFIXES) "'$text" else text

    private fun escape(text: String): String {
        val needsQuote = text.any { it == ',' || it == '"' || it == '\r' || it == '\n' }
        return if (needsQuote) "\"${text.replace("\"", "\"\"")}\"" else text
    }

    private const val BOM = "\uFEFF"
    private const val CRLF = "\r\n"
}
