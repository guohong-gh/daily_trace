package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 保存的筛选视图 */
@Entity(tableName = "filter_views")
data class FilterView(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mode: FilterMode = FilterMode.ALL,
    /** SELECTED 模式下选中的打卡选项 */
    val itemIds: List<Long> = emptyList(),
    /** 按分组筛选选中的分组 */
    val groupIds: List<Long> = emptyList(),
    /** 多选逻辑：任一 / 全部 */
    val logic: FilterLogic = FilterLogic.ANY,
    /** 只看已打卡 / 只看未打卡（对应筛选器辅助操作） */
    val dayFilter: DayFilter = DayFilter.ALL,
    val createdAt: Long = System.currentTimeMillis()
)
