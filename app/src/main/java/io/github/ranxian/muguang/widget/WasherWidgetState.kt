package io.github.ranxian.muguang.widget

import com.google.gson.JsonParser
import io.github.ranxian.muguang.model.DeviceInfo

/**
 * 桌面小组件的洗衣机运行态快照。
 *
 * App 侧把 `WasherSession` 写成 project-scoped 的 JSON（`PrefsHelper.washerSessionJson`），
 * 小组件只读这份本地快照，不联网、不轮询。
 */
data class WasherWidgetSnapshot(
    val deviceName: String,
    val modeName: String,
    val remainingSec: Int,
    val preDeductYuan: Double,
    val startedAtMs: Long
)

object WasherWidgetState {
    /**
     * 从 `WasherSession` 的 JSON 里解出小组件需要的最小字段。
     *
     * 用 `JsonObject` 而不是直接 `Gson.fromJson` 到 `WasherSession`：
     * Gson 会绕过 Kotlin 构造函数给缺失字段写 null，`session.mode` 这种非空类型
     * 一旦 JSON 是旧版本/半截的，就会在取值时 NPE 把整个小组件带崩。
     *
     * 返回 null 的三种情况：JSON 空/损坏、缺少 device/mode、按模式时长算已结束。
     */
    fun from(json: String?, nowMs: Long): WasherWidgetSnapshot? {
        if (json.isNullOrBlank()) return null
        val root = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val device = root.getAsJsonObject("device") ?: return null
        val mode = root.getAsJsonObject("mode") ?: return null

        val rawName = device.get("deviceName")
            ?.takeIf { !it.isJsonNull }?.asString
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val modeName = listOf("typeName", "modeName", "descname")
            .mapNotNull { key ->
                mode.get(key)?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            }
            .firstOrNull() ?: "洗衣机"

        val totalSec = mode.get("times")?.takeIf { !it.isJsonNull }?.asInt ?: 0
        val startedAt = root.get("startedAtMs")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
        if (startedAt <= 0L || totalSec <= 0) return null

        val elapsedSec = ((nowMs - startedAt) / 1000L).toInt().coerceAtLeast(0)
        val remainingSec = (totalSec - elapsedSec).coerceAtLeast(0)
        if (remainingSec <= 0) return null

        val preDeductMilliYuan = root.get("preDeductMilliYuan")
            ?.takeIf { !it.isJsonNull }?.asInt ?: 0

        return WasherWidgetSnapshot(
            deviceName = DeviceInfo.formatDeviceName(rawName),
            modeName = modeName,
            remainingSec = remainingSec,
            preDeductYuan = preDeductMilliYuan.coerceAtLeast(0) / 1000.0,
            startedAtMs = startedAt
        )
    }

    /** 「剩余 1分05秒」/「剩余 42秒」——小组件面板只有一行，不用时分秒 */
    fun formatRemaining(sec: Int): String {
        val total = sec.coerceAtLeast(0)
        val minutes = total / 60
        val seconds = total % 60
        return if (minutes > 0) {
            "${minutes}分${seconds.toString().padStart(2, '0')}秒"
        } else {
            "${seconds}秒"
        }
    }
}
