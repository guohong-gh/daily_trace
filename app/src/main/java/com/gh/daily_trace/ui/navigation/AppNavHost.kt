package com.gh.daily_trace.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.calendar.CalendarRoute
import com.gh.daily_trace.ui.common.AppWindowSize
import com.gh.daily_trace.ui.common.ContentMaxWidth
import com.gh.daily_trace.ui.datedetail.DateDetailRoute
import com.gh.daily_trace.ui.filter.FilterRoute
import com.gh.daily_trace.ui.itemedit.ItemEditRoute
import com.gh.daily_trace.ui.manage.ManageItemsRoute
import com.gh.daily_trace.ui.search.SearchRoute
import com.gh.daily_trace.ui.settings.SettingsRoute
import com.gh.daily_trace.ui.stats.StatsRoute
import com.gh.daily_trace.ui.today.TodayRoute

object AppRoutes {
    const val TODAY = "today"
    const val MANAGE = "manage"
    const val SETTINGS = "settings"
    const val CALENDAR = "calendar"
    const val SEARCH = "search"
    const val FILTER = "filter"
    const val STATS = "stats"
    const val ITEM_EDIT = "item/{itemId}"
    const val DATE_DETAIL = "date/{date}"
    fun itemEdit(itemId: Long) = "item/$itemId"
    fun dateDetail(date: Long) = "date/$date"
}

private data class RailDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val railDestinations = listOf(
    RailDestination(AppRoutes.TODAY, "今日", Icons.Filled.Home),
    RailDestination(AppRoutes.CALENDAR, "日历", Icons.Filled.DateRange),
    RailDestination(AppRoutes.STATS, "统计", Icons.Filled.Star),
    RailDestination(AppRoutes.MANAGE, "管理", Icons.AutoMirrored.Filled.List),
    RailDestination(AppRoutes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun AppNavHost(container: Container) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val navigateTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(AppRoutes.TODAY) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val windowSize = AppWindowSize.fromWidthDp(maxWidth.value)
        // 横屏手机高度不足，继续用各页顶栏返回，避免导航栏被裁切
        val showRail = windowSize != AppWindowSize.COMPACT && maxHeight >= 480.dp
        Row(Modifier.fillMaxSize()) {
            if (showRail) {
                AppNavigationRail(
                    currentRoute = currentRoute,
                    onNavigate = navigateTopLevel,
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.TopCenter,
            ) {
                AppNavGraph(
                    navController = navController,
                    container = container,
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = ContentMaxWidth)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
) {
    NavigationRail(
        header = {
            Text(
                "日迹",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        },
    ) {
        railDestinations.forEach { destination ->
            NavigationRailItem(
                selected = currentRoute == destination.route,
                onClick = { onNavigate(destination.route) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Composable
private fun AppNavGraph(
    navController: NavHostController,
    container: Container,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.TODAY,
        modifier = modifier,
    ) {
        composable(AppRoutes.TODAY) { entry ->
            // 筛选视图页「应用」后回写；-1 = 未筛选
            val appliedFilterId by entry.savedStateHandle
                .getStateFlow("appliedFilterId", -1L)
                .collectAsState()
            TodayRoute(
                container = container,
                onManage = { navController.navigate(AppRoutes.MANAGE) },
                onSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onCalendar = { navController.navigate(AppRoutes.CALENDAR) },
                onSearch = { navController.navigate(AppRoutes.SEARCH) },
                onFilter = { navController.navigate(AppRoutes.FILTER) },
                onStats = { navController.navigate(AppRoutes.STATS) },
                appliedFilterId = appliedFilterId,
                onClearFilter = { entry.savedStateHandle["appliedFilterId"] = -1L },
            )
        }
        composable(AppRoutes.STATS) {
            StatsRoute(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.MANAGE) {
            ManageItemsRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(AppRoutes.itemEdit(id)) },
            )
        }
        composable(
            route = AppRoutes.ITEM_EDIT,
            arguments = listOf(navArgument("itemId") { type = NavType.LongType }),
        ) { entry ->
            val itemId = entry.arguments?.getLong("itemId") ?: 0L
            ItemEditRoute(
                container = container,
                itemId = itemId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsRoute(container = container, onBack = { navController.popBackStack() })
        }
        composable(AppRoutes.CALENDAR) { entry ->
            // 日历标记联动今日页已应用的筛选（D31）：读 TODAY entry 的 savedStateHandle
            val todayEntry = remember(entry) { navController.getBackStackEntry(AppRoutes.TODAY) }
            val appliedFilterId by todayEntry.savedStateHandle
                .getStateFlow("appliedFilterId", -1L)
                .collectAsState()
            CalendarRoute(
                container = container,
                appliedFilterId = appliedFilterId,
                onBack = { navController.popBackStack() },
                onOpenDate = { date -> navController.navigate(AppRoutes.dateDetail(date)) },
            )
        }
        composable(AppRoutes.SEARCH) {
            SearchRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onOpenDate = { date -> navController.navigate(AppRoutes.dateDetail(date)) },
            )
        }
        composable(AppRoutes.FILTER) {
            FilterRoute(
                container = container,
                onBack = { navController.popBackStack() },
                onApply = { id ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("appliedFilterId", id)
                    navController.popBackStack()
                },
            )
        }
        composable(
            route = AppRoutes.DATE_DETAIL,
            arguments = listOf(navArgument("date") { type = NavType.LongType }),
        ) { entry ->
            val date = entry.arguments?.getLong("date") ?: 0L
            DateDetailRoute(
                container = container,
                date = date,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
