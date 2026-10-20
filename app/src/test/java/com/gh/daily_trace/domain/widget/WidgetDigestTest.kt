package com.gh.daily_trace.domain.widget

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M9-4 Widget 口径：在册选项进度 + 管理顺序前 4 个快捷项。 */
class WidgetDigestTest {

    private fun item(
        id: Long,
        sort: Int,
        enabled: Boolean = true,
        archived: Boolean = false,
        deleted: Boolean = false,
        type: CheckType = CheckType.BOOLEAN,
    ) = CheckItem(
        id = id,
        name = "选项$id",
        color = 0,
        icon = "🏃",
        type = type,
        sort = sort,
        enabled = enabled,
        archived = archived,
        deletedAt = if (deleted) 1L else null,
    )

    private fun record(itemId: Long, status: RecordStatus = RecordStatus.DONE) = CheckRecord(
        itemId = itemId,
        date = 20_000L,
        status = status,
        itemNameSnapshot = "选项$itemId",
        itemColorSnapshot = 0,
        itemIconSnapshot = "",
    )

    @Test
    fun keepsOnlyActiveItemsSortedByManagementOrder() {
        val digest = buildWidgetDigest(
            items = listOf(
                item(1, sort = 5),
                item(2, sort = 0, enabled = false),
                item(3, sort = 1, archived = true),
                item(4, sort = 2, deleted = true),
                item(5, sort = 3),
                item(6, sort = 4),
                item(7, sort = 6),
                item(8, sort = 7),
            ),
            records = emptyList(),
        )

        assertEquals(5, digest.total)
        assertEquals(listOf(5L, 6L, 1L, 7L), digest.quickItems.map { it.id })
    }

    @Test
    fun progressCountsDistinctDoneRecordsOnly() {
        val digest = buildWidgetDigest(
            items = listOf(item(1, 0), item(2, 1), item(3, 2)),
            records = listOf(
                record(1),
                record(1), // 同一天多次只算一次
                record(2, RecordStatus.PARTIAL),
                record(3, RecordStatus.PLANNED),
            ),
        )

        assertEquals(1, digest.done)
        assertEquals(3, digest.total)
        assertEquals(33, digest.percent)
    }

    @Test
    fun quickItemsKeepDoneFlag() {
        val digest = buildWidgetDigest(
            items = listOf(item(1, 0), item(2, 1)),
            records = listOf(record(2)),
        )

        assertTrue(digest.quickItems.first { it.id == 2L }.done)
        assertFalse(digest.quickItems.first { it.id == 1L }.done)
        assertEquals(50, digest.percent)
    }

    @Test
    fun percentRoundsToNearestInt() {
        assertEquals(33, buildWidgetDigest(listOf(item(1, 0), item(2, 1), item(3, 2)), listOf(record(1))).percent)
        assertEquals(67, buildWidgetDigest(listOf(item(1, 0), item(2, 1), item(3, 2)), listOf(record(1), record(2))).percent)
    }

    @Test
    fun emptyOrZeroLimitIsSafe() {
        val empty = buildWidgetDigest(emptyList(), emptyList())
        assertEquals(0, empty.total)
        assertEquals(0, empty.percent)
        assertTrue(empty.quickItems.isEmpty())

        val noQuick = buildWidgetDigest(listOf(item(1, 0)), emptyList(), maxQuickItems = 0)
        assertEquals(1, noQuick.total)
        assertTrue(noQuick.quickItems.isEmpty())
    }
}
