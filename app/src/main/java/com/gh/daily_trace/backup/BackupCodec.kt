package com.gh.daily_trace.backup

import com.gh.daily_trace.data.model.AppSettings
import com.gh.daily_trace.data.model.CheckItem
import com.gh.daily_trace.data.model.CheckRecord
import com.gh.daily_trace.data.model.CheckType
import com.gh.daily_trace.data.model.DayFilter
import com.gh.daily_trace.data.model.DayNote
import com.gh.daily_trace.data.model.FilterLogic
import com.gh.daily_trace.data.model.FilterMode
import com.gh.daily_trace.data.model.FilterView
import com.gh.daily_trace.data.model.FrequencyRule
import com.gh.daily_trace.data.model.FrequencyType
import com.gh.daily_trace.data.model.Group
import com.gh.daily_trace.data.model.MultiRecordRule
import com.gh.daily_trace.data.model.RecordSource
import com.gh.daily_trace.data.model.RecordStatus
import com.gh.daily_trace.data.model.Reminder
import com.gh.daily_trace.data.model.RepeatType
import com.gh.daily_trace.data.model.ThemeMode
import org.json.JSONArray
import org.json.JSONObject

/** 备份数据格式不正确（解析失败 / 非本应用备份 / 版本不支持） */
class BackupFormatException(message: String) : Exception(message)

/** 备份快照（内存表示）：导出与导入共用 */
data class BackupData(
    val exportedAt: Long,
    val items: List<CheckItem>,
    val records: List<CheckRecord>,
    val dayNotes: List<DayNote>,
    val groups: List<Group>,
    val reminders: List<Reminder>,
    val filterViews: List<FilterView>,
    val frequencyRules: List<FrequencyRule>,
    val settings: AppSettings?,
)

/**
 * JSON 备份编解码（org.json，无第三方依赖）。
 * 字段名与实体属性一一对应；枚举存 name；列表存数组；可空字段存 null。
 * 解析采用"必需字段缺失即失败 + 可选字段带默认值"，保证向前兼容。
 */
object BackupCodec {

    const val FORMAT = "daily_trace.backup"
    const val VERSION = 1

    // ---------- 编码 ----------

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", data.exportedAt)
        root.put("items", JSONArray().apply { data.items.forEach { put(encodeItem(it)) } })
        root.put("records", JSONArray().apply { data.records.forEach { put(encodeRecord(it)) } })
        root.put("dayNotes", JSONArray().apply { data.dayNotes.forEach { put(encodeDayNote(it)) } })
        root.put("groups", JSONArray().apply { data.groups.forEach { put(encodeGroup(it)) } })
        root.put("reminders", JSONArray().apply { data.reminders.forEach { put(encodeReminder(it)) } })
        root.put("filterViews", JSONArray().apply { data.filterViews.forEach { put(encodeFilterView(it)) } })
        root.put("frequencyRules", JSONArray().apply { data.frequencyRules.forEach { put(encodeRule(it)) } })
        root.put("settings", data.settings?.let { encodeSettings(it) } ?: JSONObject.NULL)
        return root.toString()
    }

    private fun encodeItem(i: CheckItem) = JSONObject().apply {
        put("id", i.id)
        put("name", i.name)
        put("color", i.color)
        put("icon", i.icon)
        put("type", i.type.name)
        put("unit", i.unit)
        put("targetValue", i.targetValue ?: JSONObject.NULL)
        put("defaultNote", i.defaultNote)
        put("groupId", i.groupId ?: JSONObject.NULL)
        put("multiRecordRule", i.multiRecordRule.name)
        put("sort", i.sort)
        put("enabled", i.enabled)
        put("archived", i.archived)
        put("deletedAt", i.deletedAt ?: JSONObject.NULL)
        put("createdAt", i.createdAt)
        put("updatedAt", i.updatedAt)
    }

    private fun encodeRecord(r: CheckRecord) = JSONObject().apply {
        put("id", r.id)
        put("itemId", r.itemId)
        put("date", r.date)
        put("value", r.value ?: JSONObject.NULL)
        put("unit", r.unit)
        put("note", r.note)
        put("status", r.status.name)
        put("source", r.source.name)
        put("isBackfill", r.isBackfill)
        put("timezone", r.timezone)
        put("createdAt", r.createdAt)
        put("updatedAt", r.updatedAt)
        put("itemNameSnapshot", r.itemNameSnapshot)
        put("itemColorSnapshot", r.itemColorSnapshot)
        put("itemIconSnapshot", r.itemIconSnapshot)
    }

    private fun encodeDayNote(n: DayNote) = JSONObject().apply {
        put("date", n.date)
        put("note", n.note)
        put("createdAt", n.createdAt)
        put("updatedAt", n.updatedAt)
    }

    private fun encodeGroup(g: Group) = JSONObject().apply {
        put("id", g.id)
        put("name", g.name)
        put("color", g.color)
        put("sort", g.sort)
    }

    private fun encodeReminder(r: Reminder) = JSONObject().apply {
        put("id", r.id)
        put("itemId", r.itemId)
        put("time", r.time)
        put("repeat", r.repeat.name)
        put("daysOfWeek", JSONArray(r.daysOfWeek))
        put("enabled", r.enabled)
        put("snooze", r.snooze)
        put("dndStart", r.dndStart ?: JSONObject.NULL)
        put("dndEnd", r.dndEnd ?: JSONObject.NULL)
        put("updatedAt", r.updatedAt)
    }

    private fun encodeFilterView(v: FilterView) = JSONObject().apply {
        put("id", v.id)
        put("name", v.name)
        put("mode", v.mode.name)
        put("itemIds", JSONArray(v.itemIds))
        put("groupIds", JSONArray(v.groupIds))
        put("logic", v.logic.name)
        put("dayFilter", v.dayFilter.name)
        put("createdAt", v.createdAt)
    }

    private fun encodeRule(rule: FrequencyRule) = JSONObject().apply {
        put("id", rule.id)
        put("itemId", rule.itemId)
        put("type", rule.type.name)
        put("daysOfWeek", JSONArray(rule.daysOfWeek))
        put("timesPerMonth", rule.timesPerMonth ?: JSONObject.NULL)
        put("timesPerWeek", rule.timesPerWeek ?: JSONObject.NULL)
        put("intervalDays", rule.intervalDays ?: JSONObject.NULL)
        put("anchorEpochDay", rule.anchorEpochDay ?: JSONObject.NULL)
        put("targetValue", rule.targetValue ?: JSONObject.NULL)
        put("allowSkip", rule.allowSkip)
        put("skipDates", JSONArray(rule.skipDates))
        put("updatedAt", rule.updatedAt)
    }

    private fun encodeSettings(s: AppSettings) = JSONObject().apply {
        put("theme", s.theme.name)
        put("dynamicColor", s.dynamicColor)
        put("weekStart", s.weekStart)
        put("startOfDay", s.startOfDay)
        put("timezone", s.timezone)
        put("dateLanguage", s.dateLanguage.name)
        put("dateFormat", s.dateFormat.name)
        put("colorBlindMode", s.colorBlindMode)
        put("maxBackfillDays", s.maxBackfillDays)
        put("partialCountsAsDone", s.partialCountsAsDone)
        put("autoBackup", s.autoBackup)
        put("autoBackupIntervalDays", s.autoBackupIntervalDays)
        put("encryption", s.encryption)
        put("pausedUntil", s.pausedUntil ?: JSONObject.NULL)
        put("dailySummaryEnabled", s.dailySummaryEnabled)
        put("dailySummaryTime", s.dailySummaryTime)
    }

    // ---------- 解析 ----------

    fun parse(json: String): BackupData {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BackupFormatException("不是有效的备份文件（JSON 解析失败）")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("不是本应用的备份文件")
        }
        val version = root.optInt("version", 0)
        if (version < 1 || version > VERSION) {
            throw BackupFormatException("备份版本 $version 不受支持（当前支持到 $VERSION）")
        }
        return try {
            BackupData(
                exportedAt = root.optLong("exportedAt", 0L),
                items = root.arrayOf("items").map { decodeItem(it) },
                records = root.arrayOf("records").map { decodeRecord(it) },
                dayNotes = root.arrayOf("dayNotes").map { decodeDayNote(it) },
                groups = root.arrayOf("groups").map { decodeGroup(it) },
                reminders = root.arrayOf("reminders").map { decodeReminder(it) },
                filterViews = root.arrayOf("filterViews").map { decodeFilterView(it) },
                frequencyRules = root.arrayOf("frequencyRules").map { decodeRule(it) },
                settings = if (root.isNull("settings")) null else decodeSettings(root.getJSONObject("settings")),
            )
        } catch (e: BackupFormatException) {
            throw e
        } catch (e: Exception) {
            throw BackupFormatException("备份内容不完整：${e.message}")
        }
    }

    private fun JSONObject.arrayOf(key: String): List<JSONObject> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    private fun decodeItem(o: JSONObject) = CheckItem(
        id = o.getLong("id"),
        name = o.getString("name"),
        color = o.getInt("color"),
        icon = o.optString("icon", ""),
        type = enumOr(o.str("type"), CheckType.BOOLEAN),
        unit = o.optString("unit", ""),
        targetValue = o.nullableDouble("targetValue"),
        defaultNote = o.optString("defaultNote", ""),
        groupId = o.nullableLong("groupId"),
        multiRecordRule = enumOr(o.str("multiRecordRule"), MultiRecordRule.KEEP_MULTIPLE),
        sort = o.optInt("sort", 0),
        enabled = o.optBoolean("enabled", true),
        archived = o.optBoolean("archived", false),
        deletedAt = o.nullableLong("deletedAt"),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    private fun decodeRecord(o: JSONObject) = CheckRecord(
        id = o.getLong("id"),
        itemId = o.getLong("itemId"),
        date = o.getLong("date"),
        value = o.nullableDouble("value"),
        unit = o.optString("unit", ""),
        note = o.optString("note", ""),
        status = enumOr(o.str("status"), RecordStatus.DONE),
        source = enumOr(o.str("source"), RecordSource.MANUAL),
        isBackfill = o.optBoolean("isBackfill", false),
        timezone = o.optString("timezone", ""),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
        itemNameSnapshot = o.optString("itemNameSnapshot", ""),
        itemColorSnapshot = o.optInt("itemColorSnapshot", 0),
        itemIconSnapshot = o.optString("itemIconSnapshot", ""),
    )

    private fun decodeDayNote(o: JSONObject) = DayNote(
        date = o.getLong("date"),
        note = o.getString("note"),
        createdAt = o.optLong("createdAt", 0L),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    private fun decodeGroup(o: JSONObject) = Group(
        id = o.getLong("id"),
        name = o.getString("name"),
        color = o.getInt("color"),
        sort = o.optInt("sort", 0),
    )

    private fun decodeReminder(o: JSONObject) = Reminder(
        id = o.getLong("id"),
        itemId = o.getLong("itemId"),
        time = o.getInt("time"),
        repeat = enumOr(o.str("repeat"), RepeatType.DAILY),
        daysOfWeek = o.intList("daysOfWeek"),
        enabled = o.optBoolean("enabled", true),
        snooze = o.optInt("snooze", 10),
        dndStart = o.nullableInt("dndStart"),
        dndEnd = o.nullableInt("dndEnd"),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    private fun decodeFilterView(o: JSONObject) = FilterView(
        id = o.getLong("id"),
        name = o.getString("name"),
        mode = enumOr(o.str("mode"), FilterMode.ALL),
        itemIds = o.longList("itemIds"),
        groupIds = o.longList("groupIds"),
        logic = enumOr(o.str("logic"), FilterLogic.ANY),
        dayFilter = enumOr(o.str("dayFilter"), DayFilter.ALL),
        createdAt = o.optLong("createdAt", 0L),
    )

    private fun decodeRule(o: JSONObject) = FrequencyRule(
        id = o.getLong("id"),
        itemId = o.getLong("itemId"),
        type = enumOr(o.str("type"), FrequencyType.DAILY),
        daysOfWeek = o.intList("daysOfWeek"),
        timesPerMonth = o.nullableInt("timesPerMonth"),
        timesPerWeek = o.nullableInt("timesPerWeek"),
        intervalDays = o.nullableInt("intervalDays"),
        anchorEpochDay = o.nullableLong("anchorEpochDay"),
        targetValue = o.nullableDouble("targetValue"),
        allowSkip = o.optBoolean("allowSkip", true),
        skipDates = o.longList("skipDates"),
        updatedAt = o.optLong("updatedAt", 0L),
    )

    private fun decodeSettings(o: JSONObject): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = enumOr(o.str("theme"), d.theme),
            dynamicColor = o.optBoolean("dynamicColor", d.dynamicColor),
            weekStart = o.optInt("weekStart", d.weekStart),
            startOfDay = o.optInt("startOfDay", d.startOfDay),
            timezone = o.optString("timezone", d.timezone),
            dateLanguage = enumOr(o.str("dateLanguage"), d.dateLanguage),
            dateFormat = enumOr(o.str("dateFormat"), d.dateFormat),
            colorBlindMode = o.optBoolean("colorBlindMode", d.colorBlindMode),
            maxBackfillDays = o.optInt("maxBackfillDays", d.maxBackfillDays),
            partialCountsAsDone = o.optBoolean("partialCountsAsDone", d.partialCountsAsDone),
            autoBackup = o.optBoolean("autoBackup", d.autoBackup),
            autoBackupIntervalDays = o.optInt("autoBackupIntervalDays", d.autoBackupIntervalDays),
            encryption = o.optBoolean("encryption", d.encryption),
            pausedUntil = o.nullableLong("pausedUntil"),
            dailySummaryEnabled = o.optBoolean("dailySummaryEnabled", d.dailySummaryEnabled),
            dailySummaryTime = o.optInt("dailySummaryTime", d.dailySummaryTime),
        )
    }

    // ---------- org.json 小工具 ----------

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.nullableLong(key: String): Long? =
        if (has(key) && !isNull(key)) getLong(key) else null

    private fun JSONObject.nullableInt(key: String): Int? =
        if (has(key) && !isNull(key)) getInt(key) else null

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null

    private fun JSONObject.intList(key: String): List<Int> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getInt(it) }
    }

    private fun JSONObject.longList(key: String): List<Long> {
        val arr = optJSONArray(key) ?: return emptyList()
        return (0 until arr.length()).map { arr.getLong(it) }
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
}
