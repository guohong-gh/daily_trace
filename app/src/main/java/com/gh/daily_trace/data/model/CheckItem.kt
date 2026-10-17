package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 打卡选项。
 * 频率规则与提醒规则分别存于 FrequencyRule / Reminder 表（以 itemId 关联），本表不冗余。
 */
@Entity(tableName = "check_items", indices = [Index("groupId")])
data class CheckItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** ARGB 颜色；必须与图标/emoji 搭配使用，不单靠颜色区分 */
    val color: Int,
    /** emoji 或图标标识，用于色盲友好的双重编码 */
    val icon: String = "",
    val type: CheckType,
    /** 单位：分钟/次/页/毫升等，布尔型可为空串 */
    val unit: String = "",
    /** 目标值，如每天 30 分钟 */
    val targetValue: Double? = null,
    /** 选项级默认备注（第一层备注） */
    val defaultNote: String = "",
    val groupId: Long? = null,
    /** 同一天多次打卡的合并规则，默认保留多条 */
    val multiRecordRule: MultiRecordRule = MultiRecordRule.KEEP_MULTIPLE,
    val sort: Int = 0,
    val enabled: Boolean = true,
    /** 归档：不再出现在打卡列表，但保留历史 */
    val archived: Boolean = false,
    /** 软删除（回收站）；非空即视为已删除，历史记录与快照不受影响 */
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
