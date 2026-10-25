package io.github.ranxian.muguang.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 更新检查的唯一判据：Release 的 tag 是否比当前版本新。
 *
 * 这段逻辑错了的后果很具体——用户会反复收到同一个版本，
 * 或者永远收不到（发过预发布之后 `1.2.0-rc1` 被当成比 `1.2.0` 新就是后者）。
 */
class ApkUpdaterVersionTest {

    @Test
    fun `同版本不算新`() {
        assertFalse(ApkUpdater.isNewer("v1.2.0", "v1.2.0"))
        assertFalse(ApkUpdater.isNewer("1.2.0", "v1.2.0"))
        assertFalse(ApkUpdater.isNewer("v1.2", "v1.2.0"))
    }

    @Test
    fun `补丁位提升算新`() {
        assertTrue(ApkUpdater.isNewer("v1.2.1", "v1.2.0"))
        assertFalse(ApkUpdater.isNewer("v1.2.0", "v1.2.1"))
    }

    @Test
    fun `多位数不能按字符串比大小`() {
        // 字符串比较会把 "1.9.0" 排在 "1.10.0" 前面
        assertTrue(ApkUpdater.isNewer("v1.10.0", "v1.9.0"))
        assertFalse(ApkUpdater.isNewer("v1.9.0", "v1.10.0"))
    }

    @Test
    fun `主版本提升压过次版本`() {
        assertTrue(ApkUpdater.isNewer("v2.0.0", "v1.99.99"))
        assertFalse(ApkUpdater.isNewer("v1.99.99", "v2.0.0"))
    }

    @Test
    fun `预发布不算比正式版新`() {
        assertFalse(ApkUpdater.isNewer("v1.2.0-rc1", "v1.2.0"))
        assertFalse(ApkUpdater.isNewer("v1.2.0-beta.3", "v1.2.0"))
        assertFalse(ApkUpdater.isNewer("v1.2.0+build.7", "v1.2.0"))
    }

    @Test
    fun `预发布仍能识别真实的新版本`() {
        assertTrue(ApkUpdater.isNewer("v1.3.0-rc1", "v1.2.0"))
    }

    @Test
    fun `缺位的段按 0 处理`() {
        assertFalse(ApkUpdater.isNewer("v1.2", "v1.2.0"))
        assertTrue(ApkUpdater.isNewer("v1.2.0.1", "v1.2.0"))
    }

    @Test
    fun `无法解析的 tag 不会误报`() {
        assertFalse(ApkUpdater.isNewer("nightly", "v1.2.0"))
        assertFalse(ApkUpdater.isNewer("", "v1.2.0"))
    }
}
