package com.gh.daily_trace.domain.update

/**
 * 版本号比较（纯函数）。
 *
 * 口径：容忍 `v` / `V` 前缀与 1~3 段数字（缺段补 0），不支持预发布后缀；
 * 任何格式异常都返回 null，由调用方按「无更新」静默处理。
 */
object VersionCompare {

    /** 解析为三段数字；非法返回 null */
    fun parse(raw: String?): List<Int>? {
        val text = raw?.trim()?.removePrefix("v")?.removePrefix("V") ?: return null
        if (text.isEmpty()) return null
        val parts = text.split('.')
        if (parts.size !in 1..3) return null
        val nums = parts.map { it.toIntOrNull() ?: return null }
        if (nums.any { it < 0 }) return null
        return nums + List(3 - nums.size) { 0 }
    }

    /** 正数表示 remote 更新；0 相同；null 表示任一版本非法 */
    fun compare(remote: String?, current: String?): Int? {
        val r = parse(remote) ?: return null
        val c = parse(current) ?: return null
        for (i in 0..2) {
            if (r[i] != c[i]) return r[i] - c[i]
        }
        return 0
    }

    fun isNewer(remote: String?, current: String?): Boolean = (compare(remote, current) ?: 0) > 0

    /**
     * 自动弹窗节流：远端必须严格高于上次已提示的版本。
     * 未提示过或记录损坏时允许提示；远端版本非法时不提示。
     */
    fun shouldPrompt(remote: String?, lastPrompted: String?): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(lastPrompted) ?: return true
        for (i in 0..2) {
            if (r[i] != l[i]) return r[i] > l[i]
        }
        return false
    }
}
