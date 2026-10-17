package com.gh.daily_trace.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.calendar.CalendarRoute
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

@Composable
fun AppNavHost(container: Container) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppRoutes.TODAY) {
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
        composable(AppRoutes.CALENDAR) {
            CalendarRoute(
                container = container,
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
