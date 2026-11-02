package com.gh.daily_trace.domain.time

import com.gh.daily_trace.data.model.AppSettings
import kotlinx.coroutines.delay
import java.time.Instant

/** 轮询上限：最长睡到这个间隔就醒一次，便于自愈时区 / 起始小时 / 系统时间变化 */
private const val MAX_SLEEP_MILLIS = 60_000L
private const val MIN_SLEEP_MILLIS = 1_000L

/**
 * 距下一个逻辑日边界的睡眠毫秒数（纯函数）。
 * 下限 1s（已跨界时尽快醒来复查），上限 [capMillis]（不一次睡过头）。
 */
fun logicalDayDelayMillis(today: Long, clock: DayClock, now: Instant, capMillis: Long = MAX_SLEEP_MILLIS): Long {
    val boundary = clock.endOfDay(today).toEpochMilli()
    return (boundary - now.toEpochMilli()).coerceIn(MIN_SLEEP_MILLIS, capMillis)
}

/**
 * 可见时钟：长驻前台跨逻辑日边界（默认凌晨 4 点）时自动回调 [onNewDay]，
 * 补上各页 LifecycleResumeEffect「回前台才刷新」覆盖不到的场景。
 *
 * 每轮重读设置（时区 / 起始小时），逻辑日变化才回调；协程随 viewModelScope 取消。
 */
suspend fun watchLogicalDay(
    settingsOf: suspend () -> AppSettings,
    onNewDay: suspend () -> Unit,
) {
    var lastDay = -1L
    while (true) {
        val clock = DayClock.from(settingsOf())
        val today = clock.today()
        if (lastDay != -1L && today != lastDay) {
            onNewDay()
        }
        lastDay = today
        delay(logicalDayDelayMillis(today, clock, Instant.now()))
    }
}
