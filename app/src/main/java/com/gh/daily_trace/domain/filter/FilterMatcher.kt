package com.gh.daily_trace.domain.filter

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView

/**
 * 筛选视图匹配规则：
 * - mode=ALL：不按选项/分组过滤，仅 dayFilter 生效；
 * - mode=SELECTED：
 *   - 仅选了选项 → 命中 itemIds；
 *   - 仅选了分组 → 命中 groupIds；
 *   - 两者都选 → logic=ANY 取并集（任一命中），logic=ALL 取交集（都需命中）；
 * - dayFilter：ALL 不限制；RECORDED 仅当天已打卡；UNRECORDED 仅当天未打卡。
 */
object FilterMatcher {

    fun itemMatches(item: CheckItem, filter: FilterView): Boolean {
        if (filter.mode == FilterMode.ALL) return true
        val hasItems = filter.itemIds.isNotEmpty()
        val hasGroups = filter.groupIds.isNotEmpty()
        if (!hasItems && !hasGroups) return true

        val inItems = item.id in filter.itemIds
        val inGroups = item.groupId != null && item.groupId in filter.groupIds
        return when {
            hasItems && hasGroups ->
                if (filter.logic == FilterLogic.ALL) inItems && inGroups else inItems || inGroups
            hasItems -> inItems
            else -> inGroups
        }
    }

    fun dayMatches(hasRecords: Boolean, filter: FilterView): Boolean = when (filter.dayFilter) {
        DayFilter.ALL -> true
        DayFilter.RECORDED -> hasRecords
        DayFilter.UNRECORDED -> !hasRecords
    }

    /** 组合判断；filter 为 null 表示未启用筛选 */
    fun matches(item: CheckItem, hasRecords: Boolean, filter: FilterView?): Boolean {
        if (filter == null) return true
        return itemMatches(item, filter) && dayMatches(hasRecords, filter)
    }
}
