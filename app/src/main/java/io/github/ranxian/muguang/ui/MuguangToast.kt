package io.github.ranxian.muguang.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranxian.muguang.R
import io.github.ranxian.muguang.ui.design.Design
import kotlinx.coroutines.delay

internal fun isToastVisible(timerVisible: Boolean, message: String?): Boolean =
    timerVisible && !message.isNullOrEmpty()

@Composable
fun MuguangToast(
    message: String?,
    onDismiss: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        if (!message.isNullOrEmpty()) {
            visible = true
            delay(2500)
            visible = false
            delay(300)
            onDismiss()
        } else {
            visible = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = isToastVisible(visible, message),
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 60.dp, start = 24.dp, end = 24.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    // 浅色风格下浮层不能用深灰：深灰底 + 深色底纹在 GPT 的暖白页面上像一块补丁。
                    // 浮层统一用 ink 作底、canvas 作字，两种模式都能保证最大对比度，
                    // 且不需要为四套风格各写一个颜色。
                    .background(Design.palette.ink)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp)
                    )
                    // ⚠️ 不能直接用普通 Text：它没有 maxLines，长文本（比如
                    // 「已绑定寝室：龙川北苑-3号楼南-3层-320」）会**换行**，
                    // 气泡变成一个方块，弹出来的位置也跟着跳。
                    //
                    // TailEllipsisText 是单行的（maxLines = 1, softWrap = false）：
                    // 先逐级缩字号，实在放不下才省略**开头**。
                    // 这里要的正是省略开头——寝室名有辨识度的是结尾的「-3层-320」，
                    // 从尾巴截就只剩「已绑定寝室：龙川北苑…」，等于什么都没说。
                    //
                    // weight(fill = false) 给它一个**有上限的宽度约束**，
                    // 否则 BoxWithConstraints 量到的是 Int.MAX_VALUE，永远判定"放得下"。
                    TailEllipsisText(
                        text = message ?: "",
                        color = Design.palette.canvas,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}
