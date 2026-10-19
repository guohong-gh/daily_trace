package com.gh.daily_trace.domain.calendar

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.domain.filter.FilterMatcher

/**
 * D31 筛选口径的共享实现：日历类聚合（圆点/摘要/热力图）只取选项/分组范围、忽略 dayFilter。
 * 选项已被彻底清除（不在 items 表中）时无法参与选项/分组匹配：与 `FilterMatcher` 语义一致，
 * ALL 模式或未选择任何范围时视为命中；软删除选项仍在 items 中，走正常匹配。
 */
internal object ItemScope {

    fun passes(itemId: Long, itemById: Map<Long, CheckItem>, filter: FilterView?): Boolean {
        if (filter == null) return true
        val item = itemById[itemId]
            ?: return filter.mode == FilterMode.ALL ||
                (filter.itemIds.isEmpty() && filter.groupIds.isEmpty())
        return FilterMatcher.itemMatches(item, filter)
    }
}
