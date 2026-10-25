package io.github.ranxian.muguang.ui.design

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 当前风格包。页面只从这里取颜色/形状/字体，不直接写死颜色。 */
val LocalStyle = staticCompositionLocalOf { StylePacks.of(StyleId.Default, false) }

/** 当前风格 id，用于少数需要按风格分支的地方（切角、双语标签等）。 */
val LocalStyleId = staticCompositionLocalOf { StyleId.Default }

object Design {
    val style: StylePack @Composable get() = LocalStyle.current
    val palette: Palette @Composable get() = LocalStyle.current.palette
    val shapes: ShapeTokens @Composable get() = LocalStyle.current.shapes
    val type: TypeTokens @Composable get() = LocalStyle.current.type
    val surface: SurfaceTokens @Composable get() = LocalStyle.current.surface
    val motion: MotionTokens @Composable get() = LocalStyle.current.motion
}

/**
 * 风格选择的全局状态。
 *
 * 四套风格都保留，默认 IOS（枚举名保留旧值，对外展示叫「默认」）；
 * 切换后立即重组，不需要重启 Activity。
 * 选择写进独立的 prefs 文件，和业务偏好分开——换风格不该影响登录态和台账。
 */
object StyleState {
    private const val PREFS = "muguang_style"
    private const val KEY = "style_id"

    var current: StyleId by mutableStateOf(StyleId.Default)
        private set

    fun load(context: Context) {
        val saved = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
        current = StyleId.fromKey(saved)
    }

    fun select(context: Context, id: StyleId) {
        current = id
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, id.name)
            .apply()
    }
}

/** 半透明叠色：把 base 往 canvas 方向拉一点，用来做"抬起来"的层次而不加阴影。 */
/**
 * 状态栏底色。
 *
 * 页面顶部画了 [AmbientGlow] 时，光晕最亮的一圈就在状态栏下方；
 * 状态栏如果还填纯 canvas，浅色风格下会看到一条明显更浅的横带（Apple / Google 尤其明显）。
 * 这里把状态栏填成「canvas + 光晕最亮值」的混合色，与页面顶部的像素值连续。
 */
fun statusBarColor(pack: StylePack): Color = if (pack.motion.glow.intensity > 0f) {
    blend(pack.palette.canvas, pack.motion.glow.tint, pack.motion.glow.intensity)
} else {
    pack.palette.canvas
}

internal fun blend(base: Color, canvas: Color, amount: Float): Color = Color(
    red = base.red + (canvas.red - base.red) * amount,
    green = base.green + (canvas.green - base.green) * amount,
    blue = base.blue + (canvas.blue - base.blue) * amount,
    alpha = base.alpha,
)
