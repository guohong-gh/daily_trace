package com.gh.daily_trace.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较与自动弹窗节流 */
class VersionCompareTest {

    @Test
    fun `相同版本返回 0`() {
        assertEquals(0, VersionCompare.compare("1.0.0", "1.0.0"))
        assertEquals(0, VersionCompare.compare("v1.0.0", "V1.0.0"))
    }

    @Test
    fun `远端更高返回正数`() {
        assertTrue((VersionCompare.compare("1.1.0", "1.0.0") ?: 0) > 0)
        assertTrue((VersionCompare.compare("2.0.0", "1.99.99") ?: 0) > 0)
        assertTrue((VersionCompare.compare("1.0.1", "1.0.0") ?: 0) > 0)
    }

    @Test
    fun `远端更低返回负数`() {
        assertTrue((VersionCompare.compare("0.9.9", "1.0.0") ?: 0) < 0)
        assertFalse(VersionCompare.isNewer("0.9.9", "1.0.0"))
    }

    @Test
    fun `缺 v 前缀与多余 v 前缀都按同版本处理`() {
        assertEquals(0, VersionCompare.compare("v1.2.3", "1.2.3"))
        assertEquals(0, VersionCompare.compare("1.2.3", "v1.2.3"))
    }

    @Test
    fun `缺段补零`() {
        assertEquals(0, VersionCompare.compare("1.1", "1.1.0"))
        assertEquals(0, VersionCompare.compare("v2", "2.0.0"))
        assertTrue(VersionCompare.isNewer("1.2", "1.1.9"))
    }

    @Test
    fun `非法格式返回 null 且不视为新版本`() {
        assertNull(VersionCompare.compare("abc", "1.0.0"))
        assertNull(VersionCompare.compare("1.0.0", ""))
        assertNull(VersionCompare.compare("1.2.3.4", "1.0.0"))
        assertNull(VersionCompare.compare("1.x.0", "1.0.0"))
        assertNull(VersionCompare.compare("1.0.0-beta", "1.0.0"))
        assertNull(VersionCompare.compare(null, "1.0.0"))
        assertFalse(VersionCompare.isNewer("abc", "1.0.0"))
    }

    @Test
    fun `未提示过时允许提示`() {
        assertTrue(VersionCompare.shouldPrompt("v1.1.0", null))
    }

    @Test
    fun `同版本不重复提示`() {
        assertFalse(VersionCompare.shouldPrompt("v1.1.0", "v1.1.0"))
        assertFalse(VersionCompare.shouldPrompt("1.1.0", "v1.1.0"))
    }

    @Test
    fun `更高版本重新允许提示`() {
        assertTrue(VersionCompare.shouldPrompt("v1.2.0", "v1.1.0"))
    }

    @Test
    fun `更低版本不提示`() {
        assertFalse(VersionCompare.shouldPrompt("v1.0.0", "v1.1.0"))
    }

    @Test
    fun `提示记录损坏时按未提示处理`() {
        assertTrue(VersionCompare.shouldPrompt("v1.1.0", "oops"))
    }

    @Test
    fun `远端版本非法不提示`() {
        assertFalse(VersionCompare.shouldPrompt("not-a-version", null))
    }
}
