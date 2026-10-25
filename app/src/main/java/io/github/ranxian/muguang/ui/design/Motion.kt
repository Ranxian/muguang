package io.github.ranxian.muguang.ui.design

import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * 沉浸光感：一层缓慢漂移的漫射光。
 *
 * 只用径向渐变，不依赖 RenderEffect / blur——minSdk 26 上也能跑，
 * 而且渐变本身没有硬边，不需要再花一次全屏模糊。
 *
 * 参数来自 [GlowTokens]：深色风格强度普遍压到 0.1 上下，再高会把深色底照灰。
 */
@Composable
fun AmbientGlow(
    tokens: GlowTokens,
    modifier: Modifier = Modifier,
    alignment: GlowAlignment = GlowAlignment.Top,
) {
    if (tokens.intensity <= 0f) return
    val transition = rememberInfiniteTransition(label = "ambient-glow")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(tokens.driftMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow-drift",
    )

    Canvas(modifier) {
        val radius = size.maxDimension * tokens.radiusFraction
        val verticalAnchor = when (alignment) {
            GlowAlignment.Top -> 0.06f
            GlowAlignment.Center -> 0.42f
            GlowAlignment.Bottom -> 0.86f
        }
        val cx = size.width * (0.5f + (drift - 0.5f) * 0.14f)
        val cy = size.height * (verticalAnchor + drift * 0.08f)
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    tokens.tint.copy(alpha = tokens.intensity),
                    tokens.tint.copy(alpha = tokens.intensity * 0.32f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

enum class GlowAlignment { Top, Center, Bottom }

/**
 * 按下反馈：按 [MotionTokens.pressScale] 缩一点，松开弹回。
 *
 * 用弹簧而不是 tween——手感上"回弹"比"匀速回位"更像实体按键。
 */
/**
 * 系统「移除动画」开关（动画时长缩放 = 0）。
 *
 * 动效层所有入场/转场都要查它：reduced 只保留透明度这类不移动的过渡，
 * 位移类（上移入场、滑页）一律退化为瞬时。
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember {
        try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f
            ) == 0f
        } catch (_: Exception) {
            false
        }
    }
}

/** Material Button 想要同款按压变形时的统一入口：交互源记得一起传给 Button。 */
@Composable
fun rememberPressInteraction(): MutableInteractionSource = remember { MutableInteractionSource() }

/**
 * 按下反馈：按 [MotionTokens.pressScale] 缩一点，松开弹回。
 *
 * 用弹簧而不是 tween——手感上"回弹"比"匀速回位"更像实体按键。
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
): Modifier {
    val motion = Design.motion
    val pressed by interactionSource.collectIsPressedAsState()
    val target = if (pressed && enabled) motion.pressScale else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(
            dampingRatio = motion.dampingRatio,
            stiffness = motion.stiffness,
        ),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
