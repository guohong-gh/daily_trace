package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 打卡选项分组（如：健康、学习、工作、运动），名称与数量由用户自定义 */
@Entity(tableName = "groups")
data class Group(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sort: Int = 0
)
