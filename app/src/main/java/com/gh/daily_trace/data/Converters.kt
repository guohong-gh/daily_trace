package com.gh.daily_trace.data

import androidx.room.TypeConverter
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.data.model.ThemeMode

/** Room 类型转换：枚举与 List<Long> 的持久化（参数需为字段精确类型） */
class Converters {

    @TypeConverter fun fromCheckType(v: CheckType): String = v.name
    @TypeConverter fun toCheckType(v: String): CheckType = CheckType.valueOf(v)

    @TypeConverter fun fromRecordStatus(v: RecordStatus): String = v.name
    @TypeConverter fun toRecordStatus(v: String): RecordStatus = RecordStatus.valueOf(v)

    @TypeConverter fun fromRecordSource(v: RecordSource): String = v.name
    @TypeConverter fun toRecordSource(v: String): RecordSource = RecordSource.valueOf(v)

    @TypeConverter fun fromMultiRecordRule(v: MultiRecordRule): String = v.name
    @TypeConverter fun toMultiRecordRule(v: String): MultiRecordRule = MultiRecordRule.valueOf(v)

    @TypeConverter fun fromFilterMode(v: FilterMode): String = v.name
    @TypeConverter fun toFilterMode(v: String): FilterMode = FilterMode.valueOf(v)

    @TypeConverter fun fromFilterLogic(v: FilterLogic): String = v.name
    @TypeConverter fun toFilterLogic(v: String): FilterLogic = FilterLogic.valueOf(v)

    @TypeConverter fun fromDayFilter(v: DayFilter): String = v.name
    @TypeConverter fun toDayFilter(v: String): DayFilter = DayFilter.valueOf(v)

    @TypeConverter fun fromFrequencyType(v: FrequencyType): String = v.name
    @TypeConverter fun toFrequencyType(v: String): FrequencyType = FrequencyType.valueOf(v)

    @TypeConverter fun fromRepeatType(v: RepeatType): String = v.name
    @TypeConverter fun toRepeatType(v: String): RepeatType = RepeatType.valueOf(v)

    @TypeConverter fun fromThemeMode(v: ThemeMode): String = v.name
    @TypeConverter fun toThemeMode(v: String): ThemeMode = ThemeMode.valueOf(v)

    @TypeConverter fun fromLongList(v: List<Long>): String = v.joinToString(",")

    @TypeConverter fun toLongList(v: String): List<Long> =
        if (v.isEmpty()) emptyList() else v.split(",").map { it.toLong() }

    @TypeConverter fun fromIntList(v: List<Int>): String = v.joinToString(",")

    @TypeConverter fun toIntList(v: String): List<Int> =
        if (v.isEmpty()) emptyList() else v.split(",").map { it.toInt() }
}
