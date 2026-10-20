package com.gh.daily_trace.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.gh.daily_trace.DailyTraceApp
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.domain.checkin.CheckInPlanner
import com.gh.daily_trace.domain.checkin.applyPlan
import com.gh.daily_trace.domain.time.DayClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 组件快捷项点击（M9-4）：
 * 布尔型且今天未完成 → 直接落一条 WIDGET 来源记录（幂等），随后刷新组件；
 * 其他情况在构建 RemoteViews 时已指向打开 App，不会进入这里。
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CHECK_IN) return
        val app = context.applicationContext as? DailyTraceApp ?: return
        val itemId = intent.getLongExtra(EXTRA_ITEM_ID, -1L)
        if (itemId <= 0) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = app.container.repository
                val item = repository.getItem(itemId) ?: return@launch
                if (item.type != CheckType.BOOLEAN || item.deletedAt != null || item.archived || !item.enabled) {
                    return@launch
                }
                val settings = repository.getSettings()
                val today = DayClock.from(settings).today()
                val existing = repository.getRecordsOfItemOn(itemId, today)
                if (existing.none { it.status == RecordStatus.DONE }) {
                    applyPlan(
                        repository,
                        CheckInPlanner.toggleBoolean(
                            item = item,
                            date = today,
                            existing = existing,
                            timezone = settings.timezone,
                            isBackfill = false,
                            now = System.currentTimeMillis(),
                            source = RecordSource.WIDGET,
                        ),
                    )
                }
                TodayWidget.updateAll(context, app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK_IN = "com.gh.daily_trace.action.WIDGET_CHECK_IN"
        const val EXTRA_ITEM_ID = "item_id"
    }
}
