package com.gh.daily_trace.domain.widget

import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import kotlin.math.roundToInt

/** Widget 中的单个快捷项（M9-4） */
data class WidgetQuickItem(
    val id: Long,
    val name: String,
    val icon: String,
    val type: CheckType,
    val done: Boolean,
)

/** Widget 视图数据：今日总进度 + 最多 N 个快捷项 */
data class WidgetDigest(
    val done: Int,
    val total: Int,
    val percent: Int,
    val quickItems: List<WidgetQuickItem>,
)

/**
 * 汇总 Widget 展示数据：
 * - 进度按在册选项（启用、未归档、未删除）统计，与今日进度 / 每日汇总同口径；
 * - 快捷项按管理顺序取前 [maxQuickItems] 个，已完成的仍保留位置（避免列表跳动）；
 * - 当天 DONE 记录去重，一个选项多次打卡只算一次。
 */
fun buildWidgetDigest(
    items: List<CheckItem>,
    records: List<CheckRecord>,
    maxQuickItems: Int = 4,
): WidgetDigest {
    val active = items.asSequence()
        .filter { it.deletedAt == null && it.enabled && !it.archived }
        .sortedWith(compareBy({ it.sort }, { it.id }))
        .toList()
    val activeIds = active.map { it.id }.toSet()
    val doneIds = records.asSequence()
        .filter { it.status == RecordStatus.DONE && it.itemId in activeIds }
        .map { it.itemId }
        .toSet()

    val total = active.size
    val done = doneIds.size
    val percent = if (total == 0) 0 else ((done * 100.0) / total).roundToInt()
    return WidgetDigest(
        done = done,
        total = total,
        percent = percent,
        quickItems = active.take(maxQuickItems.coerceAtLeast(0)).map { item ->
            WidgetQuickItem(
                id = item.id,
                name = item.name,
                icon = item.icon,
                type = item.type,
                done = item.id in doneIds,
            )
        },
    )
}
