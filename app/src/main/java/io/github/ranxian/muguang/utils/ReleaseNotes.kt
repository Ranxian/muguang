package io.github.ranxian.muguang.utils

/**
 * 应用内「更新日志」的正文清洗。
 *
 * ## 为什么要重写
 *
 * 旧实现只保留**标题里带「新增 / 功能 / 修复 / BUG」的段落**。但发行说明的实际写法
 * 是平铺的 bullet 列表（1.0、1.1 都是这样），整篇没有那种标题——于是展开后一个字都
 * 不剩，界面显示「本版本未填写更新详情」。用户反馈的「没写更新详情」就是这个。
 *
 * ## 现在的规则
 *
 * 不做语义筛选，只做「去掉发行说明的样板行 + 去 Markdown 强调符」：
 * - 丢掉平台正文的样板行：`沐光 MUGUANG v…` 标题行、`安装包：…` 资源行、
 *   以 `>` 开头的引用（更新检查说明 / 售后群）——这些在应用内没有信息量
 * - 去掉行首的 `#` 标题井号（App 里是普通 Text，不解析 Markdown）
 * - 去掉 `**` / `__` / 反引号
 * - 超过 [MAX_CHARS] 截断并加省略号，避免超长发行说明把弹窗/卡片撑爆
 */
object ReleaseNotes {

    /** 展开后最多展示的字数（够看完一次更新说明，又不至于把页面拉得找不到按钮） */
    const val MAX_CHARS = 1500

    fun extract(body: String): String {
        if (body.isBlank()) return ""
        val cleaned = body.lines()
            .map { raw -> raw.trimEnd().replace(Regex("^#+\\s*"), "") }
            .filterNot { line ->
                val t = line.trim()
                t.startsWith("沐光 MUGUANG") ||
                    t.startsWith("安装包：") ||
                    t.startsWith(">")
            }
            .joinToString("\n")
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
        return if (cleaned.length <= MAX_CHARS) cleaned else cleaned.take(MAX_CHARS).trimEnd() + "…"
    }
}
