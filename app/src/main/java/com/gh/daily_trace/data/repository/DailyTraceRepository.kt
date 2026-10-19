package com.gh.daily_trace.data.repository

import com.gh.daily_trace.data.dao.AppSettingsDao
import com.gh.daily_trace.data.dao.CheckItemDao
import com.gh.daily_trace.data.dao.CheckRecordDao
import com.gh.daily_trace.data.dao.DayNoteDao
import com.gh.daily_trace.data.dao.FilterViewDao
import com.gh.daily_trace.data.dao.FrequencyRuleDao
import com.gh.daily_trace.data.dao.GroupDao
import com.gh.daily_trace.data.dao.ReminderDao
import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.Reminder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** 单一数据入口：封装各 DAO，向上层（ViewModel）暴露 Flow */
class DailyTraceRepository(
    private val checkItemDao: CheckItemDao,
    private val checkRecordDao: CheckRecordDao,
    private val dayNoteDao: DayNoteDao,
    private val groupDao: GroupDao,
    private val reminderDao: ReminderDao,
    private val filterViewDao: FilterViewDao,
    private val frequencyRuleDao: FrequencyRuleDao,
    private val appSettingsDao: AppSettingsDao
) {

    // ---------- 打卡选项 ----------
    val items: Flow<List<CheckItem>> = checkItemDao.observeAll()
    val activeItems: Flow<List<CheckItem>> = checkItemDao.observeActive()
    val archivedItems: Flow<List<CheckItem>> = checkItemDao.observeArchived()
    val trashedItems: Flow<List<CheckItem>> = checkItemDao.observeTrashed()

    suspend fun getItem(id: Long): CheckItem? = checkItemDao.getById(id)
    fun observeItem(id: Long): Flow<CheckItem?> = checkItemDao.observeById(id)
    suspend fun insertItem(item: CheckItem): Long = checkItemDao.insert(item)
    suspend fun updateItem(item: CheckItem) = checkItemDao.update(item)
    suspend fun setItemArchived(id: Long, archived: Boolean) = checkItemDao.setArchived(id, archived)
    suspend fun softDeleteItem(id: Long) = checkItemDao.softDelete(id)
    suspend fun restoreItem(id: Long) = checkItemDao.restore(id)
    suspend fun purgeItem(id: Long) = checkItemDao.hardDelete(id)
    suspend fun setItemSort(id: Long, sort: Int) = checkItemDao.setSort(id, sort)
    suspend fun getAllItems(): List<CheckItem> = checkItemDao.getAll()

    // ---------- 打卡记录 ----------
    fun observeRecordsOn(date: Long): Flow<List<CheckRecord>> = checkRecordDao.observeByDate(date)
    fun observeRecordsBetween(start: Long, end: Long): Flow<List<CheckRecord>> =
        checkRecordDao.observeBetween(start, end)

    /** 时间线「加载更早」：date 之前是否还有记录或备注 */
    fun observeHasContentBefore(date: Long): Flow<Boolean> = combine(
        checkRecordDao.observeExistsBefore(date),
        dayNoteDao.observeExistsBefore(date),
    ) { hasRecords, hasNotes -> hasRecords || hasNotes }

    val allRecords: Flow<List<CheckRecord>> = checkRecordDao.observeAll()
    fun observeRecordsOfItem(itemId: Long): Flow<List<CheckRecord>> = checkRecordDao.observeByItem(itemId)

    suspend fun getRecordsOn(date: Long): List<CheckRecord> = checkRecordDao.getFromDate(date)
    suspend fun getRecordsBetween(start: Long, end: Long): List<CheckRecord> = checkRecordDao.getBetween(start, end)
    suspend fun getRecordsOfItemOn(itemId: Long, date: Long): List<CheckRecord> =
        checkRecordDao.getForItemOnDate(itemId, date)

    suspend fun insertRecord(record: CheckRecord): Long = checkRecordDao.insert(record)
    suspend fun insertRecords(records: List<CheckRecord>) = checkRecordDao.insertAll(records)
    suspend fun updateRecord(record: CheckRecord) = checkRecordDao.update(record)

    /** 批量改备注（覆盖式；空串即清除） */
    suspend fun updateRecordsNote(ids: List<Long>, note: String, now: Long) =
        checkRecordDao.updateNoteByIds(ids, note, now)

    /** 截至指定日期的最近一条记录（批量记录沿用值用） */
    suspend fun getLatestRecordUpTo(itemId: Long, date: Long): CheckRecord? =
        checkRecordDao.getLatestUpTo(itemId, date)

    suspend fun deleteRecord(id: Long) = checkRecordDao.delete(id)
    suspend fun deleteRecords(ids: List<Long>) = checkRecordDao.deleteByIds(ids)
    suspend fun getAllRecords(): List<CheckRecord> = checkRecordDao.getAll()
    suspend fun searchRecords(query: String): List<CheckRecord> = checkRecordDao.search(query)

    // ---------- 日期整体备注 ----------
    fun observeDayNote(date: Long): Flow<DayNote?> = dayNoteDao.observeByDate(date)
    fun observeDayNotesBetween(start: Long, end: Long): Flow<List<DayNote>> =
        dayNoteDao.observeBetween(start, end)

    suspend fun getDayNote(date: Long): DayNote? = dayNoteDao.getByDate(date)
    suspend fun saveDayNote(note: DayNote) = dayNoteDao.upsert(note)
    suspend fun deleteDayNote(date: Long) = dayNoteDao.delete(date)
    suspend fun searchDayNotes(query: String): List<DayNote> = dayNoteDao.search(query)

    // ---------- 分组 ----------
    val groups: Flow<List<Group>> = groupDao.observeAll()
    suspend fun getAllGroups(): List<Group> = groupDao.getAll()
    suspend fun insertGroup(group: Group): Long = groupDao.insert(group)
    suspend fun updateGroup(group: Group) = groupDao.update(group)
    suspend fun deleteGroup(id: Long) = groupDao.delete(id)

    // ---------- 提醒 ----------
    val reminders: Flow<List<Reminder>> = reminderDao.observeAll()
    fun observeReminderOf(itemId: Long): Flow<Reminder?> = reminderDao.observeForItem(itemId)
    suspend fun getReminderOf(itemId: Long): Reminder? = reminderDao.getForItem(itemId)
    suspend fun saveReminder(reminder: Reminder) = reminderDao.upsert(reminder)
    suspend fun setReminderEnabled(itemId: Long, enabled: Boolean) = reminderDao.setEnabled(itemId, enabled)
    suspend fun deleteReminderOf(itemId: Long) = reminderDao.deleteForItem(itemId)
    suspend fun getEnabledReminders(): List<Reminder> = reminderDao.getEnabled()

    // ---------- 筛选视图 ----------
    val filterViews: Flow<List<FilterView>> = filterViewDao.observeAll()
    suspend fun saveFilterView(view: FilterView) = filterViewDao.upsert(view)
    suspend fun deleteFilterView(id: Long) = filterViewDao.delete(id)

    // ---------- 频率规则 ----------
    val frequencyRules: Flow<List<FrequencyRule>> = frequencyRuleDao.observeAll()
    fun observeFrequencyOf(itemId: Long): Flow<FrequencyRule?> = frequencyRuleDao.observeForItem(itemId)
    suspend fun getFrequencyOf(itemId: Long): FrequencyRule? = frequencyRuleDao.getForItem(itemId)
    suspend fun saveFrequency(rule: FrequencyRule) = frequencyRuleDao.upsert(rule)
    suspend fun deleteFrequencyOf(itemId: Long) = frequencyRuleDao.deleteForItem(itemId)

    // ---------- 设置 ----------
    val settings: Flow<AppSettings> = appSettingsDao.observe().map { it ?: AppSettings() }
    suspend fun getSettings(): AppSettings = appSettingsDao.get() ?: AppSettings()
    suspend fun saveSettings(settings: AppSettings) = appSettingsDao.upsert(settings)
}
