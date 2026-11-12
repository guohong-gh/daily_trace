package com.gh.daily_trace.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 最新版本查询。
 *
 * 只用系统 [HttpURLConnection]，不引入第三方网络库；请求不携带任何设备标识，
 * 也不上传任何本地数据。任何失败（断网、超时、限流、非 200、解析异常）都返回 null，
 * 由调用方按「无更新」静默处理。
 */
class UpdateChecker(
    private val userAgent: String,
    private val endpoint: String = DEFAULT_ENDPOINT,
) {

    fun fetchLatestTag(): String? = try {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                parseTagName(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                null
            }
        } finally {
            connection.disconnect()
        }
    } catch (_: Exception) {
        null
    }

    companion object {
        const val DEFAULT_ENDPOINT =
            "https://api.github.com/repos/guohong-gh/daily_trace/releases/latest"

        /** 下载页：应用内不做下载与安装，统一跳浏览器 */
        const val RELEASES_PAGE = "https://github.com/guohong-gh/daily_trace/releases/latest"

        /** 解析 tag_name；draft / prerelease 与非法 JSON 一律视为无更新 */
        fun parseTagName(json: String): String? = runCatching {
            val obj = JSONObject(json)
            if (obj.optBoolean("draft") || obj.optBoolean("prerelease")) return@runCatching null
            obj.optString("tag_name").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
