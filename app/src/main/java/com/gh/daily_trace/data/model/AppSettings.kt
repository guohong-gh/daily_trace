package com.gh.daily_trace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 应用设置，单行表（id 恒为 1）。
 * 使用 Room 而非 DataStore，以便备份/导出时与其余数据一并序列化。
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** 是否启用 Material You 动态取色（默认关闭，保持品牌色板一致性） */
    val dynamicColor: Boolean = false,
    /** 周起始日：1=周一 … 7=周日 */
    val weekStart: Int = 1,
    /** 日起始小时：凌晨 4 点前仍算前一天 */
    val startOfDay: Int = 4,
    /** 判定"今天"的时区 ID */
    val timezone: String = "Asia/Shanghai",
    val colorBlindMode: Boolean = false,
    /** 允许补卡的最大天数（0 = 禁止补卡） */
    val maxBackfillDays: Int = 7,
    /** 统计口径：部分完成是否计入完成率分子 */
    val partialCountsAsDone: Boolean = true,
    val autoBackup: Boolean = false,
    /** 自动备份间隔天数 */
    val autoBackupIntervalDays: Int = 7,
    val encryption: Boolean = false
)
