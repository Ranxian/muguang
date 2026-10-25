package io.github.ranxian.muguang.ui.design

import io.github.ranxian.muguang.R

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 四套风格的具体取值。浅色 / 深色各一份，同一套结构。
 *
 * 取值约定：
 * - 一套风格里只有一个强调色，状态色（成功/警告/危险）不参与装饰；
 * - 描边色一律是"能看见但不抢眼"的灰，深色下换成极淡的白；
 * - 光晕只在浅色下明显，深色下压到很低，否则会发灰；
 * - inkSubtle 对 surface ≥3:1、accentInk 对 accent ≥4.5:1，改色前先算 WCAG 比值。
 */
object StylePacks {

    fun of(id: StyleId, dark: Boolean): StylePack = when (id) {
        StyleId.IOS -> ios(dark)
        StyleId.GPT -> gpt(dark)
        StyleId.CLAUDE -> claude(dark)
        // 方舟的识别度完全建立在深底荧光黄上，浅色版会变成橄榄黄，
        // 所以这一套不跟随系统深浅，始终用深色令牌
        StyleId.ARK -> ark(true)
    }

    // ── GPT：纯白、极细描边、超大标题、超大等宽数字 ──
    // ── iOS 18：系统分组底、白色内嵌卡、玻璃材质、系统蓝只给交互 ──
    // 新一版默认风格。参照 Apple iOS 18 HIG：
    // systemGroupedBackground / secondarySystemGroupedBackground / separator 三层灰阶，
    // 语义色直接取系统色，卡片内嵌 12dp 圆角，材质走玻璃（glass）。
    private fun ios(dark: Boolean): StylePack {
        val p = if (dark) Palette(
            canvas = Color(0xFF000000),                       // systemGroupedBackground (dark)
            surface = Color(0xFF1C1C1E),                      // secondarySystemGroupedBackground
            surfaceSunken = Color(0xFF2C2C2E),                // tertiarySystemFill
            surfaceTonal = Color(0xFF25252A),
            ink = Color(0xFFFFFFFF),                          // label
            inkMuted = Color(0xFFB0B4BC),                     // secondaryLabel（提亮：深底说明文字对比 ≥4.5:1）
            inkSubtle = Color(0xFF8A8F98),                    // tertiaryLabel（5.24:1）
            hairline = Color(0xFF38383A),                     // separator (dark)
            // systemBlue (dark) 加深一档：白字对比 3.65:1 → 4.58:1
            accent = Color(0xFF0874E0),
            accentInk = Color(0xFFFFFFFF),
            positive = Color(0xFF30D158),                     // systemGreen
            warning = Color(0xFFFF9F0A),                      // systemOrange
            danger = Color(0xFFFF453A),                       // systemRed
            isDark = true,
        ) else Palette(
            canvas = Color(0xFFF2F2F7),                       // systemGroupedBackground
            surface = Color(0xFFFFFFFF),                      // secondarySystemGroupedBackground
            surfaceSunken = Color(0xFFE9E9EB),
            surfaceTonal = Color(0xFFE5F0FF),                 // systemBlue 10% 容器
            ink = Color(0xFF000000),                          // label
            inkMuted = Color(0xFF6C6C70),                     // secondaryLabel
            inkSubtle = Color(0xFF8E8E93),                    // systemGray（3.26:1，Apple 的弱化文字档）
            hairline = Color(0xFFE3E3E8),                     // separator
            accent = Color(0xFF0071E3),                       // Apple Blue（白字 4.70:1）
            accentInk = Color(0xFFFFFFFF),
            positive = Color(0xFF34C759),                     // systemGreen
            warning = Color(0xFFFF9500),                      // systemOrange
            danger = Color(0xFFFF3B30),                       // systemRed
            isDark = false,
        )
        return StylePack(
            id = StyleId.IOS,
            palette = p,
            shapes = ShapeTokens(card = 12.dp, control = 12.dp, chip = 999.dp),
            type = TypeTokens(
                hero = sansHero(32, FontWeight.Bold),          // Large Title
                title = sansHero(20, FontWeight.SemiBold).copy(fontFamily = wenKai),     // Title2
                section = sansHero(15, FontWeight.SemiBold).copy(fontFamily = wenKai),   // Headline
                // 正文/说明统一霞鹜文楷：主页、账单、我的三页字型一致
                body = sansBody(15).copy(fontFamily = wenKai),
                caption = sansBody(12).copy(fontFamily = wenKai),
                number = sansNumber(30, FontWeight.SemiBold),
                label = labelStyle(11, 0.6).copy(fontFamily = wenKai),
            ),
            // 玻璃材质：iOS 内嵌分组卡零投影零描边，靠分组底色与圆角分层；
            // 带一丝 shadow 会出现「双框」错觉
            surface = SurfaceTokens(bordered = false, shadow = ZeroShadow, glass = true),
            motion = MotionTokens(
                dampingRatio = 0.85f,
                stiffness = 450f,
                pressScale = 0.97f,
                glow = GlowTokens(
                    tint = p.accent,
                    // iOS 语言没有页面级光晕：状态栏混合色 helper 会自动退回纯 canvas
                    intensity = 0f,
                    driftMillis = 10_000,
                    radiusFraction = 0.9f,
                ),
            ),
        )
    }

    private fun gpt(dark: Boolean): StylePack {
        val p = if (dark) Palette(
            canvas = Color(0xFF0C0D0F),
            surface = Color(0xFF131417),
            surfaceSunken = Color(0xFF191B1F),
            surfaceTonal = Color(0xFF1D2025),
            ink = Color(0xFFECEDEF),
            inkMuted = Color(0xFF9BA1AC),
            inkSubtle = Color(0xFF6C727C),
            hairline = Color(0xFF24262B),
            accent = Color(0xFFF2F3F5),
            accentInk = Color(0xFF101114),
            positive = Color(0xFF4ADE80),
            warning = Color(0xFFFBBF24),
            danger = Color(0xFFF87171),
            isDark = true,
        ) else Palette(
            canvas = Color(0xFFFFFFFF),
            surface = Color(0xFFFFFFFF),
            surfaceSunken = Color(0xFFF6F6F4),
            surfaceTonal = Color(0xFFF1F1EE),
            ink = Color(0xFF14161A),
            inkMuted = Color(0xFF63696F),
            inkSubtle = Color(0xFF8C9298),                    // 3.14:1（原 #9AA0A6 仅 2.64）
            hairline = Color(0xFFE6E6E2),
            accent = Color(0xFF111214),
            accentInk = Color(0xFFFFFFFF),
            positive = Color(0xFF15803D),
            warning = Color(0xFFB45309),
            danger = Color(0xFFDC2626),
            isDark = false,
        )
        return StylePack(
            id = StyleId.GPT,
            palette = p,
            shapes = ShapeTokens(card = 18.dp, control = 12.dp, chip = 999.dp),
            type = TypeTokens(
                hero = sansHero(30, FontWeight.Bold),
                title = sansHero(20, FontWeight.SemiBold),
                section = sansHero(15, FontWeight.SemiBold),
                body = sansBody(14),
                caption = sansBody(12),
                number = sansNumber(34, FontWeight.Light),
                label = labelStyle(11, 1.4),
            ),
            // GPT 保持「纯白 + 细分隔线 + 超大标题与数字」的思路，
            // 磨砂只体现在卡片上：半透明白叠在光晕上，透出一点微光层次
            surface = SurfaceTokens(bordered = true, shadow = ZeroShadow, glass = true),
            motion = MotionTokens(
                dampingRatio = 0.86f,
                stiffness = 420f,
                pressScale = 0.985f,
                glow = GlowTokens(
                    tint = if (dark) Color(0xFF5B6BFF) else Color(0xFF111214),
                    intensity = if (dark) 0.16f else 0.05f,
                    driftMillis = 9_000,
                    radiusFraction = 0.9f,
                ),
            ),
        )
    }

    // ── Claude：暖纸底、衬线标题、赤陶色 ──
    private fun claude(dark: Boolean): StylePack {
        val p = if (dark) Palette(
            canvas = Color(0xFF14120F),
            surface = Color(0xFF1D1A16),
            surfaceSunken = Color(0xFF171410),
            surfaceTonal = Color(0xFF26221C),
            ink = Color(0xFFF0EAE0),
            inkMuted = Color(0xFFA69C8C),
            inkSubtle = Color(0xFF7B7365),
            hairline = Color(0xFF352F27),
            accent = Color(0xFFD98A63),
            accentInk = Color(0xFF1B120C),
            positive = Color(0xFF8FBF7F),
            warning = Color(0xFFD9A85C),
            danger = Color(0xFFD97A6C),
            isDark = true,
        ) else Palette(
            canvas = Color(0xFFF7F5F0),
            surface = Color(0xFFFCFBF7),
            surfaceSunken = Color(0xFFF1EEE6),
            surfaceTonal = Color(0xFFF0E7DA),
            ink = Color(0xFF23201B),
            inkMuted = Color(0xFF6B6459),
            inkSubtle = Color(0xFF8F8A7D),                    // 3.33:1（原 #9A9487 仅 2.91）
            hairline = Color(0xFFE2DCCF),
            accent = Color(0xFFAC5C3B),                       // 赤陶加深一档：奶白字 3.99:1 → 4.75:1
            accentInk = Color(0xFFFFFDF9),
            positive = Color(0xFF4F7A45),
            warning = Color(0xFF9A6B20),
            danger = Color(0xFFA63D2F),
            isDark = false,
        )
        return StylePack(
            id = StyleId.CLAUDE,
            palette = p,
            shapes = ShapeTokens(card = 10.dp, control = 8.dp, chip = 999.dp),
            type = TypeTokens(
                hero = serifHero(28),
                title = serifHero(19),
                section = sansHero(14, FontWeight.SemiBold).copy(letterSpacing = 0.2.sp),
                body = serifBody(14),
                caption = serifBody(12),
                number = sansNumber(30, FontWeight.Normal),
                label = labelStyle(11, 1.0),
            ),
            surface = SurfaceTokens(bordered = true, shadow = ZeroShadow, paperGrain = true),
            motion = MotionTokens(
                dampingRatio = 0.9f,
                stiffness = 320f,
                pressScale = 0.99f,
                glow = GlowTokens(
                    tint = p.accent,
                    intensity = if (dark) 0.10f else 0.07f,
                    driftMillis = 12_000,
                    radiusFraction = 1.0f,
                ),
            ),
        )
    }

    // ── 明日方舟：深色底、荧光黄、切角、等宽双语 ──
    private fun ark(dark: Boolean): StylePack {
        val p = if (dark) Palette(
            canvas = Color(0xFF0B1013),
            surface = Color(0xFF141B1F),
            surfaceSunken = Color(0xFF101619),
            surfaceTonal = Color(0xFF1B2429),
            ink = Color(0xFFE8EDF0),
            inkMuted = Color(0xFF8D9AA2),
            inkSubtle = Color(0xFF5F6C74),
            hairline = Color(0xFF2A363D),
            accent = Color(0xFFF3D24B),
            accentInk = Color(0xFF12140C),
            positive = Color(0xFF7FD1A0),
            warning = Color(0xFFF3D24B),
            danger = Color(0xFFE86F5C),
            isDark = true,
        ) else Palette(
            canvas = Color(0xFFF4F5F6),
            surface = Color(0xFFFFFFFF),
            surfaceSunken = Color(0xFFEDEFF1),
            surfaceTonal = Color(0xFFE7E9EB),
            ink = Color(0xFF11171A),
            inkMuted = Color(0xFF5C6870),
            inkSubtle = Color(0xFF8A959C),
            hairline = Color(0xFFD5DADE),
            accent = Color(0xFFB58900),
            accentInk = Color(0xFF171400),
            positive = Color(0xFF2F7D5B),
            warning = Color(0xFF8A6A00),
            danger = Color(0xFFB4453A),
            isDark = false,
        )
        return StylePack(
            id = StyleId.ARK,
            palette = p,
            shapes = ShapeTokens(card = 2.dp, control = 2.dp, chip = 2.dp, cutCorner = true),
            type = TypeTokens(
                hero = sansHero(26, FontWeight.Bold),
                title = sansHero(18, FontWeight.Bold),
                section = sansHero(14, FontWeight.Bold).copy(letterSpacing = 0.6.sp),
                body = sansBody(13),
                caption = sansBody(12),
                number = sansNumber(32, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                label = labelStyle(10, 2.0),
            ),
            surface = SurfaceTokens(bordered = true, shadow = ZeroShadow),
            motion = MotionTokens(
                dampingRatio = 0.72f,
                stiffness = 600f,
                pressScale = 0.96f,
                glow = GlowTokens(
                    tint = p.accent,
                    intensity = 0.12f,
                    driftMillis = 7_000,
                    radiusFraction = 0.8f,
                ),
            ),
        )
    }
}

/**
 * 衬线正文。
 *
 * ⚠️ 不带颜色：TextStyle 里一旦写死颜色，就会盖掉 Button / 徽章里的 contentColor，
 * 表现为"同一个样式在按钮上变成深色字"。颜色一律由调用方从 palette 传。
 */
private fun serifBody(size: Int) = TextStyle(
    fontFamily = FontFamily.Serif,
    fontSize = size.sp,
    lineHeight = (size * 1.6f).sp,
)

/** 霞鹜文楷（子集化打包）：默认风格全站正文字型 */
private val wenKai = FontFamily(Font(R.font.lxgw_wen_kai_regular))
