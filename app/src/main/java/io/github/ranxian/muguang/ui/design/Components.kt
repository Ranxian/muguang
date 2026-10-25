package io.github.ranxian.muguang.ui.design

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import io.github.ranxian.muguang.model.DeviceKind

/** 卡片形状：方舟风格用切角，其余用圆角。切角也走同一个 [radius]，否则 shape 令牌形同虚设。 */
@Composable
fun cardShape(radius: Dp = Design.shapes.card): Shape =
    if (Design.shapes.cutCorner) {
        CutCornerShape(topEnd = radius, bottomStart = radius)
    } else {
        RoundedCornerShape(radius)
    }

@Composable
fun controlShape(radius: Dp = Design.shapes.control): Shape =
    if (Design.shapes.cutCorner) {
        CutCornerShape(topEnd = radius, bottomStart = radius)
    } else {
        RoundedCornerShape(radius)
    }

/**
 * 通用面板。
 *
 * 层次由风格决定：GPT/Claude/方舟用 1dp 描边，Apple 用极浅投影，Google 用色调块。
 * [glow] 打开后会在面板内部垫一层漫射光——只给 hero 用，列表项别开。
 */
@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    glow: Boolean = false,
    tonal: Boolean = Design.surface.tonalFill,
    accentTint: Boolean = false,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val p = Design.palette
    val sf = Design.surface
    val shape = cardShape()
    // 磨砂：surface 打 8 折透明度叠在光晕上，透出后面那层微光。
    // 深色风格压得更低一点——深底上透明度太高的卡片会和背景糊在一起。
    val glassAlpha = if (p.isDark) 0.80f else 0.78f
    val fill = when {
        accentTint -> p.surfaceTonal
        tonal -> p.surfaceTonal
        sf.glass -> p.surface.copy(alpha = glassAlpha)
        else -> p.surface
    }
    val base = modifier
        .then(if (sf.shadow > 0.dp) Modifier.shadow(sf.shadow, shape, clip = false) else Modifier)
        .clip(shape)
        .background(fill)
        .then(if (sf.bordered) Modifier.border(BorderStroke(1.dp, p.hairline), shape) else Modifier)

    Box(modifier = base) {
        if (glow) {
            AmbientGlow(
                tokens = Design.motion.glow,
                modifier = Modifier.matchParentSize(),
                alignment = GlowAlignment.Top,
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content,
        )
    }
}

/** 文字操作，靠右对齐，不填充。 */
@Composable
fun QuietAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val p = Design.palette
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val ink = if (enabled) p.inkMuted else p.inkSubtle
    Row(
        modifier = modifier
            .pressFeedback(interaction, enabled)
            .clip(controlShape())
            .styleClickable(interaction, enabled, onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text, style = Design.type.caption, color = ink)
        if (icon != null) Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(13.dp))
    }
}

/**
 * Material Button 的「带按压变形」版本。
 *
 * 样式参数与 [Button] 一一对应，调用方照常给 colors / shape；
 * 差别只有一点：按下去会按当前风格包的弹簧缩一下（pressScale）。
 * 交互源是本组件持有的，调用方不用管。
 */
@Composable
fun PressButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = rememberPressInteraction(),
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.pressFeedback(interactionSource, enabled),
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content,
    )
}

/** 统一点击入口：保留项目全局涟漪（LocalIndication），并带上按下缩放。 */
@Composable
private fun Modifier.styleClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interaction,
    indication = LocalIndication.current,
    enabled = enabled,
    onClick = onClick,
)

/**
 * 设备类型色。
 *
 * 原实现把饮水机 / 洗衣机 / 洗手台写死成绿、紫、橙三组固定值。
 * 换风格后固定值会跟底色打架：方舟的深底会把 0xFF10B981 压暗，
 * Claude 的纸感底上荧光紫又过于现代。改为从当前风格的
 * positive / accent / warning 派生，仍保留「不同设备不同色」的辨识度。
 */
@Composable
fun deviceTint(kind: DeviceKind): Color = when (kind) {
    DeviceKind.DRINKING -> Design.palette.positive
    DeviceKind.WASHER -> Design.palette.accent
    DeviceKind.SINK -> Design.palette.warning
    DeviceKind.OTHER -> Design.palette.accent
}

/** emoji → 设备类型。三个页面（附近设备 / 上次使用 / 账单）共用同一套判定。 */
@Composable
fun deviceTint(emoji: String): Color = deviceTint(
    when (emoji) {
        "🚰", "❄️", "♨️" -> DeviceKind.DRINKING
        "🪥" -> DeviceKind.SINK
        else -> DeviceKind.OTHER
    }
)

/**
 * 洗衣机动画（改绘自 Uiverse Shoh2008 的 loader）：
 * 机身轻微摇晃 + 滚筒旋转。[running] 必须由**真实订单状态**驱动——
 * 动画在这里不是装饰，是「正在洗衣」的状态指示；未绑定时保持静止。
 *
 * 无限动画只在 running 时创建：rememberInfiniteTransition 一旦进组合就会
 * 一直推动帧时钟，主页在空闲时永远等不到 idle。空闲分支画静态帧（0°），
 * 视觉与「动画跑在 0° 的那一帧」完全一致。
 */
@Composable
fun WashingMachineLoader(
    modifier: Modifier = Modifier,
    running: Boolean = true,
    size: Dp = 72.dp,
) {
    if (running) {
        val t = rememberInfiniteTransition(label = "washer")
        val drum by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "drum")
        val tilt by t.animateFloat(0.5f, -0.5f, infiniteRepeatable(tween(800, easing = FastOutSlowInEasing)), label = "tilt")
        WasherFrame(modifier = modifier, size = size, drumAngle = drum, tiltAngle = tilt)
    } else {
        WasherFrame(modifier = modifier, size = size, drumAngle = 0f, tiltAngle = 0f)
    }
}

/** 洗衣机机身与滚筒的静态绘制；角度由调用方给，自己不起动画。 */
@Composable
private fun WasherFrame(
    modifier: Modifier,
    size: Dp,
    drumAngle: Float,
    tiltAngle: Float,
) {
    Box(
        modifier
            .size(width = size * 0.86f, height = size)
            .rotate(tiltAngle)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            drawRoundRect(
                color = Color(0xFFF2F2F7),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
            )
            drawRoundRect(
                color = Color(0xFFDADADF),
                cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawLine(
                Color(0xFFD0D0D5),
                Offset(0f, h * 0.12f), Offset(w, h * 0.12f),
                strokeWidth = 2.dp.toPx()
            )
            val cx = w / 2f
            val cy = h * 0.60f
            val r = min(w, h) * 0.30f
            drawCircle(Color(0xFFBBDEFB), radius = r, center = Offset(cx, cy))
            rotate(drumAngle, pivot = Offset(cx, cy)) {
                drawArc(Color(0xFF64B5F6), 0f, 180f, true, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2))
                drawArc(Color(0xFF607D8B), 180f, 180f, true, topLeft = Offset(cx - r, cy - r), size = Size(r * 2, r * 2))
            }
            drawCircle(Color(0xFFD9D9DE), radius = r, center = Offset(cx, cy), style = Stroke(width = 5.dp.toPx()))
        }
    }
}

/**
 * 探测雷达（改绘自 Uiverse Z4drus 的轨道 loader，亮背板对比度已修正）。
 *
 * 交互规则（用户指定）：
 * - 不点不动——空闲时整个雷达完全静止；
 * - 点按 → 旋转整三圈（1080°）后精确停回居中位（3×360 = 0° mod 360），
 *   同时触发 [onProbeStart]（真实 BLE 扫描）；
 * - 旋转期间若真实扫描仍在进行，说明文字显示「探测中…」，
 *   动画停止不代表扫描停止，两者解耦。
 */
@Composable
fun RadarProbe(
    modifier: Modifier = Modifier,
    scanning: Boolean = false,
    size: Dp = 32.dp,
    onProbeStart: () -> Unit = {},
    /** 外部驱动的自动旋转信号（值变化即转三圈并触发探测），如抽屉展开 */
    autoSpinKey: Int = 0,
    /**
     * 图标下方那行 9sp 说明文字。默认显示；
     * 抽屉那类「左侧图标 + 右侧标题」的行里已经有标题了，再挂一行会和标题重复
     * （扫描时两处都写「探测中…」），此时传 false。
     */
    showCaption: Boolean = true,
) {
    val angle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var spinning by remember { mutableStateOf(false) }
    fun spin(onStart: () -> Unit = {}) {
        if (spinning) return
        spinning = true
        onStart()
        scope.launch {
            angle.snapTo(angle.value % 360f)
            angle.animateTo(
                angle.value + 1080f,
                tween(2400, easing = CubicBezierEasing(0.33f, 1f, 0.68f, 1f))
            )
            angle.snapTo(angle.value % 360f)
            spinning = false
        }
    }
    LaunchedEffect(autoSpinKey) { if (autoSpinKey > 0) spin(onProbeStart) }

    // 触控下限 48dp：雷达图形和说明文字尺寸不变，只把热区补够
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { spin(onProbeStart) },
        contentAlignment = Alignment.CenterStart
    ) {
        Column(horizontalAlignment = Alignment.Start) {
            Box(Modifier.size(size)) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = this.size.minDimension / 2f
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    val a = angle.value
                    rotate(a) {
                        // 外圈弧（留 60° 缺口，旋转时像雷达扫描线）
                        drawArc(
                            color = Color(0x8C1D1D1F),
                            startAngle = -90f, sweepAngle = 300f, useCenter = false,
                            topLeft = Offset(c.x - r + 2.dp.toPx(), c.y - r + 2.dp.toPx()),
                            size = Size((r - 2.dp.toPx()) * 2, (r - 2.dp.toPx()) * 2),
                            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // 内圈 conic 扫掠
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color(0x261D1D1F), Color.Transparent)),
                            startAngle = -90f, sweepAngle = 360f, useCenter = false,
                            topLeft = Offset(c.x - r * 0.58f, c.y - r * 0.58f),
                            size = Size(r * 1.16f, r * 1.16f),
                            style = Stroke(width = r * 0.20f)
                        )
                        // 4 枚轨道点
                        val orbitR = r * 0.66f
                        for (k in 0 until 4) {
                            val rad = Math.toRadians((90.0 * k))
                            drawCircle(
                                Color(0xFF0071E3),
                                radius = 1.6.dp.toPx(),
                                center = Offset(c.x + orbitR * cos(rad).toFloat(), c.y + orbitR * sin(rad).toFloat())
                            )
                        }
                    }
                    // 中心点（不随旋转，始终居中）
                    drawCircle(Color(0xFF0071E3), radius = r * 0.16f, center = c)
                }
            }
            if (showCaption) {
                Spacer(Modifier.height(3.dp))
                Text(
                    if (scanning) "探测中…" else "点按探测",
                    fontSize = 9.sp,
                    // 抽屉的白 80% 玻璃最浅到 #E8E8EA：这里给 7.2:1，和抽屉次级文字同一档
                    color = Color(0xFF4A4A4F)
                )
            }
        }
    }
}

/**
 * 3D 立方体 spinner（改绘自 Uiverse AqFox 的 CSS cube loader）。
 * graphicsLayer 双轴旋转模拟 3D；描边方块 + 淡填充，尺寸 16dp 适配行内文本。
 */
@Composable
fun CubeSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 16.dp,
    color: Color = Color(0xFF0071E3),
) {
    val t = rememberInfiniteTransition(label = "cube")
    val rx by t.animateFloat(0f, 385f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "rx")
    val ry by t.animateFloat(0f, 385f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "ry")
    Box(
        modifier
            .size(size)
            .graphicsLayer {
                rotationX = -25f + rx
                rotationY = 25f + ry
                rotationZ = 45f
                cameraDistance = 8f * density
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
            }
            .border(1.5.dp, color, RoundedCornerShape(3.dp))
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(3.dp))
    )
}

/**
 * 日夜分段开关（nasty-moth-15 结构改绘，主题化配色）：
 * 底/描边/图标全部取当前风格令牌，与设置页卡片同材质；
 * 滑块 = 强调色，暗态叠加顶部微光。切换走圆形揭示（真实生效）。
 */
@Composable
fun DayNightToggle(
    isDark: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 84.dp,
    height: Dp = 34.dp,
) {
    val p = Design.palette
    val knobX by animateDpAsState(
        targetValue = if (isDark) width - height else 0.dp,
        animationSpec = tween(300, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)),
        label = "daynight-knob"
    )
    val glow by animateFloatAsState(if (isDark) 1f else 0f, tween(300), label = "daynight-glow")
    // 可视高度保持 34dp，热区用外层 48dp 的透明盒撑到触控下限
    Box(
        modifier
            .size(width = width, height = height.coerceAtLeast(48.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(width, height)
                .clip(RoundedCornerShape(999.dp))
                .background(p.surfaceSunken)
                .border(1.dp, p.hairline, RoundedCornerShape(999.dp))
                .padding(3.dp)
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("☀️", fontSize = 12.sp) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Text("🌙", fontSize = 12.sp) }
            }
            Box(
                Modifier
                    .offset(x = knobX)
                    .size(height - 6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            0f to p.accent.copy(alpha = 0.30f * glow + 0.06f),
                            0.7f to p.accent,
                            1f to p.accent.copy(alpha = 0.92f)
                        )
                    )
            )
        }
    }
}
