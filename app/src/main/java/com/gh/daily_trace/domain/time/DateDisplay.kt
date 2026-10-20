package com.gh.daily_trace.domain.time

import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.DateFormat
import com.gh.daily_trace.data.model.DateLanguage
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * 日期显示格式器（M10-4）：统一今日 / 日历 / 日期详情 / 搜索等日期标签。
 *
 * - 日期语言只影响日期文本，不翻译应用界面；[DateLanguage.SYSTEM] 在系统语言为中文时用中文，
 *   其余语言回退英文，保证输出稳定且可测。
 * - [DateFormat.AUTO] 使用语言对应的自然格式；YMD / MDY / DMY 使用纯数字日期。
 * - 月 / 周 / 年标题保持日期语言的自然写法；CSV、备份文件名与数据库日期不受此设置影响。
 */
class DateDisplay(
    languageSetting: DateLanguage = DateLanguage.ZH,
    val dateFormat: DateFormat = DateFormat.AUTO,
    systemLocale: Locale = Locale.getDefault(),
) {

    /** 实际生效的语言：SYSTEM 仅区分中文 / 英文，避免引入未翻译的第三语言日期。 */
    val language: DateLanguage = when (languageSetting) {
        DateLanguage.SYSTEM ->
            if (systemLocale.language.equals("zh", ignoreCase = true)) DateLanguage.ZH else DateLanguage.EN

        else -> languageSetting
    }

    private val locale: Locale =
        if (language == DateLanguage.ZH) Locale.SIMPLIFIED_CHINESE else Locale.ENGLISH

    private val numericFormatter: DateTimeFormatter? = when (dateFormat) {
        DateFormat.YMD -> DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US)
        DateFormat.MDY -> DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US)
        DateFormat.DMY -> DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US)
        DateFormat.AUTO -> null
    }

    private val fullFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (language == DateLanguage.ZH) "yyyy年M月d日 EEE" else "EEE, MMM d, yyyy",
        locale,
    )

    private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (language == DateLanguage.ZH) "M月d日 EEE" else "EEE, MMM d",
        locale,
    )

    private val monthTitleFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(
        if (language == DateLanguage.ZH) "yyyy年M月" else "MMMM yyyy",
        locale,
    )

    private val weekSameMonthFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (language == DateLanguage.ZH) "yyyy年M月d日" else "MMM d", locale)

    private val weekSameYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (language == DateLanguage.ZH) "M月d日" else "MMM d", locale)

    private val weekYearFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (language == DateLanguage.ZH) "yyyy年M月d日" else "MMM d, yyyy", locale)

    private val weekdayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (language == DateLanguage.ZH) "EEEEE" else "EEE", locale)

    /** 完整日期（含年份） */
    fun full(epochDay: Long): String =
        numericFormatter?.let { LocalDate.ofEpochDay(epochDay).format(it) }
            ?: LocalDate.ofEpochDay(epochDay).format(fullFormatter)

    /** 短日期（今日标题、日期详情、搜索、摘要） */
    fun short(epochDay: Long): String =
        numericFormatter?.let { LocalDate.ofEpochDay(epochDay).format(it) }
            ?: LocalDate.ofEpochDay(epochDay).format(shortFormatter)

    /** 日历月标题，如「2026年10月」/「October 2026」 */
    fun monthTitle(month: YearMonth): String = month.format(monthTitleFormatter)

    /** 年标题，如「2026 年」/「2026」 */
    fun yearTitle(year: Int): String =
        if (language == DateLanguage.ZH) "$year 年" else year.toString()

    /** 周标题；跨月 / 跨年时补全，如「2026年10月19日 – 25日」/「Oct 19 – 25, 2026」 */
    fun weekTitle(startDay: Long, endDay: Long): String {
        val start = LocalDate.ofEpochDay(startDay)
        val end = LocalDate.ofEpochDay(endDay)
        return when {
            start.year == end.year && start.month == end.month -> {
                if (language == DateLanguage.ZH) {
                    "${start.format(weekSameMonthFormatter)} – ${end.dayOfMonth}日"
                } else {
                    "${start.format(weekSameMonthFormatter)} – ${end.dayOfMonth}, ${end.year}"
                }
            }

            start.year == end.year -> {
                if (language == DateLanguage.ZH) {
                    "${start.format(weekSameMonthFormatter)} – ${end.format(weekSameYearFormatter)}"
                } else {
                    "${start.format(weekSameYearFormatter)} – ${end.format(weekYearFormatter)}"
                }
            }

            else -> {
                "${start.format(weekYearFormatter)} – ${end.format(weekYearFormatter)}"
            }
        }
    }

    /** 星期表头：中文单字（一…日），英文三字母（Mon…Sun） */
    fun weekdayHeader(epochDay: Long): String =
        LocalDate.ofEpochDay(epochDay).format(weekdayFormatter)

    /** 年视图月份栏：中文「10月」，英文「Oct」 */
    fun monthShort(month: Int): String =
        if (language == DateLanguage.ZH) {
            "${month}月"
        } else {
            Month.of(month).getDisplayName(TextStyle.SHORT, locale)
        }

    companion object {
        fun from(settings: AppSettings, systemLocale: Locale = Locale.getDefault()): DateDisplay =
            DateDisplay(settings.dateLanguage, settings.dateFormat, systemLocale)
    }
}
