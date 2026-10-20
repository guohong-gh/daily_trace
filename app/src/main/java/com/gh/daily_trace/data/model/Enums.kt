package com.gh.daily_trace.data.model

/** 打卡选项类型 */
enum class CheckType {
    BOOLEAN, COUNT, DURATION, VALUE
}

/** 打卡记录状态 */
enum class RecordStatus {
    DONE, PARTIAL, SKIPPED, PLANNED
}

/** 记录产生来源 */
enum class RecordSource {
    MANUAL, WIDGET, NOTIFICATION, IMPORT
}

/** 同一天同一选项多次打卡的处理规则 */
enum class MultiRecordRule {
    MERGE, ACCUMULATE, KEEP_MULTIPLE
}

/** 筛选模式：全部 / 选中若干项 */
enum class FilterMode {
    ALL, SELECTED
}

/** 多选筛选逻辑：任一 / 全部 */
enum class FilterLogic {
    ANY, ALL
}

/** 日期筛选：全部 / 只看已打卡 / 只看未打卡 */
enum class DayFilter {
    ALL, RECORDED, UNRECORDED
}

/** 频率类型 */
enum class FrequencyType {
    DAILY, DAYS_OF_WEEK, TIMES_PER_MONTH, CUSTOM
}

/** 提醒重复方式（WEEKLY 按频率规则的 daysOfWeek 生效，Phase 3 调度时使用） */
enum class RepeatType {
    DAILY, WEEKDAYS, WEEKLY
}

/** 主题模式 */
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

/** 日期显示语言（仅影响日期标签，不翻译应用界面） */
enum class DateLanguage {
    SYSTEM, ZH, EN
}

/** 日期显示格式：跟随语言 / 年-月-日 / 月/日/年 / 日/月/年 */
enum class DateFormat {
    AUTO, YMD, MDY, DMY
}
