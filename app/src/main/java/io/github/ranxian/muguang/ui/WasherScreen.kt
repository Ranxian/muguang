package io.github.ranxian.muguang.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.ui.DeviceGlyph
import io.github.ranxian.muguang.model.WasherMode
import io.github.ranxian.muguang.ui.design.AmbientGlow
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.design.GlowAlignment
import io.github.ranxian.muguang.ui.design.SurfaceCard
import io.github.ranxian.muguang.ui.design.controlShape
import io.github.ranxian.muguang.ui.theme.AppColors
import io.github.ranxian.muguang.utils.MoneyFormat

@Composable
fun WasherScreen(
    device: DeviceInfo,
    mode: WasherMode,
    stageText: String,
    elapsedSec: Int,
    remainingSec: Int,
    onMinimizeClick: () -> Unit
) {
    BackHandler(onBack = onMinimizeClick)

    // 洗衣机原来是紫色渐变，换成令牌底色后由光晕提供氛围；
    // 运行中给一张带 glow 的卡片，让「正在跑」这件事在视觉上比首页更亮一档
    Box(Modifier.fillMaxSize().background(Design.palette.canvas)) {
        AmbientGlow(
            tokens = Design.motion.glow,
            modifier = Modifier.fillMaxWidth().height(340.dp),
            alignment = GlowAlignment.Top
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 设备头像：单色矢量（有图的类型）压在 sunken 圆底上，和首页 / 使用页同一套画法；
            // 没有矢量图的类型仍然回退画原 emoji
            Box(
                modifier = Modifier
                    .size(132.dp)
                    .clip(CircleShape)
                    .background(Design.palette.surfaceSunken),
                contentAlignment = Alignment.Center
            ) {
                DeviceGlyph(device.typeEmoji, emojiSize = 68.sp, iconSize = 72.dp, tint = Design.palette.accent)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "洗衣机 · 运行中",
                fontSize = 16.sp,
                color = AppColors.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            // ⚠️ 必须显式给颜色：外层只是普通 Box，没有 Material 的 Surface 提供
            // LocalContentColor，默认是纯黑；终端风格画布 #0B1013 上黑字只有 1.1:1。
            Text(device.displayName, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                color = Design.palette.ink)
            Spacer(Modifier.height(18.dp))

            SurfaceCard(
                modifier = Modifier.fillMaxWidth(),
                glow = true,
                contentPadding = 20.dp
            ) {
                    Text(mode.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Design.palette.ink)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stageText.ifBlank { mode.displayName },
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Design.palette.accent,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    LaundryStageStrip(
                        stages = listOf("洗涤", "漂洗", "脱水"),
                        current = stageText
                    )
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatBlock("已运行", formatClock(elapsedSec))
                        StatBlock(
                            "预计剩余",
                            if (remainingSec > 0) formatClock(remainingSec) else "以设备为准"
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "预扣 ${MoneyFormat.withSymbol(device.withholdMoney)}",
                        fontSize = 13.sp,
                        color = Design.palette.inkMuted
                    )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "返回首页不会结束订单",
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        }

        IconButton(
            onClick = onMinimizeClick,
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "返回首页（不停止设备）",
                tint = AppColors.TextPrimary
            )
        }
    }
}

@Composable
private fun LaundryStageStrip(stages: List<String>, current: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        stages.forEach { stage ->
            val active = current.contains(stage)
            Surface(
                modifier = Modifier.weight(1f),
                shape = controlShape(),
                color = if (active) Design.palette.accent.copy(alpha = 0.16f)
                else Design.palette.canvas.copy(alpha = 0.55f)
            ) {
                Text(
                    stage,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    // 未激活的步骤用 inkMuted 而不是 inkSubtle：inkSubtle 只是 3:1 的
                    // 图形/大字级，13sp 正文压在上面读不清
                    color = if (active) Design.palette.accent else Design.palette.inkMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 同上：没有 Surface 提供内容色，深色风格下必须用 ink 才不会变成黑字压黑底
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Design.palette.ink)
        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary)
    }
}

private fun formatClock(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    return "%02d:%02d".format(safe / 60, safe % 60)
}
