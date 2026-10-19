@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.gh.daily_trace.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.domain.calendar.DayMarks
import com.gh.daily_trace.domain.calendar.DaySummary
import com.gh.daily_trace.domain.calendar.HeatRow
import com.gh.daily_trace.domain.calendar.SummaryEntry
import com.gh.daily_trace.domain.calendar.WeekCellStatus
import com.gh.daily_trace.domain.calendar.WeekRow
import com.gh.daily_trace.domain.calendar.YearGrid
import com.gh.daily_trace.domain.calendar.YearHeatmap
import com.gh.daily_trace.domain.calendar.YearSummary
import com.gh.daily_trace.domain.time.DayClock
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.RecordStatusPill
import com.gh.daily_trace.ui.common.StatusPill
import com.gh.daily_trace.ui.common.formatValue
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** 远距离跳月（年月选择器/今天）直接定位，避免长动画；近距离（箭头/滑动邻月）平滑翻页 */
private const val PAGE_ANIMATE_RANGE = 2

/** 周视图行首列宽（色块 + 名称） */
private val WEEK_LABEL_WIDTH = 84.dp

private val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

/** 年视图：行首月份栏宽 / 单元格边长与间距 / 单行高（今天行自动滚动换算用） */
private val YEAR_GUTTER_WIDTH = 30.dp
private val YEAR_CELL_SIZE = 32.dp
private val YEAR_CELL_GAP = 3.dp
private val YEAR_ROW_HEIGHT = YEAR_CELL_SIZE + YEAR_CELL_GAP

@Composable
fun CalendarRoute(
    container: Container,
    appliedFilterId: Long,
    onBack: () -> Unit,
    onOpenDate: (Long) -> Unit,
) {
    val viewModel: CalendarViewModel = viewModel(
        factory = viewModelFactory { initializer { CalendarViewModel(container.repository) } }
    )
    LaunchedEffect(appliedFilterId) {
        viewModel.setFilter(appliedFilterId)
    }
    CalendarScreen(viewModel = viewModel, onBack = onBack, onOpenDate = onOpenDate)
}

@Composable
fun CalendarScreen(viewModel: CalendarViewModel, onBack: () -> Unit, onOpenDate: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsState()
    var summaryDate by remember { mutableStateOf<Long?>(null) }
    var pickerOpen by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.backToToday() }) { Text("今天") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            state.filterName?.let { name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusPill("筛选：$name", color = MaterialTheme.colorScheme.primary)
                    Text(
                        "标记已按筛选联动",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            CalendarModeTabs(selected = state.viewMode, onSelect = viewModel::setViewMode)
            Spacer(Modifier.height(8.dp))
            when (state.viewMode) {
                CalendarViewMode.MONTH -> MonthView(
                    state = state,
                    viewModel = viewModel,
                    onOpenDate = onOpenDate,
                    onLongPressDate = { summaryDate = it },
                    onOpenPicker = { pickerOpen = true },
                )
                CalendarViewMode.WEEK -> WeekView(
                    state = state,
                    viewModel = viewModel,
                    onOpenDate = onOpenDate,
                    onLongPressDate = { summaryDate = it },
                )
                CalendarViewMode.YEAR -> YearView(
                    state = state,
                    viewModel = viewModel,
                    onOpenDate = onOpenDate,
                    onLongPressDate = { summaryDate = it },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    val summary = summaryDate?.let { state.summaries[it] }
    val date = summaryDate
    if (date != null && summary != null) {
        DaySummaryDialog(
            date = date,
            summary = summary,
            onOpenDetail = {
                summaryDate = null
                onOpenDate(date)
            },
            onDismiss = { summaryDate = null },
        )
    }

    if (pickerOpen) {
        MonthPickerDialog(
            current = state.month,
            onSelect = { month ->
                pickerOpen = false
                viewModel.setMonth(month)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** 视图 tab（M8）：点击切换、不横滑（横滑留给各视图自己的翻页） */
@Composable
private fun CalendarModeTabs(selected: CalendarViewMode, onSelect: (CalendarViewMode) -> Unit) {
    val tabs = listOf(CalendarViewMode.MONTH to "月", CalendarViewMode.WEEK to "周", CalendarViewMode.YEAR to "年")
    PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0)) {
        tabs.forEach { (mode, label) ->
            Tab(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                text = { Text(label) },
            )
        }
    }
}

// ---------- 月视图 ----------

@Composable
private fun MonthView(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpenDate: (Long) -> Unit,
    onLongPressDate: (Long) -> Unit,
    onOpenPicker: () -> Unit,
) {
    val pagerState = rememberPagerState(
        initialPage = monthToPage(state.month),
        pageCount = { CALENDAR_MONTH_COUNT },
    )
    // 滑动停稳 → 同步 VM 月份（驱动数据窗口与标题）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { page -> viewModel.setMonth(pageToMonth(page)) }
    }
    // VM 月份变化（箭头 / 今天 / 年月选择器）→ 定位 pager
    LaunchedEffect(state.month) {
        val target = monthToPage(state.month)
        when {
            pagerState.currentPage == target -> Unit
            abs(target - pagerState.currentPage) <= PAGE_ANIMATE_RANGE ->
                pagerState.animateScrollToPage(target)
            else -> pagerState.scrollToPage(target)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.prevMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClickLabel = "选择年月", onClick = onOpenPicker)
                        .padding(vertical = 8.dp),
                ) {
                    Text(state.title, style = MaterialTheme.typography.titleMedium)
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { viewModel.nextMonth() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                }
            }
            Row(Modifier.fillMaxWidth()) {
                state.weekHeaders.forEach { header ->
                    Text(
                        header,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val pageMonth = pageToMonth(page)
                Column(Modifier.fillMaxWidth()) {
                    DayClock.monthGridRows(pageMonth, state.weekStart).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                    if (date != null) {
                                        DayCell(
                                            date = date,
                                            mark = state.marks[date],
                                            isToday = date == state.today,
                                            hasSummary = state.summaries.containsKey(date),
                                            onOpenDate = { onOpenDate(date) },
                                            onLongPress = { onLongPressDate(date) },
                                        )
                                    } else {
                                        Spacer(Modifier.height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Dot(Color(0xFF2E6B4A), 4.dp)
            Dot(Color(0xFF1565C0), 4.dp)
            Dot(Color(0xFFEF6C00), 4.dp)
            Spacer(Modifier.width(3.dp))
            Text(
                "已完成的选项（颜色对应选项）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Dot(MaterialTheme.colorScheme.tertiary, 5.dp)
            Text(
                "有备注",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "左右滑动切换月份 · 长按日期查看当日摘要",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- 周视图 ----------

@Composable
private fun WeekView(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpenDate: (Long) -> Unit,
    onLongPressDate: (Long) -> Unit,
) {
    val bounds = calendarWeekBounds(state.weekStart)
    val anchor = if (state.weekAnchor in bounds) state.weekAnchor else bounds.first
    val pagerState = rememberPagerState(
        initialPage = weekToPage(anchor, state.weekStart),
        pageCount = { weekPageCount(state.weekStart) },
    )
    // 滑动停稳 → 同步 VM 周锚点（驱动数据窗口与标题）
    LaunchedEffect(pagerState, state.weekStart) {
        snapshotFlow { pagerState.settledPage }
            .collect { page -> viewModel.setWeekAnchor(pageToWeekStart(page, state.weekStart)) }
    }
    // VM 周锚点变化（箭头 / 今天）→ 定位 pager
    LaunchedEffect(state.weekAnchor, state.weekStart) {
        val target = weekToPage(state.weekAnchor.coerceIn(bounds.first, bounds.last), state.weekStart)
        when {
            pagerState.currentPage == target -> Unit
            abs(target - pagerState.currentPage) <= PAGE_ANIMATE_RANGE ->
                pagerState.animateScrollToPage(target)
            else -> pagerState.scrollToPage(target)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.prevWeek() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一周")
                }
                Text(
                    state.weekTitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.nextWeek() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一周")
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                val pageAnchor = pageToWeekStart(page, state.weekStart)
                WeekPage(
                    anchor = pageAnchor,
                    today = state.today,
                    rows = state.weekRowsByAnchor[pageAnchor].orEmpty(),
                    summaries = state.summaries,
                    onOpenDate = onOpenDate,
                    onLongPressDate = onLongPressDate,
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        WeekLegendItem(WeekCellStatus.DONE, "完成")
        WeekLegendItem(WeekCellStatus.PARTIAL, "部分")
        WeekLegendItem(WeekCellStatus.PLANNED, "计划")
        WeekLegendItem(WeekCellStatus.SKIPPED, "跳过")
    }
    Spacer(Modifier.height(6.dp))
    Text(
        "左右滑动切换周 · 长按日期查看当日摘要",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun WeekPage(
    anchor: Long,
    today: Long,
    rows: List<WeekRow>,
    summaries: Map<Long, DaySummary>,
    onOpenDate: (Long) -> Unit,
    onLongPressDate: (Long) -> Unit,
) {
    val days = (anchor until anchor + 7).toList()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(WEEK_LABEL_WIDTH))
            days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        WEEKDAY_LABELS[LocalDate.ofEpochDay(day).dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .then(
                                if (day == today) {
                                    Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        Text(
                            LocalDate.ofEpochDay(day).dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (day == today) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        if (rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "没有可展示的选项",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .width(WEEK_LABEL_WIDTH)
                            .padding(end = 6.dp),
                    ) {
                        Box(Modifier.size(6.dp).background(Color(row.color), CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            row.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    row.cells.forEach { cell ->
                        val hasSummary = summaries.containsKey(cell.date)
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .combinedClickable(
                                    onLongClick = if (hasSummary) {
                                        ({ onLongPressDate(cell.date) })
                                    } else {
                                        null
                                    },
                                    onClick = { onOpenDate(cell.date) },
                                ),
                        ) {
                            WeekCellDot(cell.status, Color(row.color))
                        }
                    }
                }
            }
        }
    }
}

/** 周视图格子：完成=实心、部分=半透明、计划=空心描边、跳过=灰杠、空=占位 */
@Composable
private fun WeekCellDot(status: WeekCellStatus, color: Color) {
    when (status) {
        WeekCellStatus.DONE -> Box(Modifier.size(16.dp).background(color, CircleShape))
        WeekCellStatus.PARTIAL -> Box(Modifier.size(16.dp).background(color.copy(alpha = 0.4f), CircleShape))
        WeekCellStatus.PLANNED -> Box(Modifier.size(16.dp).border(1.5.dp, color, CircleShape))
        WeekCellStatus.SKIPPED -> Box(
            Modifier
                .width(12.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        )
        WeekCellStatus.NONE -> Spacer(Modifier.size(16.dp))
    }
}

@Composable
private fun WeekLegendItem(status: WeekCellStatus, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WeekCellDot(status, MaterialTheme.colorScheme.primary)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 年视图 ----------

@Composable
private fun YearView(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    onOpenDate: (Long) -> Unit,
    onLongPressDate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(
        initialPage = yearToPage(state.yearAnchor.coerceIn(CALENDAR_MIN_YEAR, CALENDAR_MAX_YEAR)),
        pageCount = { CALENDAR_YEAR_COUNT },
    )
    // 滑动停稳 → 同步 VM 年份（驱动数据窗口与标题）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .collect { page -> viewModel.setYear(pageToYear(page)) }
    }
    // VM 年份变化（箭头 / 今天）→ 定位 pager
    LaunchedEffect(state.yearAnchor) {
        val target = yearToPage(state.yearAnchor.coerceIn(CALENDAR_MIN_YEAR, CALENDAR_MAX_YEAR))
        when {
            pagerState.currentPage == target -> Unit
            abs(target - pagerState.currentPage) <= PAGE_ANIMATE_RANGE ->
                pagerState.animateScrollToPage(target)
            else -> pagerState.scrollToPage(target)
        }
    }

    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.prevYear() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一年")
                }
                Text(
                    state.yearTitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.nextYear() }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一年")
                }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val year = pageToYear(page)
                YearPage(
                    year = year,
                    today = state.today,
                    weekStart = state.weekStart,
                    grid = state.yearGridsByYear[year],
                    summaries = state.summaries,
                    onOpenDate = onOpenDate,
                    onLongPressDate = onLongPressDate,
                )
            }
            Spacer(Modifier.height(4.dp))
            YearLegend(summary = state.yearGridsByYear[state.yearAnchor]?.summary)
        }
    }
}

@Composable
private fun YearPage(
    year: Int,
    today: Long,
    weekStart: DayOfWeek,
    grid: YearGrid?,
    summaries: Map<Long, DaySummary>,
    onOpenDate: (Long) -> Unit,
    onLongPressDate: (Long) -> Unit,
) {
    if (grid == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val scrollState = rememberScrollState()
    val rowPx = with(LocalDensity.current) { YEAR_ROW_HEIGHT.toPx() }
    // 打开当年时滚到今天所在行附近（换年才重置）
    LaunchedEffect(year) {
        val rowIndex = grid.rows.indexOfFirst { row -> row.cells.any { it.inYear && it.date == today } }
        if (rowIndex >= 0) {
            snapshotFlow { scrollState.viewportSize }.first { it > 0 }
            val target = (rowIndex * rowPx - scrollState.viewportSize / 2f + rowPx / 2f)
                .toInt()
                .coerceAtLeast(0)
            scrollState.scrollTo(target)
        }
    }
    // 表头星期随周起始设置轮转
    val labels = (0 until 7).map { WEEKDAY_LABELS[(weekStart.value - 1 + it) % 7] }
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Spacer(Modifier.width(YEAR_GUTTER_WIDTH))
            labels.forEachIndexed { index, label ->
                if (index > 0) Spacer(Modifier.width(YEAR_CELL_GAP))
                Text(
                    label,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(YEAR_CELL_SIZE),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            grid.rows.forEach { row ->
                YearRowView(
                    row = row,
                    today = today,
                    summaries = summaries,
                    onOpenDate = onOpenDate,
                    onLongPressDate = onLongPressDate,
                )
            }
        }
    }
}

@Composable
private fun YearRowView(
    row: HeatRow,
    today: Long,
    summaries: Map<Long, DaySummary>,
    onOpenDate: (Long) -> Unit,
    onLongPressDate: (Long) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(YEAR_ROW_HEIGHT),
    ) {
        Box(Modifier.width(YEAR_GUTTER_WIDTH)) {
            row.monthStart?.let { month ->
                Text(
                    "${month}月",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val shape = RoundedCornerShape(6.dp)
        row.cells.forEachIndexed { index, cell ->
            if (index > 0) Spacer(Modifier.width(YEAR_CELL_GAP))
            if (!cell.inYear) {
                Spacer(Modifier.size(YEAR_CELL_SIZE))
            } else {
                Box(
                    modifier = Modifier
                        .size(YEAR_CELL_SIZE)
                        .clip(shape)
                        .background(heatColor(cell.level))
                        .then(
                            if (cell.date == today) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.tertiary, shape)
                            } else {
                                Modifier
                            }
                        )
                        .combinedClickable(
                            onLongClick = if (summaries.containsKey(cell.date)) {
                                ({ onLongPressDate(cell.date) })
                            } else {
                                null
                            },
                            onClick = { onOpenDate(cell.date) },
                        ),
                )
            }
        }
    }
}

/** 热力图色阶：0 = 淡灰空档；1..4 = primary 由浅到深（4 = 4 项及以上） */
@Composable
private fun heatColor(level: Int): Color = when (level) {
    0 -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    1 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    2 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    3 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)
    else -> MaterialTheme.colorScheme.primary
}

/** 年视图底部：强度图例 + 「环 = 今天」+ 全年汇总（筛选内） */
@Composable
private fun YearLegend(summary: YearSummary?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            "少",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        (0..YearHeatmap.MAX_LEVEL).forEach { level ->
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(heatColor(level))
            )
            Spacer(Modifier.width(2.dp))
        }
        Text(
            "多",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            "环 = 今天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(2.dp))
    Text(
        if (summary != null) {
            "全年完成 ${summary.doneCount} 项次 · 打卡 ${summary.activeDays} 天"
        } else {
            "暂无数据"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- 共用组件 ----------

/** 年月选择器（D32）：年步进 + 3×4 月网格，点月即跳转 */
@Composable
private fun MonthPickerDialog(
    current: YearMonth,
    onSelect: (YearMonth) -> Unit,
    onDismiss: () -> Unit,
) {
    var year by remember { mutableIntStateOf(current.year) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = { year-- },
                    enabled = year > CALENDAR_MIN_MONTH.year,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一年")
                }
                Text("$year 年", style = MaterialTheme.typography.titleMedium)
                IconButton(
                    onClick = { year++ },
                    enabled = year < CALENDAR_MAX_MONTH.year,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一年")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (0 until 4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (1..3).forEach { column ->
                            val monthValue = row * 3 + column
                            MonthCell(
                                monthValue = monthValue,
                                selected = year == current.year && monthValue == current.monthValue,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(YearMonth.of(year, monthValue)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun MonthCell(monthValue: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val background = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val textColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text("${monthValue}月", style = MaterialTheme.typography.bodyMedium, color = textColor)
    }
}

@Composable
private fun DayCell(
    date: Long,
    mark: DayMarks?,
    isToday: Boolean,
    hasSummary: Boolean,
    onOpenDate: () -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                onLongClick = if (hasSummary) onLongPress else null,
                onClick = onOpenDate,
            )
            .padding(vertical = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(28.dp)
                .then(
                    if (isToday) {
                        Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                ),
        ) {
            Text(
                LocalDate.ofEpochDay(date).dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark?.dotColors?.forEach { color -> Dot(Color(color), 5.dp) }
            if ((mark?.overflow ?: 0) > 0) {
                Text(
                    "+${mark!!.overflow}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (mark?.hasNote == true) {
                Dot(MaterialTheme.colorScheme.tertiary, 5.dp)
            }
        }
    }
}

@Composable
private fun Dot(color: Color, size: Dp) {
    Box(Modifier.size(size).background(color, CircleShape))
}

/** 长按摘要对话框：记录条目 + 休息日 + 日期备注；「查看详情」进入日期详情页 */
@Composable
private fun DaySummaryDialog(
    date: Long,
    summary: DaySummary,
    onOpenDetail: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dayLabel(date)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.entries.forEach { entry -> SummaryEntryRow(entry) }
                if (summary.skipNames.isNotEmpty()) {
                    Text(
                        "休息日：${summary.skipNames.joinToString("、")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                summary.noteText?.let { note ->
                    Text(
                        "备注：$note",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpenDetail) { Text("查看详情") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun SummaryEntryRow(entry: SummaryEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ItemBadge(icon = entry.icon, color = entry.color, size = 28.dp, iconSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RecordStatusPill(entry.status)
        val valueText = entryValueText(entry)
        when {
            valueText != null -> Text(
                valueText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            entry.status == RecordStatus.DONE &&
                entry.type == CheckType.BOOLEAN -> Icon(
                Icons.Filled.Check,
                contentDescription = "已完成",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** 布尔型只表达完成与否，不展示数值 */
private fun entryValueText(entry: SummaryEntry): String? =
    if (entry.type == CheckType.BOOLEAN) null else entry.value?.let { "${formatValue(it)}${entry.unit}" }

private fun dayLabel(date: Long): String =
    LocalDate.ofEpochDay(date).format(DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA))
