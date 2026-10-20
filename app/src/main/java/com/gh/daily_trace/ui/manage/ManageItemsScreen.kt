@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
)

package com.gh.daily_trace.ui.manage

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.di.Container
import com.gh.daily_trace.ui.common.EmptyState
import com.gh.daily_trace.ui.common.ItemBadge
import com.gh.daily_trace.ui.common.SelectionActionBar
import com.gh.daily_trace.ui.common.rememberSelectedIds
import kotlinx.coroutines.launch

@Composable
fun ManageItemsRoute(container: Container, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel: ManageItemsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ManageItemsViewModel(container.repository, container.reminderScheduler) }
        }
    )
    ManageItemsScreen(viewModel = viewModel, onBack = onBack, onEdit = onEdit)
}

@Composable
fun ManageItemsScreen(viewModel: ManageItemsViewModel, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val sections by viewModel.uiState.collectAsState()
    var actionTarget by remember { mutableStateOf<CheckItem?>(null) }
    var purgeTarget by remember { mutableStateOf<CheckItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // 多选批量操作（长按行进入；一次只在一个分区内选择）
    var selectionSection by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIds by rememberSelectedIds()
    var purgeBatch by remember { mutableStateOf(false) }

    val sectionRows: Map<String, List<ManageRow>> = mapOf(
        "使用中" to sections.active,
        "已停用" to sections.disabled,
        "已归档" to sections.archived,
        "回收站" to sections.trashed,
    )
    val activeRows = selectionSection?.let { sectionRows[it] }.orEmpty()
    val allSelected = activeRows.isNotEmpty() && activeRows.all { it.item.id in selectedIds }

    val exitSelection: () -> Unit = {
        selectionSection = null
        selectedIds = emptySet()
    }
    BackHandler(enabled = selectionSection != null) { exitSelection() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (selectionSection != null) {
                        Text("已选 ${selectedIds.size} 项")
                    } else {
                        Text("选项管理")
                    }
                },
                navigationIcon = {
                    if (selectionSection != null) {
                        IconButton(onClick = exitSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "退出多选")
                        }
                    } else {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (selectionSection != null) {
                        TextButton(onClick = {
                            selectedIds = if (allSelected) emptySet() else activeRows.map { it.item.id }.toSet()
                        }) {
                            Text(if (allSelected) "取消全选" else "全选")
                        }
                    } else {
                        IconButton(onClick = { onEdit(0L) }) {
                            Icon(Icons.Filled.Add, contentDescription = "新建选项")
                        }
                    }
                },
            )
        },
        bottomBar = {
            val section = selectionSection
            if (section != null) {
                SelectionActionBar {
                    when (section) {
                        "回收站" -> {
                            FilledTonalButton(
                                onClick = {
                                    val ids = selectedIds
                                    viewModel.restoreAll(ids)
                                    exitSelection()
                                    scope.launch { snackbarHostState.showSnackbar("已恢复 ${ids.size} 个选项") }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("恢复") }
                            Button(
                                onClick = { purgeBatch = true },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) { Text("彻底删除") }
                        }
                        "已归档" -> {
                            FilledTonalButton(
                                onClick = {
                                    val ids = selectedIds
                                    viewModel.archiveAll(ids, archived = false)
                                    exitSelection()
                                    scope.launch { snackbarHostState.showSnackbar("已取消归档 ${ids.size} 个选项") }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("取消归档") }
                            Button(
                                onClick = {
                                    val ids = selectedIds
                                    viewModel.trashAll(ids)
                                    exitSelection()
                                    scope.launch { snackbarHostState.showSnackbar("已移入回收站 ${ids.size} 个选项") }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) { Text("移入回收站") }
                        }
                        else -> {
                            FilledTonalButton(
                                onClick = {
                                    val ids = selectedIds
                                    viewModel.archiveAll(ids, archived = true)
                                    exitSelection()
                                    scope.launch { snackbarHostState.showSnackbar("已归档 ${ids.size} 个选项") }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                            ) { Text("归档") }
                            Button(
                                onClick = {
                                    val ids = selectedIds
                                    viewModel.trashAll(ids)
                                    exitSelection()
                                    scope.launch { snackbarHostState.showSnackbar("已移入回收站 ${ids.size} 个选项") }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                ),
                            ) { Text("移入回收站") }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (sections.isEmpty) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    emoji = "🧩",
                    title = "还没有打卡选项",
                    description = "创建第一个选项，开始记录每天的小坚持",
                    actionLabel = "新建选项",
                    onAction = { onEdit(0L) },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 4.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                manageSection(
                    title = "使用中",
                    rows = sections.active,
                    selectionSection = selectionSection,
                    selectedIds = selectedIds,
                    onClick = { actionTarget = it },
                    onLongPress = { selectionSection = "使用中"; selectedIds = setOf(it.id) },
                    onToggleSelect = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                )
                manageSection(
                    title = "已停用",
                    rows = sections.disabled,
                    selectionSection = selectionSection,
                    selectedIds = selectedIds,
                    onClick = { actionTarget = it },
                    onLongPress = { selectionSection = "已停用"; selectedIds = setOf(it.id) },
                    onToggleSelect = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                )
                manageSection(
                    title = "已归档",
                    rows = sections.archived,
                    selectionSection = selectionSection,
                    selectedIds = selectedIds,
                    onClick = { actionTarget = it },
                    onLongPress = { selectionSection = "已归档"; selectedIds = setOf(it.id) },
                    onToggleSelect = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                )
                manageSection(
                    title = "回收站",
                    rows = sections.trashed,
                    selectionSection = selectionSection,
                    selectedIds = selectedIds,
                    onClick = { actionTarget = it },
                    onLongPress = { selectionSection = "回收站"; selectedIds = setOf(it.id) },
                    onToggleSelect = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
                )
            }
        }
    }

    actionTarget?.let { item ->
        ItemActionsDialog(
            item = item,
            viewModel = viewModel,
            onEdit = onEdit,
            onDismiss = { actionTarget = null },
            onPurgeRequest = { purgeTarget = it },
        )
    }

    purgeTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { purgeTarget = null },
            title = { Text("彻底删除") },
            text = { Text("「${item.name}」将从回收站彻底删除，历史打卡记录保留。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = { viewModel.purge(item); purgeTarget = null }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { purgeTarget = null }) { Text("取消") } },
        )
    }

    if (purgeBatch) {
        AlertDialog(
            onDismissRequest = { purgeBatch = false },
            title = { Text("彻底删除") },
            text = { Text("所选的 ${selectedIds.size} 个选项将从回收站彻底删除，历史打卡记录保留。此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedIds
                    viewModel.purgeAll(ids)
                    purgeBatch = false
                    exitSelection()
                    scope.launch { snackbarHostState.showSnackbar("已彻底删除 ${ids.size} 个选项") }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { purgeBatch = false }) { Text("取消") } },
        )
    }
}

private fun LazyListScope.manageSection(
    title: String,
    rows: List<ManageRow>,
    selectionSection: String?,
    selectedIds: Set<Long>,
    onClick: (CheckItem) -> Unit,
    onLongPress: (CheckItem) -> Unit,
    onToggleSelect: (Long) -> Unit,
) {
    if (rows.isEmpty()) return
    val inSelection = selectionSection == title
    item(key = "header-$title") {
        Text(
            "$title（${rows.size}）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
        )
    }
    items(rows, key = { "row-${it.item.id}" }) { row ->
        ManageItemRow(
            row = row,
            selecting = inSelection,
            selected = row.item.id in selectedIds,
            onClick = { if (inSelection || selectionSection == null) onClick(row.item) },
            onLongPress = { onLongPress(row.item) },
            onToggleSelect = { onToggleSelect(row.item.id) },
        )
    }
}

@Composable
private fun ManageItemRow(
    row: ManageRow,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (selecting) onToggleSelect() else onClick() },
                onLongClick = { if (!selecting) onLongPress() },
            ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (selecting) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelect() })
                Spacer(Modifier.width(4.dp))
            }
            ItemBadge(
                icon = row.item.icon,
                color = row.item.color,
                size = 36.dp,
                iconSize = 18.sp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.item.name, style = MaterialTheme.typography.titleMedium)
                val subtitle = buildString {
                    append(typeLabel(row.item.type))
                    if (row.item.unit.isNotBlank()) append(" · ${row.item.unit}")
                    row.groupName?.let { append(" · $it") }
                    if (row.item.deletedAt != null) append(" · 回收站")
                }
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selecting) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ItemActionsDialog(
    item: CheckItem,
    viewModel: ManageItemsViewModel,
    onEdit: (Long) -> Unit,
    onDismiss: () -> Unit,
    onPurgeRequest: (CheckItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ItemBadge(icon = item.icon, color = item.color, size = 32.dp, iconSize = 16.sp)
                Spacer(Modifier.width(10.dp))
                Text(item.name)
            }
        },
        text = {
            Column {
                if (item.deletedAt != null) {
                    DialogActionRow("♻️", "恢复") { viewModel.restore(item); onDismiss() }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DialogActionRow("❗", "彻底删除", danger = true) { onPurgeRequest(item); onDismiss() }
                } else {
                    DialogActionRow("✏️", "编辑") { onEdit(item.id); onDismiss() }
                    if (!item.archived) {
                        DialogActionRow("⬆️", "上移") { viewModel.moveUp(item); onDismiss() }
                        DialogActionRow("⬇️", "下移") { viewModel.moveDown(item); onDismiss() }
                    }
                    DialogActionRow(
                        if (item.enabled) "⏸️" else "▶️",
                        if (item.enabled) "停用" else "启用",
                    ) { viewModel.setEnabled(item, !item.enabled); onDismiss() }
                    DialogActionRow(
                        if (item.archived) "↩️" else "📦",
                        if (item.archived) "取消归档" else "归档",
                    ) { viewModel.setArchived(item, !item.archived); onDismiss() }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    DialogActionRow("🗑️", "移入回收站", danger = true) {
                        viewModel.moveToTrash(item); onDismiss()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun DialogActionRow(
    emoji: String,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        Text(emoji, fontSize = 16.sp)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun typeLabel(type: CheckType): String = when (type) {
    CheckType.BOOLEAN -> "打卡"
    CheckType.COUNT -> "计数"
    CheckType.DURATION -> "时长"
    CheckType.VALUE -> "数值"
}
