package com.gh.daily_trace.update

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** GitHub Releases 响应解析：只认 tag_name，draft / prerelease / 非法 JSON 一律忽略 */
@RunWith(AndroidJUnit4::class)
class UpdateResponseTest {

    @Test
    fun `解析 tag_name`() {
        val json = """{"tag_name":"v1.2.0","name":"日迹 v1.2.0","draft":false,"prerelease":false}"""
        assertEquals("v1.2.0", UpdateChecker.parseTagName(json))
    }

    @Test
    fun `缺少 tag_name 返回 null`() {
        assertNull(UpdateChecker.parseTagName("""{"name":"no tag"}"""))
    }

    @Test
    fun `空 tag_name 返回 null`() {
        assertNull(UpdateChecker.parseTagName("""{"tag_name":"  "}"""))
    }

    @Test
    fun `draft 忽略`() {
        assertNull(UpdateChecker.parseTagName("""{"tag_name":"v1.2.0","draft":true}"""))
    }

    @Test
    fun `prerelease 忽略`() {
        assertNull(UpdateChecker.parseTagName("""{"tag_name":"v1.2.0","prerelease":true}"""))
    }

    @Test
    fun `非法 JSON 返回 null`() {
        assertNull(UpdateChecker.parseTagName("not json"))
        assertNull(UpdateChecker.parseTagName(""))
    }
}
