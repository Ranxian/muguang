@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.ranxian.muguang.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.ranxian.muguang.ui.design.Design
import io.github.ranxian.muguang.ui.design.SurfaceCard
import io.github.ranxian.muguang.ui.design.StyleId
import io.github.ranxian.muguang.ui.design.StyleState
import io.github.ranxian.muguang.ui.design.controlShape

/**
 * 界面风格选择。
 *
 * 四套风格是并列的完整设计，不是换色：形状、字体、分隔手法都不同。
 * 切换后立即重组，不重启 Activity——所以放在设置页第一屏，随时可试。
 */
@Composable
fun StylePickerCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = Design.palette
    val current = StyleState.current
    var showSheet by remember { mutableStateOf(false) }

    // 入口收敛为一行：风格名 + chevron，点开二级弹窗切换（组件 = ModalBottomSheet）
    SurfaceCard(modifier = modifier.fillMaxWidth(), contentPadding = 14.dp) {
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        // 按压反馈 = 行底色淡入淡出（120ms）：高频交互不做缩放，缩放会带得整卡晃
        val pressBg by animateColorAsState(
            targetValue = if (pressed) p.surfaceSunken else Color.Transparent,
            animationSpec = tween(durationMillis = 120, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
            label = "style-row-press",
        )
        Row(
            Modifier
                .fillMaxWidth()
                // 触控下限 48dp：字号不变，只把行高撑够
                .heightIn(min = 48.dp)
                .clip(controlShape())
                .background(pressBg)
                .clickable(
                    interactionSource = interaction,
                    indication = null
                ) { showSheet = true }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("界面风格", style = Design.type.section, color = p.ink)
            Spacer(Modifier.width(6.dp))
            Text("结构一致，只换视觉语言", style = Design.type.caption, color = p.inkMuted)
            Spacer(Modifier.weight(1f))
            Text("${current.label} ›", style = Design.type.body, color = Design.palette.accent)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = Design.palette.surface,
        ) {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("界面风格", style = Design.type.title, color = p.ink)
                Spacer(Modifier.height(2.dp))
                Text("选择后立即生效，结构完全一致", style = Design.type.caption, color = p.inkMuted)
                Spacer(Modifier.height(12.dp))
                StyleId.entries.chunked(2).forEach { rowIds ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowIds.forEach { id ->
                            Box(Modifier.weight(1f)) {
                                StyleChip(
                                    id = id,
                                    selected = id == current,
                                    fillMax = true,
                                    onClick = {
                                        StyleState.select(context, id)
                                        showSheet = false
                                    },
                                )
                            }
                        }
                        if (rowIds.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun StyleChip(id: StyleId, selected: Boolean, fillMax: Boolean = false, onClick: () -> Unit) {
    val p = Design.palette
    val shape = RoundedCornerShape(Design.shapes.control)
    Box(
        modifier = Modifier
            .then(if (fillMax) Modifier.fillMaxWidth() else Modifier)
            .clip(shape)
            .background(if (selected) p.accent else p.surfaceSunken)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) p.accent else p.hairline,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Text(
                text = id.label,
                style = Design.type.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (selected) p.accentInk else p.ink,
            )
            Spacer(Modifier.height(1.dp))
            Text(
                text = id.tagline,
                style = Design.type.caption,
                color = if (selected) p.accentInk.copy(alpha = 0.7f) else p.inkMuted,
            )
        }
    }
}
