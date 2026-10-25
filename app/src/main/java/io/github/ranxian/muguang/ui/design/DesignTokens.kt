package io.github.ranxian.muguang.ui.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 四套视觉语言。结构、信息层级、交互完全一致，只有色彩 / 字体 / 圆角 / 分隔手法不同。
 *
 * ⚠️ 新增风格只需要在这里加一项并在 [StylePacks] 里补齐两套（浅色 + 深色）令牌，
 * 页面代码不用改——所有页面都从 [LocalStyle] 读令牌。
 */
enum class StyleId(val label: String, val tagline: String) {
    // 默认风格：iOS 18 系统语言（内嵌分组列表 / 玻璃材质 / 系统色）。
    // 枚举名 IOS 保留是为了兼容已写入本机的旧偏好值；对外展示只叫「默认」。
    IOS("默认", "玻璃分层 · 内嵌分组"),
    // 原 GPT 风保留但不默认；枚举名保留 GPT 同上。
    GPT("极简", "安静留白"),
    // 原 Claude：暖纸底 + 衬线，更名「暖纸」
    CLAUDE("暖纸", "暖纸底 · 衬线人文"),
    // 原 明日方舟：深底终端感，更名「终端」
    ARK("终端", "深底 · 战术终端");

    companion object {
        /** 默认风格。产品和设计评审都按这一套验收。 */
        val Default: StyleId = IOS

        fun fromKey(key: String?): StyleId =
            entries.firstOrNull { it.name == key } ?: Default
    }
}

@Immutable
data class Palette(
    /** 页面底色 */
    val canvas: Color,
    /** 卡片 / 面板底色 */
    val surface: Color,
    /** 比 surface 更沉的内嵌块（输入框、次级容器） */
    val surfaceSunken: Color,
    /** 有色容器：Google 用色调块代替描边 */
    val surfaceTonal: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkSubtle: Color,
    /** 分隔线 / 卡片描边 */
    val hairline: Color,
    /** 唯一强调色，只给交互与状态 */
    val accent: Color,
    /** 强调色上的文字色 */
    val accentInk: Color,
    val positive: Color,
    val warning: Color,
    val danger: Color,
    val isDark: Boolean,
)

@Immutable
data class ShapeTokens(
    val card: Dp,
    val control: Dp,
    val chip: Dp,
    /** 方舟那类切角边框；为 true 时组件用切角而不是圆角 */
    val cutCorner: Boolean = false,
)

@Immutable
data class TypeTokens(
    val hero: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    /** 大号数字：等宽数位，避免倒计时跳动时宽度抖动 */
    val number: TextStyle,
    /** 等宽小标签（双语、状态码） */
    val label: TextStyle,
)

@Immutable
data class SurfaceTokens(
    /** 用 1dp 描边勾出卡片轮廓 */
    val bordered: Boolean,
    /** 卡片投影高度；0 表示完全不用阴影 */
    val shadow: Dp,
    /** 用色调块代替描边（Google） */
    val tonalFill: Boolean = false,
    /** 暖纸纹（Claude） */
    val paperGrain: Boolean = false,
    /**
     * 磨砂卡片：surface 带 20% 左右透明度画在 canvas 上。
     * 不做真模糊（RenderEffect 要 API 31，minSdk 26 跑不了），
     * 透明卡片叠在 AmbientGlow 上天然有「光从卡片后面透过来」的层次。
     */
    val glass: Boolean = false,
)

/**
 * 沉浸光感参数：漫射光晕的色相、强度、漂移周期与半径。
 *
 * 只在 hero 区域用，列表项不开——一个屏幕上多处漂移会变成噪声，
 * 也白白多耗绘制。intensity = 0 表示这套风格不要光晕。
 */
@Immutable
data class GlowTokens(
    val tint: Color,
    val intensity: Float,
    val driftMillis: Int,
    val radiusFraction: Float,
)

@Immutable
data class MotionTokens(
    val dampingRatio: Float,
    val stiffness: Float,
    val pressScale: Float,
    val glow: GlowTokens,
)

@Immutable
data class StylePack(
    val id: StyleId,
    val palette: Palette,
    val shapes: ShapeTokens,
    val type: TypeTokens,
    val surface: SurfaceTokens,
    val motion: MotionTokens,
)

// ── 字体工具箱 ──────────────────────────────────────────────────────────────

internal fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

internal fun sansHero(size: Int = 30, weight: FontWeight = FontWeight.SemiBold) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = (size * 1.18f).sp,
    fontWeight = weight,
    letterSpacing = (-0.4).sp,
)

internal fun sansBody(size: Int = 14) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = (size * 1.55f).sp,
    fontWeight = FontWeight.Normal,
)

internal fun sansNumber(size: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontSize = size.sp,
    lineHeight = (size * 1.08f).sp,
    fontWeight = weight,
    letterSpacing = (-0.8).sp,
).tabular()

internal fun labelStyle(size: Int = 11, spacing: Double = 1.2) = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = size.sp,
    lineHeight = (size * 1.4f).sp,
    fontWeight = FontWeight.Medium,
    letterSpacing = spacing.sp,
)

internal fun serifHero(size: Int = 28) = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = size.sp,
    lineHeight = (size * 1.3f).sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp,
)

internal val ZeroShadow: Dp = 0.dp
