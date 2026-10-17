package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 日期整体备注（第三层备注），与选项默认备注、单次打卡备注完全分离 */
@Entity(tableName = "day_notes")
data class DayNote(
    /** 本地日历日（epochDay），即主键，一天一条 */
    @PrimaryKey val date: Long,
    val note: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
