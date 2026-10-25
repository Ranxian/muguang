package io.github.ranxian.muguang.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 一套背景的配置 */
data class BgConfig(
    val enabled: Boolean = false,
    val opacity: Float = BackgroundState.DEFAULT_OPACITY,
    val blur: Float = BackgroundState.DEFAULT_BLUR,
    val brightness: Float = BackgroundState.DEFAULT_BRIGHTNESS,
    val themeColor: Int? = null
)

/**
 * 自定义背景的 Compose 可观察状态（读写都走持久化）。
 *
 * 分两套、互相独立：
 * - [BackgroundManager.SCOPE_HOME]  ：首页 / 账单 / 我的
 * - [BackgroundManager.SCOPE_SHOWER]：使用页
 *
 * ⚠️ 与深浅模式（ThemeMode / LocalThemeMode）**完全独立**：
 * 这里只描述"背景图片及其效果"，不控制 Light / Dark，也不会触发主题切换。
 */
object BackgroundState {
    // 新背景图的默认效果参数：不透明、不模糊、原亮度（改图时也会重置回这一组）
    const val DEFAULT_OPACITY = 1.0f
    const val DEFAULT_BLUR = 0f
    const val DEFAULT_BRIGHTNESS = 1.0f

    // 用私有 backing state + 只读属性，避免属性 setter 与 setXxx() 方法签名冲突
    private var _home by mutableStateOf(BgConfig())
    private var _shower by mutableStateOf(BgConfig())
    private var _imageVersion by mutableStateOf(0)

    val home: BgConfig get() = _home
    val shower: BgConfig get() = _shower

    /** 换图时递增，用于让背景层重新取图 */
    val imageVersion: Int get() = _imageVersion

    fun config(scope: String): BgConfig =
        if (scope == BackgroundManager.SCOPE_SHOWER) _shower else _home

    /** App 启动时从持久化恢复（两套都读） */
    fun refresh() {
        _home = readConfig(BackgroundManager.SCOPE_HOME)
        _shower = readConfig(BackgroundManager.SCOPE_SHOWER)
    }

    fun notifyImageChanged() { _imageVersion++ }

    fun setEnabled(scope: String, v: Boolean) { write(scope, config(scope).copy(enabled = v)) }
    fun setOpacity(scope: String, v: Float) { write(scope, config(scope).copy(opacity = v)) }
    fun setBlur(scope: String, v: Float) { write(scope, config(scope).copy(blur = v)) }
    fun setBrightness(scope: String, v: Float) { write(scope, config(scope).copy(brightness = v)) }
    fun setThemeColor(scope: String, v: Int?) { write(scope, config(scope).copy(themeColor = v)) }

    private fun write(scope: String, cfg: BgConfig) {
        if (scope == BackgroundManager.SCOPE_SHOWER) _shower = cfg else _home = cfg
        PrefsHelper.bgPutBool(scope, "enabled", cfg.enabled)
        PrefsHelper.bgPutFloat(scope, "opacity", cfg.opacity)
        PrefsHelper.bgPutFloat(scope, "blur", cfg.blur)
        PrefsHelper.bgPutFloat(scope, "brightness", cfg.brightness)
        PrefsHelper.bgPutInt(scope, "themeColor", cfg.themeColor ?: 0)
    }

    private fun readConfig(scope: String): BgConfig = BgConfig(
        enabled = PrefsHelper.bgGetBool(scope, "enabled", false),
        opacity = PrefsHelper.bgGetFloat(scope, "opacity", DEFAULT_OPACITY),
        blur = PrefsHelper.bgGetFloat(scope, "blur", DEFAULT_BLUR),
        brightness = PrefsHelper.bgGetFloat(scope, "brightness", DEFAULT_BRIGHTNESS),
        themeColor = PrefsHelper.bgGetInt(scope, "themeColor", 0).takeIf { it != 0 }
    )
}
