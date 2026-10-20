package com.gh.daily_trace.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.gh.daily_trace.DailyTraceApp
import com.gh.daily_trace.MainActivity
import com.gh.daily_trace.R
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.domain.time.DayClock
import com.gh.daily_trace.domain.widget.WidgetDigest
import com.gh.daily_trace.domain.widget.buildWidgetDigest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 今日小组件（M9-4）：今日总进度 + 最多 4 个快捷项。
 *
 * 数据刷新：系统 30 分钟周期、应用内数据变化（Room Flow 观察者）、
 * 组件点击打卡后立即刷新。所有组件实例展示同一份数据。
 */
object TodayWidget {

    const val QUICK_LIMIT = 4

    private val ROW_IDS = intArrayOf(
        R.id.widget_row1,
        R.id.widget_row2,
        R.id.widget_row3,
        R.id.widget_row4,
    )
    private val ICON_IDS = intArrayOf(
        R.id.widget_icon1,
        R.id.widget_icon2,
        R.id.widget_icon3,
        R.id.widget_icon4,
    )
    private val NAME_IDS = intArrayOf(
        R.id.widget_name1,
        R.id.widget_name2,
        R.id.widget_name3,
        R.id.widget_name4,
    )
    private val ACTION_IDS = intArrayOf(
        R.id.widget_action1,
        R.id.widget_action2,
        R.id.widget_action3,
        R.id.widget_action4,
    )

    private fun widgetIds(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))

    /** 异步刷新全部组件；没有组件实例时直接返回，不查库 */
    fun requestUpdate(context: Context) {
        val app = context.applicationContext as? DailyTraceApp ?: return
        if (widgetIds(context).isEmpty()) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { updateAll(context, app) }
        }
    }

    suspend fun updateAll(context: Context, app: DailyTraceApp) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) return

        val repository = app.container.repository
        val settings = repository.getSettings()
        val today = DayClock.from(settings).today()
        val digest = buildWidgetDigest(
            items = repository.getAllItems(),
            records = repository.getRecordsOn(today),
            maxQuickItems = QUICK_LIMIT,
        )
        val views = buildViews(context, digest)
        ids.forEach { manager.updateAppWidget(it, views) }
    }

    private fun buildViews(context: Context, digest: WidgetDigest): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_today)
        views.setTextViewText(R.id.widget_progress, "${digest.done} / ${digest.total}")
        views.setProgressBar(R.id.widget_bar, 100, digest.percent, false)
        views.setOnClickPendingIntent(R.id.widget_header, openAppIntent(context))
        views.setOnClickPendingIntent(R.id.widget_more, openAppIntent(context))

        val empty = digest.total == 0
        views.setViewVisibility(R.id.widget_empty, if (empty) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widget_rows, if (empty) View.GONE else View.VISIBLE)

        ROW_IDS.indices.forEach { index ->
            val quick = digest.quickItems.getOrNull(index)
            val rowId = ROW_IDS[index]
            if (quick == null) {
                views.setViewVisibility(rowId, View.GONE)
                return@forEach
            }
            views.setViewVisibility(rowId, View.VISIBLE)
            views.setTextViewText(ICON_IDS[index], quick.icon.ifBlank { "•" })
            views.setTextViewText(NAME_IDS[index], quick.name)
            views.setTextViewText(
                ACTION_IDS[index],
                when {
                    quick.done -> "已完成"
                    quick.type == CheckType.BOOLEAN -> "打卡"
                    else -> "记录"
                },
            )
            views.setTextColor(
                NAME_IDS[index],
                ContextCompat.getColor(
                    context,
                    if (quick.done) R.color.widget_text_secondary else R.color.widget_text_primary,
                ),
            )
            views.setTextColor(
                ACTION_IDS[index],
                ContextCompat.getColor(
                    context,
                    if (quick.done) R.color.widget_text_secondary else R.color.widget_accent,
                ),
            )
            views.setOnClickPendingIntent(
                rowId,
                if (!quick.done && quick.type == CheckType.BOOLEAN) {
                    checkInIntent(context, quick.id)
                } else {
                    openAppIntent(context)
                },
            )
        }

        val more = (digest.total - digest.quickItems.size).coerceAtLeast(0)
        views.setViewVisibility(R.id.widget_more, if (more > 0) View.VISIBLE else View.GONE)
        if (more > 0) views.setTextViewText(R.id.widget_more, "还有 $more 项 · 点按打开")
        return views
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun checkInIntent(context: Context, itemId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            itemId.toInt(),
            Intent(context, WidgetActionReceiver::class.java)
                .setAction(WidgetActionReceiver.ACTION_CHECK_IN)
                .putExtra(WidgetActionReceiver.EXTRA_ITEM_ID, itemId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
