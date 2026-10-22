package com.gh.daily_trace.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 仅 debug 构建包含的测试入口：验证全局未捕获异常处理器会写入崩溃日志。
 *
 * 触发方式：
 * `adb shell am broadcast -n com.gh.daily_trace/.debug.CrashTestReceiver`
 */
class CrashTestReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        throw IllegalStateException("M10-7 崩溃日志验收测试")
    }
}
