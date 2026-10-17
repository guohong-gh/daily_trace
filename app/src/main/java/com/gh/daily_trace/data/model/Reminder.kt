package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 提醒规则：每个打卡选项至多一条（itemId 唯一索引） */
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["itemId"], unique = true)]
)
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** 提醒时刻，距 0 点分钟数（0..1439） */
    val time: Int,
    val repeat: RepeatType = RepeatType.DAILY,
    /** WEEKLY 时生效：1=周一..7=周日（ISO-8601）。与频率规则的 daysOfWeek 相互独立 */
    val daysOfWeek: List<Int> = emptyList(),
    val enabled: Boolean = true,
    /** 稍后提醒间隔（分钟） */
    val snooze: Int = 10,
    /** 免打扰时段（距 0 点分钟数），null 表示未启用 */
    val dndStart: Int? = null,
    val dndEnd: Int? = null,
    val updatedAt: Long = System.currentTimeMillis()
)
