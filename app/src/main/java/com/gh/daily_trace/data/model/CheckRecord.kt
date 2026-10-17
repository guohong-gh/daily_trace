package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.TimeZone

/** 打卡记录：某一天某个打卡选项的一次完成 */
@Entity(
    tableName = "check_records",
    indices = [Index("date"), Index("itemId"), Index("itemId", "date")]
)
data class CheckRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    /** 本地日历日（epochDay），结合 timezone 判定，避免跨时区/跨天错乱 */
    val date: Long,
    /** 实际值：布尔型为 1.0 或空，次数/时长/数值型为具体量 */
    val value: Double? = null,
    val unit: String = "",
    /** 单次打卡备注（第二层备注） */
    val note: String = "",
    val status: RecordStatus = RecordStatus.DONE,
    val source: RecordSource = RecordSource.MANUAL,
    /** 是否补卡 */
    val isBackfill: Boolean = false,
    /** 记录产生时的时区 ID */
    val timezone: String = TimeZone.getDefault().id,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 选项快照：改名/改色/删除选项后历史展示仍正确 */
    val itemNameSnapshot: String,
    val itemColorSnapshot: Int,
    val itemIconSnapshot: String
)
