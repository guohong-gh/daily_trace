package com.gh.daily_trace.domain.time

import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.DateFormat
import com.gh.daily_trace.data.model.DateLanguage
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** M10-4：日期语言 / 格式的统一显示口径。 */
class DateDisplayTest {

    private val oct20 = LocalDate.of(2026, 10, 20).toEpochDay()
    private val oct19 = LocalDate.of(2026, 10, 19).toEpochDay()
    private val oct26 = LocalDate.of(2026, 10, 26).toEpochDay()
    private val dec28 = LocalDate.of(2026, 12, 28).toEpochDay()
    private val nov1 = LocalDate.of(2026, 11, 1).toEpochDay()
    private val jan3 = LocalDate.of(2027, 1, 3).toEpochDay()

    @Test
    fun chineseAutoUsesChineseDateWords() {
        val display = DateDisplay(DateLanguage.ZH, DateFormat.AUTO)

        assertEquals("2026年10月20日 周二", display.full(oct20))
        assertEquals("10月20日 周二", display.short(oct20))
        assertEquals("2026年10月", display.monthTitle(YearMonth.of(2026, 10)))
        assertEquals("2026 年", display.yearTitle(2026))
    }

    @Test
    fun englishAutoUsesEnglishDateWords() {
        val display = DateDisplay(DateLanguage.EN, DateFormat.AUTO)

        assertEquals("Tue, Oct 20, 2026", display.full(oct20))
        assertEquals("Tue, Oct 20", display.short(oct20))
        assertEquals("October 2026", display.monthTitle(YearMonth.of(2026, 10)))
        assertEquals("2026", display.yearTitle(2026))
    }

    @Test
    fun numericFormatsOverrideLanguageForDayLabels() {
        assertEquals("2026-10-20", DateDisplay(DateLanguage.EN, DateFormat.YMD).short(oct20))
        assertEquals("10/20/2026", DateDisplay(DateLanguage.ZH, DateFormat.MDY).short(oct20))
        assertEquals("20/10/2026", DateDisplay(DateLanguage.ZH, DateFormat.DMY).full(oct20))
    }

    @Test
    fun chineseWeekTitleExpandsAcrossMonthAndYear() {
        val display = DateDisplay(DateLanguage.ZH, DateFormat.AUTO)

        assertEquals("2026年10月19日 – 25日", display.weekTitle(oct19, oct19 + 6))
        assertEquals("2026年10月26日 – 11月1日", display.weekTitle(oct26, nov1))
        assertEquals("2026年12月28日 – 2027年1月3日", display.weekTitle(dec28, jan3))
    }

    @Test
    fun englishWeekTitleExpandsAcrossMonthAndYear() {
        val display = DateDisplay(DateLanguage.EN, DateFormat.AUTO)

        assertEquals("Oct 19 – 25, 2026", display.weekTitle(oct19, oct19 + 6))
        assertEquals("Oct 26 – Nov 1, 2026", display.weekTitle(oct26, nov1))
        assertEquals("Dec 28, 2026 – Jan 3, 2027", display.weekTitle(dec28, jan3))
    }

    @Test
    fun systemLanguageFallsBackToChineseOrEnglish() {
        val zh = DateDisplay(DateLanguage.SYSTEM, DateFormat.AUTO, Locale.SIMPLIFIED_CHINESE)
        val en = DateDisplay(DateLanguage.SYSTEM, DateFormat.AUTO, Locale.US)

        assertEquals(DateLanguage.ZH, zh.language)
        assertEquals(DateLanguage.EN, en.language)
        assertEquals("10月20日 周二", zh.short(oct20))
        assertEquals("Tue, Oct 20", en.short(oct20))
    }

    @Test
    fun weekdayHeadersAndShortMonthsFollowLanguage() {
        val zh = DateDisplay(DateLanguage.ZH, DateFormat.AUTO)
        val en = DateDisplay(DateLanguage.EN, DateFormat.AUTO)

        assertEquals("一", zh.weekdayHeader(oct19))
        assertEquals("Mon", en.weekdayHeader(oct19))
        assertEquals("10月", zh.monthShort(10))
        assertEquals("Oct", en.monthShort(10))
    }

    @Test
    fun settingsFactoryCarriesLanguageAndFormat() {
        val display = DateDisplay.from(
            AppSettings(dateLanguage = DateLanguage.EN, dateFormat = DateFormat.MDY),
            Locale.SIMPLIFIED_CHINESE,
        )

        assertEquals(DateLanguage.EN, display.language)
        assertEquals(DateFormat.MDY, display.dateFormat)
        assertEquals("10/20/2026", display.short(oct20))
        assertEquals("October 2026", display.monthTitle(YearMonth.of(2026, 10)))
    }
}
