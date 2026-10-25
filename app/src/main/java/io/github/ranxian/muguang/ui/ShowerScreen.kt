package io.github.ranxian.muguang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranxian.muguang.ui.design.AmbientGlow
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.design.GlowAlignment
import io.github.ranxian.muguang.ui.design.pressFeedback
import io.github.ranxian.muguang.ui.design.rememberPressInteraction
import io.github.ranxian.muguang.ui.design.cardShape
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.ui.theme.inkOnFill
import io.github.ranxian.muguang.utils.BackgroundManager
import io.github.ranxian.muguang.utils.BackgroundState

@Composable
fun ShowerScreen(
    emoji: String,
    statusText: String,
    location: String,
    remaining: String,
    elapsedSec: Int,
    autoDisConSec: Int = 0,
    isStopping: Boolean = false,
    onStopClick: () -> Unit,
    onMinimizeClick: () -> Unit = {}
) {
    BackHandler(onBack = onMinimizeClick)
    val minutes = elapsedSec / 60
    val seconds = elapsedSec % 60
    val timeText = if (minutes > 0) "${minutes}分${seconds}秒" else "${seconds}秒"

    // 使用页是否套用了自定义背景
    val customBg = BackgroundState.config(BackgroundManager.SCOPE_SHOWER).enabled

    // 背景应用到「使用页」时不再画自身底色，让底层背景图透出来
    val bgModifier = if (customBg) Modifier else Modifier.background(Design.palette.canvas)

    Box(modifier = Modifier.fillMaxSize().then(bgModifier)) {
        // 原来的洗手台是固定深蓝渐变，换风格后底色必须跟着令牌走，
        // 否则切到暖色/浅色风格时只有这一页还是蓝的。
        // 顶部留一层很淡的光晕把视觉重心压在设备图标上，不压卡片和按钮。
        if (!customBg) {
            AmbientGlow(
                tokens = Design.motion.glow,
                modifier = Modifier.fillMaxWidth().height(320.dp),
                alignment = GlowAlignment.Top
            )
        }
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 套了自定义背景就不画 emoji：背景图本身就是画面主体，
            // 再叠一个 72sp 的表情符号会直接盖在图上，很突兀。
            // 只针对自定义背景；默认渐变背景保留 emoji（洗手台 / 卫生间都一样）
            if (!customBg) {
                // 尺寸保持 72 —— 原来 emoji 是 72sp，图标给 72dp 视觉体量才对得上。
                // ⚠️ 图标本身是白色（首页压在设备类型色圆底上），这里没有底色，
                // 白图标放到浅色 canvas 上几乎看不见，所以补一层强调色淡底并按令牌给图标上色。
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(Design.palette.accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    DeviceGlyph(
                        emoji,
                        emojiSize = 72.sp,
                        iconSize = 72.dp,
                        tint = Design.palette.accent
                    )
                }

                Spacer(Modifier.height(8.dp))
            }

            Text(statusText, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.Accent)

            Spacer(Modifier.height(4.dp))

            Text(location, style = MaterialTheme.typography.bodyLarge, color = AppColors.TextSecondary)

            Spacer(Modifier.height(32.dp))

            // 预扣金额卡片
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape(),
                color = AppColors.Card,
                // 原来描边是压在白玻璃上的「白色微光」，底色换成令牌后白色描边在浅色风格里几乎看不见；
                // 改为按风格决定：需要描边的用 hairline，不需要的（GPT / Apple）留空
                border = if (Design.surface.bordered) {
                    BorderStroke(1.dp, Design.palette.hairline)
                } else null,
                shadowElevation = 0.dp // 去掉阴影：半透明卡片叠在光晕上时阴影会显脏
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("已预扣 ¥$remaining", fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, color = AppColors.Accent)
                    Spacer(Modifier.height(4.dp))
                    Text("计费以热水器显示为准", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }

            Spacer(Modifier.height(24.dp))

            // 计时器
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Card.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent // 背景已由 Modifier 绘制，避免 Surface 默认的不透明底色盖住
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⏱", fontSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("已用 $timeText", fontSize = 16.sp,
                        fontWeight = FontWeight.Medium, color = AppColors.TextPrimary)
                }
            }

            // 自动关停倒计时
            if (autoDisConSec > 0) {
                val dMin = autoDisConSec / 60
                val dSec = autoDisConSec % 60
                val countdownText = if (dMin > 0) "${dMin}分${dSec}秒" else "${dSec}秒"
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Warning.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent // 同上：背景由 Modifier 绘制
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⏳", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("闲置约 $countdownText 后自动关闭",
                            fontSize = 13.sp,
                            color = AppColors.Warning)
                    }
                }
            }

            Spacer(Modifier.height(48.dp))

            // 按压变形：和组件库的 PrimaryAction 同一套弹簧（幅度由风格包决定）
            val pressInteraction = rememberPressInteraction()
            // 结束用水是破坏性操作，底色取令牌的 danger（固定红在方舟的深底上会偏亮，
            // 而 Claude 的纸感底需要更暗的红才不刺眼）。
            //
            // 但字**不能**写死白色：白字压 danger 在 iOS（3.55:1）、极简深色（2.77:1）、
            // 暖纸深色（3.02:1）、终端（3.06:1）都不达 4.5:1。这里按实测挑字色 ——
            // 令牌自带的 accentInk 够用就用它（深色风格里它是近黑，
            // 「亮底 + 深字」本来也是这套令牌的既有配法），不够才退到黑白里更强的一侧。
            val stopFill = Design.palette.danger
            val stopInk = inkOnFill(stopFill, Design.palette.accentInk)
            Button(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .pressFeedback(pressInteraction, enabled = !isStopping),
                interactionSource = pressInteraction,
                enabled = !isStopping,
                colors = ButtonDefaults.buttonColors(
                    containerColor = stopFill,
                    contentColor = stopInk,
                    // 禁用态不再把底色压到 40% 透明度：danger@0.4 叠在浅色画布上会变成
                    // 浅粉，字只剩 1.9:1（实测）。「正在关闭」这件事已经由转圈 + 文案表达，
                    // 底色保持原样反而是最稳的。
                    disabledContainerColor = stopFill,
                    disabledContentColor = stopInk
                ),
                shape = CircleShape,
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                if (isStopping) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = stopInk
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("正在关闭...", color = stopInk, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                } else {
                    Text("结 束 使 用", color = stopInk, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // 原来这里写死「费率: 0.041元/升」——所有设备、所有项目共用这一个数字，
            // 而真实费率是服务端 downRate 下发的（每个学校 / 项目都不一样），
            // 写死等于替服务端编一个值。本页拿不到费率（调用方没有这个参数），
            // 所以不显示数值，只说明该去哪儿看。
            Text("费率以账单为准 · 设备: ${location.takeLast(6)}",
                fontSize = 11.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
        }

        // 左上角返回：必须放在 Column 之后（上层），否则会被 full-size 的内容层拦截点击
        IconButton(
            onClick = onMinimizeClick,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回（不结束用水）",
                tint = AppColors.TextPrimary
            )
        }
    }
}
