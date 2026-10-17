package com.gh.daily_trace.ui.itemedit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.data.repository.DailyTraceRepository
import com.gh.daily_trace.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val DEFAULT_COLOR = 0xFF2E7D32.toInt()

data class ItemEditState(
    val isNew: Boolean = true,
    val loaded: Boolean = false,
    val name: String = "",
    val icon: String = "",
    val color: Int = DEFAULT_COLOR,
    val type: CheckType = CheckType.BOOLEAN,
    val unit: String = "",
    val targetText: String = "",
    val defaultNote: String = "",
    val groupId: Long? = null,
    val rule: MultiRecordRule = MultiRecordRule.KEEP_MULTIPLE,
    val enabled: Boolean = true,
    val archived: Boolean = false,
    // 提醒
    val reminderEnabled: Boolean = false,
    val reminderTime: Int = 8 * 60,
    val reminderRepeat: RepeatType = RepeatType.DAILY,
    /** ISO 1=周一..7=周日，WEEKLY 时生效 */
    val reminderDays: List<Int> = emptyList(),
    val reminderSnooze: Int = 10,
    val dndEnabled: Boolean = false,
    val dndStart: Int = 22 * 60,
    val dndEnd: Int = 7 * 60,
    // 权限状态（页面回前台时刷新）
    val notificationsAllowed: Boolean = true,
    val exactAlarmAllowed: Boolean = true,
    val groups: List<Group> = emptyList(),
    /** 保存/删除等操作完成，界面应返回上一页 */
    val done: Boolean = false,
    val error: String? = null,
)

/** 选项新建/编辑。itemId <= 0 表示新建。 */
class ItemEditViewModel(
    private val repository: DailyTraceRepository,
    private val scheduler: ReminderScheduler,
    private val itemId: Long,
) : ViewModel() {

    private val _state = MutableStateFlow(ItemEditState(isNew = itemId <= 0))
    val state: StateFlow<ItemEditState> = _state

    private var base: CheckItem? = null

    init {
        viewModelScope.launch {
            val groups = repository.getAllGroups()
            if (itemId <= 0) {
                _state.value = _state.value.copy(groups = groups, loaded = true)
                return@launch
            }
            val item = repository.getItem(itemId)
            if (item == null) {
                _state.value = _state.value.copy(groups = groups, loaded = true, error = "选项不存在")
            } else {
                base = item
                val reminder = repository.getReminderOf(itemId)
                val rule = repository.getFrequencyOf(itemId)
                _state.value = _state.value.copy(
                    groups = groups,
                    loaded = true,
                    isNew = false,
                    name = item.name,
                    icon = item.icon,
                    color = item.color,
                    type = item.type,
                    unit = item.unit,
                    targetText = item.targetValue?.let { plainNumber(it) } ?: "",
                    defaultNote = item.defaultNote,
                    groupId = item.groupId,
                    rule = item.multiRecordRule,
                    enabled = item.enabled,
                    archived = item.archived,
                    reminderEnabled = reminder?.enabled == true,
                    reminderTime = reminder?.time ?: 8 * 60,
                    reminderRepeat = reminder?.repeat ?: RepeatType.DAILY,
                    reminderDays = rule?.daysOfWeek.orEmpty(),
                    reminderSnooze = reminder?.snooze ?: 10,
                    dndEnabled = reminder?.dndStart != null && reminder.dndEnd != null,
                    dndStart = reminder?.dndStart ?: 22 * 60,
                    dndEnd = reminder?.dndEnd ?: 7 * 60,
                )
            }
        }
    }

    fun update(transform: (ItemEditState) -> ItemEditState) {
        _state.value = transform(_state.value).copy(error = null)
    }

    fun save() {
        val s = _state.value
        val name = s.name.trim()
        if (name.isEmpty()) {
            update { it.copy(error = "名称不能为空") }
            return
        }
        val target = if (s.type == CheckType.BOOLEAN) null else s.targetText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (s.type != CheckType.BOOLEAN && s.targetText.isNotBlank() && target == null) {
            update { it.copy(error = "目标值必须是数字") }
            return
        }
        if (s.reminderEnabled && s.reminderRepeat == RepeatType.WEEKLY && s.reminderDays.isEmpty()) {
            update { it.copy(error = "请选择每周的提醒日") }
            return
        }

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val unit = if (s.type == CheckType.BOOLEAN) "" else s.unit.trim()
            val b = base
            val resolvedId: Long
            if (s.isNew || b == null) {
                resolvedId = repository.insertItem(
                    CheckItem(
                        name = name,
                        color = s.color,
                        icon = s.icon.trim(),
                        type = s.type,
                        unit = unit,
                        targetValue = target,
                        defaultNote = s.defaultNote.trim(),
                        groupId = s.groupId,
                        multiRecordRule = s.rule,
                        enabled = s.enabled,
                        sort = (repository.getAllItems().maxOfOrNull { it.sort } ?: -1) + 1,
                        createdAt = now,
                        updatedAt = now,
                    )
                )
            } else {
                repository.updateItem(
                    b.copy(
                        name = name,
                        color = s.color,
                        icon = s.icon.trim(),
                        type = s.type,
                        unit = unit,
                        targetValue = target,
                        defaultNote = s.defaultNote.trim(),
                        groupId = s.groupId,
                        multiRecordRule = s.rule,
                        enabled = s.enabled,
                        updatedAt = now,
                    )
                )
                resolvedId = itemId
            }
            applyReminder(resolvedId, s, now)
            scheduler.rescheduleAll()
            _state.value = _state.value.copy(done = true)
        }
    }

    /** 保存提醒设置：启用则 upsert；禁用则删除并取消闹钟。WEEKLY 的天数写入频率规则。 */
    private suspend fun applyReminder(resolvedItemId: Long, s: ItemEditState, now: Long) {
        val existing = repository.getReminderOf(resolvedItemId)
        if (!s.reminderEnabled) {
            if (existing != null) {
                scheduler.cancel(existing.id)
                repository.deleteReminderOf(resolvedItemId)
            }
            return
        }
        repository.saveReminder(
            Reminder(
                id = existing?.id ?: 0,
                itemId = resolvedItemId,
                time = s.reminderTime.coerceIn(0, 1439),
                repeat = s.reminderRepeat,
                enabled = true,
                snooze = s.reminderSnooze.coerceIn(1, 1440),
                dndStart = if (s.dndEnabled) s.dndStart.coerceIn(0, 1439) else null,
                dndEnd = if (s.dndEnabled) s.dndEnd.coerceIn(0, 1439) else null,
                updatedAt = now,
            )
        )
        if (s.reminderRepeat == RepeatType.WEEKLY) {
            val rule = repository.getFrequencyOf(resolvedItemId)
                ?: FrequencyRule(itemId = resolvedItemId)
            repository.saveFrequency(
                rule.copy(daysOfWeek = s.reminderDays.distinct().sorted(), updatedAt = now)
            )
        }
    }

    /** 页面回前台时刷新权限状态（用于提示与引导按钮） */
    fun setPermissions(notificationsAllowed: Boolean, exactAlarmAllowed: Boolean) {
        _state.value = _state.value.copy(
            notificationsAllowed = notificationsAllowed,
            exactAlarmAllowed = exactAlarmAllowed,
        )
    }

    fun toggleArchived() {
        val b = base ?: return
        viewModelScope.launch {
            val target = !b.archived
            repository.setItemArchived(itemId, target)
            base = b.copy(archived = target)
            _state.value = _state.value.copy(archived = target)
            scheduler.rescheduleAll()
        }
    }

    fun moveToTrash() {
        viewModelScope.launch {
            repository.softDeleteItem(itemId)
            scheduler.rescheduleAll()
            _state.value = _state.value.copy(done = true)
        }
    }
}

private fun plainNumber(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
