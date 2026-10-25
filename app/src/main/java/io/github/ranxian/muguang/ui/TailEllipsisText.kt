package io.github.ranxian.muguang.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private const val LEADING_ELLIPSIS = "…"

/** 放不下时最多缩到原字号的这个比例，再小就不体面了，改为省略开头 */
private const val MIN_SCALE = 0.78f

/**
 * 单行文本：优先缩小字号，实在放不下才省略**开头**。
 *
 * 为什么不用 `TextOverflow.Ellipsis`：
 * 1. 它省略的是**结尾**，而设备名 `龙川北苑 3号楼南 320房` 的辨识度全在结尾的房号上
 * 2. 系统字体被调大后，普通 Text 要么换行要么把结尾砍掉，两种都很难看
 *
 * 所以策略是三级：**原字号 → 逐级缩到 78% → 头部省略**。
 * 缩字号优先，因为"字小一点但看得全"比"字大但缺一半"信息量更大。
 *
 * ⚠️ 测量必须用 `LocalTextStyle` 合并后的样式：直接拿自己的 [TextStyle] 去量，
 * 会和 Text 实际渲染的样式差一个 letterSpacing / fontFamily，量出来偏窄，
 * 结果就是文字真的溢出容器（之前首页设备名盖到按钮上就是这个原因）。
 */
@Composable
fun TailEllipsisText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null
) {
    val measurer = rememberTextMeasurer()
    val ambient = LocalTextStyle.current
    val baseSize = if (fontSize != TextUnit.Unspecified) fontSize
                   else ambient.fontSize.takeIf { it != TextUnit.Unspecified } ?: 14.sp

    BoxWithConstraints(modifier) {
        val maxWidth = constraints.maxWidth
        val result = remember(text, maxWidth, color, fontSize, fontWeight, ambient) {
            val fits = { s: String, size: TextUnit ->
                maxWidth <= 0 || maxWidth == Int.MAX_VALUE ||
                    measurer.measure(
                        s,
                        ambient.merge(TextStyle(color = color, fontSize = size, fontWeight = fontWeight))
                    ).size.width <= maxWidth
            }

            // ① 原字号能放下
            if (fits(text, baseSize)) {
                text to baseSize
            } else {
                // ② 逐级缩小字号
                val shrunk = generateSequence(baseSize.value * 0.92f) { it * 0.92f }
                    .takeWhile { it >= baseSize.value * MIN_SCALE }
                    .firstOrNull { fits(text, it.sp) }
                if (shrunk != null) {
                    text to shrunk.sp
                } else {
                    // ③ 缩到下限还放不下 → 二分找最长的后缀 + 前导省略号
                    var lo = 0
                    var hi = text.length
                    while (lo < hi) {
                        val mid = (lo + hi + 1) / 2
                        if (fits(LEADING_ELLIPSIS + text.takeLast(mid), baseSize * MIN_SCALE)) lo = mid
                        else hi = mid - 1
                    }
                    (LEADING_ELLIPSIS + text.takeLast(lo)) to (baseSize * MIN_SCALE)
                }
            }
        }

        Text(
            text = result.first,
            color = color,
            fontSize = result.second,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            // 前面已经量准了，Clip 只是保险：万一还有偏差也不会画到隔壁控件上
            overflow = TextOverflow.Clip
        )
    }
}

private operator fun TextUnit.times(f: Float): TextUnit = (value * f).sp
