package io.github.ranxian.muguang.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import io.github.ranxian.muguang.ui.design.LocalStyle
import io.github.ranxian.muguang.ui.design.LocalStyleId
import io.github.ranxian.muguang.ui.design.StyleId
import io.github.ranxian.muguang.ui.design.StylePacks
import io.github.ranxian.muguang.ui.design.StyleState
import io.github.ranxian.muguang.ui.design.TypeTokens
import io.github.ranxian.muguang.utils.BackgroundManager
import io.github.ranxian.muguang.utils.BackgroundState

enum class ThemeMode { LIGHT, DARK }

// ── Light palette（液态玻璃：半透明卡片 + 微光描边） ──
val LightColors = AppColorSet(
    Background = Color(0xFFF1F5F9),
    Card = Color(0xB8FFFFFF),          // 72% 透白玻璃
    SolidSurface = Color(0xFFFFFFFF),  // 弹窗等需完全遮挡的场合用纯色
    Primary = Color(0xFF0D1117),
    Accent = Color(0xFF2563EB),
    TextPrimary = Color(0xFF111827),
    TextSecondary = Color(0xFF6B7280),
    Success = Color(0xFF22C55E), Warning = Color(0xFFF59E0B),
    Danger = Color(0xFFEF4444), ActiveBg = Color(0xFFFFF7ED),
    SurfaceVariant = Color(0xFFF0F0F0),
    Border = Color(0x14000000),        // 玻璃微光描边（极淡，仅提示圆角轮廓）
    isDark = false
)

// ── Dark palette（液态玻璃：半透明磨砂 + 微光描边） ──
val DarkColors = AppColorSet(
    Background = Color(0xFF0E131D),
    Card = Color(0xBF1D232E),          // 75% 磨砂玻璃
    SolidSurface = Color(0xFF161B22),
    Primary = Color(0xFFE6EDF3),
    Accent = Color(0xFF58A6FF),
    TextPrimary = Color(0xFFE6EDF3),
    TextSecondary = Color(0xFF8B949E),
    Success = Color(0xFF3FB950), Warning = Color(0xFFD29922),
    Danger = Color(0xFFF85149), ActiveBg = Color(0xFF1B1F23),
    SurfaceVariant = Color(0xFF21262D),
    Border = Color(0x14FFFFFF),
    isDark = true
)

data class AppColorSet(
    val Background: Color, val Card: Color,
    val SolidSurface: Color = Color.White,
    val Primary: Color, val Accent: Color,
    val TextPrimary: Color, val TextSecondary: Color,
    val Success: Color, val Warning: Color,
    val Danger: Color, val ActiveBg: Color,
    val SurfaceVariant: Color,
    val Border: Color = Color(0x18FFFFFF),
    val isDark: Boolean = false
)

val LocalAppColors = staticCompositionLocalOf { LightColors }
val LocalThemeMode = compositionLocalOf { mutableStateOf(ThemeMode.LIGHT) }

/** Convenience: use AppColors.xxx in composable functions */
object AppColors {
    val Background: Color @Composable get() = LocalAppColors.current.Background
    val Card: Color @Composable get() = LocalAppColors.current.Card
    val SolidSurface: Color @Composable get() = LocalAppColors.current.SolidSurface
    val Primary: Color @Composable get() = LocalAppColors.current.Primary
    val Accent: Color @Composable get() = LocalAppColors.current.Accent
    val TextPrimary: Color @Composable get() = LocalAppColors.current.TextPrimary
    val TextSecondary: Color @Composable get() = LocalAppColors.current.TextSecondary
    val Success: Color @Composable get() = LocalAppColors.current.Success
    val Warning: Color @Composable get() = LocalAppColors.current.Warning
    val Danger: Color @Composable get() = LocalAppColors.current.Danger
    val ActiveBg: Color @Composable get() = LocalAppColors.current.ActiveBg
    val Border: Color @Composable get() = LocalAppColors.current.Border
    val isDark: Boolean @Composable get() = LocalAppColors.current.isDark
}

/**
 * 实心色块上的文字色。
 *
 * 风格包只给了 accentInk 一个「强调色上的墨色」，可实心块不止强调色一种：
 * 「结束用水」是 danger、「请求代扣」是 warning。白字压在上面在深色 / 终端两套里
 * 低到 1.5~1.7:1（终端荧光黄 + 白字等于看不见），所以这里按 WCAG 实测兜底：
 * 令牌给的颜色够 4.5:1 就用它，不够就退到黑 / 白里更强的一侧。
 *
 * ⚠️ 只换字色、**不改填充**：danger / warning 是语义色（破坏性 / 待处理），
 * 为了凑对比度把它们调暗会同时改掉语义，还会和状态色「不参与装饰」的约定打架。
 * 深色风格包里 accentInk 本来就是近黑（极简、终端、暖纸的深色版），
 * 「亮底 + 深字」在这套系统里是既有做法，不是这里新发明的。
 */
fun inkOnFill(fill: Color, preferred: Color): Color {
    if (contrastRatio(preferred, fill) >= 4.5f) return preferred
    return if (contrastRatio(Color.White, fill) >= contrastRatio(Color.Black, fill)) Color.White else Color.Black
}

/** WCAG 相对亮度对比度。半透明色请先自行合成到底色上再比。 */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * 按「指定模式」渲染主题。
 * 与 [MuguangTheme] 的区别：它不读取全局状态，而是渲染传入的 mode，
 * 供主题切换动画同时叠加两层（旧主题 + 新主题）使用。
 */
@Composable
fun MuguangThemeSpecific(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = (mode == ThemeMode.DARK)
    val styleId = StyleState.current
    val pack = StylePacks.of(styleId, dark)
    val p = pack.palette
    // 自定义背景提取的主题色：只覆盖「强调色」，不参与 Light / Dark 的判定与切换
    val bgTheme = BackgroundState.config(BackgroundManager.SCOPE_HOME).themeColor
    val accent = if (bgTheme != null) Color(bgTheme) else p.accent
    val set = AppColorSet(
        Background = p.canvas,
        // 磨砂风格（GPT）卡片统一带透明度，深色风格压得比浅色更低一点；
        // 非 glass 风格只在深色下保留原来的磨砂层次，浅色保持不透明白卡。
        Card = when {
            pack.surface.glass -> p.surface.copy(alpha = if (dark) 0.80f else 0.78f)
            dark -> p.surface.copy(alpha = 0.86f)
            else -> p.surface
        },
        SolidSurface = p.surface,
        Primary = p.ink,
        Accent = accent,
        TextPrimary = p.ink,
        TextSecondary = p.inkMuted,
        Success = p.positive,
        Warning = p.warning,
        Danger = p.danger,
        ActiveBg = p.surfaceTonal,
        SurfaceVariant = p.surfaceSunken,
        Border = p.hairline,
        isDark = dark,
    )
    val scheme = if (dark) darkColorScheme(
        primary = accent, background = set.Background, surface = set.SolidSurface,
        surfaceContainer = set.SolidSurface, surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SolidSurface,
        onPrimary = p.accentInk, secondary = set.TextSecondary,
        surfaceVariant = set.SurfaceVariant,
        outline = p.hairline, onSurface = p.ink, onSurfaceVariant = p.inkMuted,
        error = p.danger,
    ) else lightColorScheme(
        primary = accent, background = set.Background, surface = set.SolidSurface,
        surfaceContainer = set.SolidSurface, surfaceContainerHigh = set.SolidSurface,
        surfaceContainerHighest = set.SolidSurface,
        onPrimary = p.accentInk, secondary = set.TextSecondary,
        surfaceVariant = set.SurfaceVariant,
        outline = p.hairline, onSurface = p.ink, onSurfaceVariant = p.inkMuted,
        error = p.danger,
    )
    // 裸 Text() 取的是 LocalTextStyle。material3 的 MaterialTheme 只提供 typography 本身，
    // **不**提供 LocalTextStyle（1.2 里已经没有 ProvideTextStyle 了），所以没写 style 的
    // Text 会退回 TextStyle.Default —— Roboto 16sp。账单 / 我的 / 使用页 / 登录页里
    // 大量 Text 既不传 style 也不传 fontFamily，正文于是和首页（Design.type.body）对不上，
    // 这就是「账单和我的的字体要跟 home 统一」一直没做完的原因。
    //
    // 把当前风格包的正文令牌整体提到 CompositionLocal 上，一次覆盖所有老页面：
    // 默认 = 霞鹜文楷，极简 = 无衬线，暖纸 = 衬线，终端 = 无衬线小一号。
    //
    // ⚠️ 令牌里不能带颜色：正文样式一旦写死 color，Button / 徽章里的文字会被它盖掉
    // （见 StylePacks 里 serifBody 的注释）。四个包的 body 目前都不带颜色，
    // 这里再做一层保险，将来谁给 body 加了色也不会泄漏到按钮上。
    val bodyStyle = pack.type.body.let {
        if (it.color.isSpecified) it.copy(color = Color.Unspecified) else it
    }
    CompositionLocalProvider(
        LocalAppColors provides set,
        LocalStyle provides pack,
        LocalStyleId provides styleId,
        // 全局提供更明显的点击涟漪（默认涟漪太淡，感知不到）
        LocalIndication provides rememberRipple(color = accent.copy(alpha = 0.35f))
    ) {
        // 组件槽位（Button 的 labelLarge、对话框的 headline、输入框的 bodySmall…）只换 fontFamily、
        // 字号仍用 Material 默认：这些槽位的尺寸被 Material 组件自己的内边距算过，
        // 改字号会把按钮 / 弹窗的行高顶歪。裸 Text() 走下面那条 LocalTextStyle。
        MaterialTheme(colorScheme = scheme, typography = materialTypography(pack.type)) {
            // 再压一层 LocalTextStyle：Material 那条路径提供不到，裸 Text 才真正拿到正文令牌
            CompositionLocalProvider(LocalTextStyle provides bodyStyle, content = content)
        }
    }
}

/** 把风格包的字体族映射到 Material3 字号槽位；字号与行高一律沿用 Material 默认。 */
private fun materialTypography(type: TypeTokens): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = type.hero.fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = type.hero.fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = type.hero.fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = type.hero.fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = type.title.fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = type.title.fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = type.title.fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = type.section.fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = type.section.fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = type.body.fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = type.body.fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = type.body.fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = type.label.fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = type.label.fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = type.label.fontFamily),
    )
}

/** 跟随 [LocalThemeMode] 的常规入口 */
@Composable
fun MuguangTheme(content: @Composable () -> Unit) {
    val themeMode by LocalThemeMode.current
    MuguangThemeSpecific(themeMode, content)
}
