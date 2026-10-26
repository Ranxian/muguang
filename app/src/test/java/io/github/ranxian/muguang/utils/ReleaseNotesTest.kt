package io.github.ranxian.muguang.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「更新日志」正文清洗的回归测试。
 *
 * 旧实现只保留标题带「新增/功能/修复」的段落——而 1.0 / 1.1 的发行说明是平铺列表，
 * 于是展开后显示「本版本未填写更新详情」。用户反馈的「没写更新详情」就是这个。
 */
class ReleaseNotesTest {

    @Test
    fun `平铺列表也要有内容（旧实现的 bug 回归）`() {
        val body = """
            沐光 MUGUANG v1.1（arm64-v8a，Android 8.0+）

            安装包：`muguang-1.1-arm64-v8a.apk`

            - 洗衣机可同时绑定两台，首页逐行显示
            - 绑定寝室后自动检测热水器
            - 移除主页刷新按钮

            > 官方售后群（QQ）：1126843866
        """.trimIndent()
        val text = ReleaseNotes.extract(body)
        assertTrue(text.isNotBlank())
        assertTrue(text.contains("洗衣机可同时绑定两台"))
        assertTrue(text.contains("自动检测热水器"))
    }

    @Test
    fun `标题井号与 Markdown 强调符被去掉`() {
        val body = "### 本次修复\n- 检查更新改为**双源**，附件 `size` 可能缺失"
        val text = ReleaseNotes.extract(body)
        assertFalse(text.contains("#"))
        assertFalse(text.contains("**"))
        assertFalse(text.contains("`"))
        assertTrue(text.startsWith("本次修复"))
    }

    @Test
    fun `发行说明样板行被丢掉`() {
        val body = "沐光 MUGUANG v1.1.1（arm64-v8a）\n安装包：`x.apk`\n正文要点\n> 售后群 1126843866"
        val text = ReleaseNotes.extract(body)
        assertEquals("正文要点", text)
    }

    @Test
    fun `超长正文截断并加省略号`() {
        val body = "- " + "很长的更新说明".repeat(400)
        val text = ReleaseNotes.extract(body)
        assertTrue(text.length <= ReleaseNotes.MAX_CHARS + 1)
        assertTrue(text.endsWith("…"))
    }

    @Test
    fun `空白正文返回空串`() {
        assertEquals("", ReleaseNotes.extract(""))
        assertEquals("", ReleaseNotes.extract("   \n  "))
    }
}
