package io.github.ranxian.muguang.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import io.github.ranxian.muguang.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CutCornerShape
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.theme.AppColors

/**
 * 悬浮胶囊底栏：单一高亮滑块跟随选中项，弹簧阻尼产生轻微"果冻"回弹。
 * 玻璃质感由半透明底色 + 微光描边 + 阴影构成。
 *
 * 交互：**点击**某个 tab 即切换，滑块以弹簧动画滑动到位。
 */
@Composable
fun FloatingPillNavBar(
    currentTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = AppColors.isDark
    val p = Design.palette
    val sf = Design.surface
    val pillShape = if (Design.shapes.cutCorner) {
        CutCornerShape(topEnd = 12.dp, bottomStart = 12.dp)
    } else {
        RoundedCornerShape(28.dp)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // ── 液态毛玻璃底座 ──
        // 胶囊原来直接浮在照片上，底栏那一整条露的就是原图；抽屉收起后悬浮把手
        // 又单独飘在图上也显得突兀。底座把这一条统一压成玻璃面：上沿略透
        // （透出底色一点点色相，像磨过），往下收成接近实色 ——
        // 抽屉被拖到半开、内部行经过这里时也不会透出来。
        Box(
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .background(
                    Brush.verticalGradient(
                        0f to p.surface.copy(alpha = if (isDark) 0.86f else 0.90f),
                        0.5f to p.surface.copy(alpha = if (isDark) 0.93f else 0.95f),
                        1f to p.surface.copy(alpha = if (isDark) 0.97f else 0.98f)
                    )
                )
        ) {
            // 上沿发丝线：没有它整条玻璃面会和上面的内容糊成一片
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(p.hairline)
            )
        }
        Surface(
            shape = pillShape,
            // 深色下留一点透明度，让页面底色透上来；浅色下用实色纸面，
            // 描边已经足够勾轮廓，再叠阴影会脏。
            color = if (isDark) p.surface.copy(alpha = 0.88f) else p.surface,
            border = if (sf.bordered) BorderStroke(1.dp, p.hairline) else null,
            shadowElevation = if (sf.shadow > 0.dp) 8.dp else 0.dp,
            tonalElevation = 0.dp,
            modifier = Modifier
                .padding(bottom = 22.dp, start = 24.dp, end = 24.dp)
                .width(260.dp)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
                val itemWidth = maxWidth / 3

                // 滑块位移：与 MudLife 相同的果冻弹簧动画
                val animatedOffset by animateDpAsState(
                    targetValue = itemWidth * currentTab,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
                    label = "SpringPillOffset"
                )

                // 高亮滑块
                Surface(
                    modifier = Modifier
                        .offset(x = animatedOffset)
                        .width(itemWidth).height(42.dp),
                    shape = RoundedCornerShape(22.dp),
                    // 跟随强调色（会随自定义背景提取的主题色变化）。
                    //
                    // ⚠️ 深色模式以前写死成 #A8C7FA，注释还写着「跟随强调色」——
                    // 结果自定义背景提取出来的主题色只在浅色下生效，一到深色就变回那个默认蓝。
                    // 现在两边都用 AppColors.Accent，深浅只影响下面图标文字的取色。
                    color = AppColors.Accent,
                    shadowElevation = 0.dp
                ) {}

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NavBarTabItem(Modifier.weight(1f), "首页", rememberVectorPainter(Icons.Default.Home), currentTab == 0, isDark) {
                        if (currentTab != 0) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(0)
                        }
                    }
                    NavBarTabItem(Modifier.weight(1f), "账单", painterResource(R.drawable.ic_receipt_long), currentTab == 1, isDark) {
                        if (currentTab != 1) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(1)
                        }
                    }
                    NavBarTabItem(Modifier.weight(1f), "我的", rememberVectorPainter(Icons.Default.Person), currentTab == 2, isDark) {
                        if (currentTab != 2) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTabSelected(2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBarTabItem(
    modifier: Modifier = Modifier,
    label: String,
    // 用 Painter 而不是 ImageVector：账单图标已经换成项目自己的 vector drawable，
    // 不再从 material-icons-extended 取（那个库太大）
    icon: Painter,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    // 选中项的文字/图标压在滑块上，取色得看**滑块本身的明暗**——
    // 滑块现在是强调色，可能来自自定义背景提取，什么颜色都有可能，
    // 写死深蓝/白都可能糊成一片。按亮度挑黑或白。
    // 滑块强调色可能来自自定义背景提取，什么颜色都有可能：
    // 亮底压 ink，暗底压 accentInk，两边都由风格包保证对比度。
    val palette = Design.palette
    val activeColor = if (AppColors.Accent.luminance() > 0.5f) palette.ink else palette.accentInk
    val inactiveColor = palette.inkSubtle
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) activeColor else inactiveColor,
        animationSpec = tween(200),
        label = "TabContentColor"
    )
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.04f else 1.0f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "TabScale"
    )
    // 与 MudLife 一致：无点击水波纹、无按压缩放，仅靠滑块弹簧滑动作为反馈
    Box(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .scale(scale),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = contentColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}
