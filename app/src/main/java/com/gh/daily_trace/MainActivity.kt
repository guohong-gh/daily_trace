package com.gh.daily_trace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.ThemeMode
import com.gh.daily_trace.ui.navigation.AppNavHost
import com.gh.daily_trace.ui.theme.Daily_TraceTheme
import com.gh.daily_trace.ui.update.UpdateDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as DailyTraceApp).container
        // 退到后台时检查自动备份：刚结束的这一轮数据变更可在离开前落盘
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching { container.autoBackup.runIfDue() }
                }
            }
        })
        setContent {
            val settings by container.repository.settings
                .collectAsState(initial = AppSettings())

            Daily_TraceTheme(
                darkTheme = when (settings.theme) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                },
                dynamicColor = settings.dynamicColor,
            ) {
                AppNavHost(container = container)

                // 冷启动发现新版本时的非强制提示；数据与页面不阻塞
                val pendingVersion by container.updateManager.pendingVersion.collectAsState()
                pendingVersion?.let { version ->
                    UpdateDialog(
                        version = version,
                        currentVersion = container.updateManager.currentVersion,
                        onDismiss = { container.updateManager.dismiss() },
                    )
                }
            }
        }
    }
}
