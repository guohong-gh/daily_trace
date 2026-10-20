package com.gh.daily_trace.domain.csv

import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** M10-1 CSV 导出：Excel 兼容、RFC 4180 转义、稳定排序与中文标签。 */
class CsvExporterTest {

    private val day = LocalDate.of(2026, 10, 20).toEpochDay()
    private val createdAt = Instant.parse("2026-10-20T11:57:13Z").toEpochMilli()

    private fun record(
        id: Long = 1,
        itemId: Long = 1,
        date: Long = day,
        createdAt: Long = this.createdAt,
        value: Double? = null,
        unit: String = "",
        note: String = "",
        status: RecordStatus = RecordStatus.DONE,
        source: RecordSource = RecordSource.MANUAL,
        isBackfill: Boolean = false,
        name: String = "Bool1",
        timezone: String = "Asia/Shanghai",
    ) = CheckRecord(
        id = id,
        itemId = itemId,
        date = date,
        value = value,
        unit = unit,
        note = note,
        status = status,
        source = source,
        isBackfill = isBackfill,
        timezone = timezone,
        createdAt = createdAt,
        updatedAt = createdAt,
        itemNameSnapshot = name,
        itemColorSnapshot = 0,
        itemIconSnapshot = "",
    )

    private fun dataLines(csv: String): List<String> =
        csv.removePrefix("\uFEFF").split("\r\n").drop(1).filter { it.isNotEmpty() }

    @Test
    fun writesBomHeaderAndRowsInChronologicalOrder() {
        val csv = CsvExporter.buildRecordsCsv(
            records = listOf(
                record(id = 3, itemId = 3, name = "Val1", date = day + 1),
                record(id = 2, itemId = 2, name = "Count1", createdAt = createdAt + 1_000),
                record(id = 1, itemId = 1, name = "Bool1"),
            ),
            timezone = "Asia/Shanghai",
        )

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.startsWith("\uFEFF日期,记录时间,选项,状态,数值,单位,备注,来源,补卡,时区\r\n"))
        assertEquals(listOf("Bool1", "Count1", "Val1"), dataLines(csv).map { it.split(",")[2] })
        assertTrue(csv.endsWith("\r\n"))
    }

    @Test
    fun escapesCommasQuotesAndLineBreaks() {
        val csv = CsvExporter.buildRecordsCsv(
            records = listOf(record(name = "跑步,晨间", note = "他说\"加油\"\n第二行")),
            timezone = "Asia/Shanghai",
        )

        assertTrue(csv.contains("\"跑步,晨间\""))
        assertTrue(csv.contains("\"他说\"\"加油\"\"\n第二行\""))
    }

    @Test
    fun protectsFormulaLikeTextButNotNegativeNumbers() {
        val csv = CsvExporter.buildRecordsCsv(
            records = listOf(record(name = "=SUM(A1)", note = "+1", value = -1.5)),
            timezone = "Asia/Shanghai",
        )

        assertTrue(csv.contains("'=SUM(A1)"))
        assertTrue(csv.contains("'+1"))
        assertTrue(csv.contains(",-1.5,"))
        assertTrue(!csv.contains(",'-1.5"))
    }

    @Test
    fun formatsStatusSourceBackfillAndValues() {
        val csv = CsvExporter.buildRecordsCsv(
            records = listOf(
                record(
                    value = 8.5,
                    unit = "分钟",
                    status = RecordStatus.PARTIAL,
                    source = RecordSource.WIDGET,
                    isBackfill = true,
                ),
            ),
            timezone = "Asia/Shanghai",
        )
        val fields = dataLines(csv).single().split(",")

        assertEquals("2026-10-20", fields[0])
        assertEquals("2026-10-20 19:57:13", fields[1])
        assertEquals("部分完成", fields[3])
        assertEquals("8.5", fields[4])
        assertEquals("分钟", fields[5])
        assertEquals("小组件", fields[7])
        assertEquals("是", fields[8])
        assertEquals("Asia/Shanghai", fields[9])
    }

    @Test
    fun formatsWholeNumbersAndLeavesNullEmpty() {
        val csv = CsvExporter.buildRecordsCsv(
            records = listOf(
                record(id = 1, value = 1.0),
                record(id = 2, value = null),
            ),
            timezone = "Asia/Shanghai",
        )
        val rows = dataLines(csv).map { it.split(",") }

        assertEquals("1", rows[0][4])
        assertEquals("", rows[1][4])
    }

    @Test
    fun recordTimeUsesProvidedTimezone() {
        val shanghai = CsvExporter.buildRecordsCsv(listOf(record()), "Asia/Shanghai")
        val utc = CsvExporter.buildRecordsCsv(listOf(record()), "UTC")

        assertTrue(dataLines(shanghai).single().contains("2026-10-20 19:57:13"))
        assertTrue(dataLines(utc).single().contains("2026-10-20 11:57:13"))
    }

    @Test
    fun emptyRecordsProducesHeaderOnly() {
        val csv = CsvExporter.buildRecordsCsv(emptyList(), "Asia/Shanghai")

        assertEquals("\uFEFF" + "日期,记录时间,选项,状态,数值,单位,备注,来源,补卡,时区" + "\r\n", csv)
    }
}
