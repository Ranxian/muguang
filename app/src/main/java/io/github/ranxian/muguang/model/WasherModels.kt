package io.github.ranxian.muguang.model

import com.google.gson.annotations.SerializedName

data class WasherMode(
    val id: Long? = null,
    @SerializedName(value = "typeId", alternate = ["modeId"])
    val typeId: Int = 0,
    @SerializedName(value = "typeName", alternate = ["modeName"])
    val typeName: String? = null,
    val typeValue: String? = null,
    val projectId: String? = null,
    val smallTypeId: String? = null,
    @SerializedName(value = "money", alternate = ["modeMoney"])
    val money: Int = 0,
    @SerializedName(value = "times", alternate = ["modeTimeQuantity"])
    val times: Int = 0,
    val modeMoneyYuan: String? = null,
    val modeTimeMinute: String? = null,
    val positionId: Int? = null,
    val goodsId: Int = 0,
    val isSelected: Int = 0,
    @SerializedName(value = "descname", alternate = ["modeDesc"])
    val descname: String? = null
) {
    val displayName: String get() = typeName?.takeIf { it.isNotBlank() } ?: "未命名模式"


    val priceYuan: Double
        get() = modeMoneyYuan?.toDoubleOrNull()
            ?: (money.coerceAtLeast(0) / 1000.0)


    val durationMinutes: Int
        get() = modeTimeMinute?.toIntOrNull()?.takeIf { it > 0 }
            ?: (times.coerceAtLeast(0) + 59) / 60


    val isMainMode: Boolean get() = positionId == null || positionId == 0
}

fun washerMainModes(modes: List<WasherMode>): List<WasherMode> =
    modes.filter { it.isMainMode }
        .distinctBy { it.typeId }

data class WasherStartData(
    val isOwner: Boolean = true,
    val perMoney: Int = 0,

    val timeIds: String? = null
)

data class WasherStatus(
    val isOwner: Boolean = true,
    @SerializedName(value = "modelName", alternate = ["modeName"])
    val modelName: String? = null,
    val modeTime: Int = 0,
    val modeMoney: Int = 0,
    /**
     * 剩余秒数。官方 `SnWasherStatusResponse` 里该字段的正式名是 `expireTimes`，
     * `time` 只是它在对账接口里的别名（`@SerializedName(value="time", alternate={"expireTimes"})`）。
     * v3 接口实测下发 `expireTimes`，只声明 `time` 会永远解析出 0——表现为
     * 「使用中但剩余时间未知」。
     */
    @SerializedName(value = "time", alternate = ["expireTimes"])
    val time: Int = 0,
    @SerializedName(value = "timeIds", alternate = ["orderNo"])
    val timeIds: String? = null,
    val liquidOrderNo: String? = null,
    val liquidOrderStatus: Int = 0,
    val liquidOrderStatusDisplay: String? = null,
    val liquidModeName: String? = null,
    val liquidConsumeMoney: Int = 0,
    val perMoney: Int = 0,
    val leftModeName: String? = null,
    val leftModeMoney: Int = 0,
    val leftGoodsName: String? = null,
    val rightModeName: String? = null,
    val rightModeMoney: Int = 0,
    val rightGoodsName: String? = null
) {
    val orderKey: String get() = timeIds?.takeIf { it.isNotBlank() }
        ?: liquidOrderNo?.takeIf { it.isNotBlank() }
        ?: ""

    val stageText: String get() =
        liquidOrderStatusDisplay?.takeIf { it.isNotBlank() }
            ?: modelName?.takeIf { it.isNotBlank() }
            ?: "设备运行中"


    /**
     * 剩余秒数（服务端 `time` / `expireTimes`）> 0 即视为有进行中订单。
     * 空闲设备两种表现：`data == null`（errorCode 0），或 data 存在但全零——
     * 两者都不能据此断定"失败"。
     *
     * ⚠️ **不要**再用 `liquidOrderStatus != 0` 判活跃：官方 `w7()` 里
     * `0` = 运行中（橙色 + 点击才开始刷新动画）、`1` = 已完成、`2` = 异常，
     * 老写法正好把「已完成」当成「在用」。
     */
    val looksActive: Boolean get() =
        orderKey.isNotEmpty() || time > 0 || modeTime > 0

    /** 是否拿到了任何服务端字段（区分「空闲/无 data」与「响应损坏」） */
    val hasAnyField: Boolean get() =
        modelName != null || orderKey.isNotEmpty() ||
            time > 0 || modeTime > 0 || liquidOrderStatusDisplay != null
}

/**
 * `GET /order/query/lastOrder/byMac` 的 data（官方 `OrderLastBean`）。
 *
 * 这是**设备视角**的最近一单：`washer/status` 是否回别人的单要看服务端实现
 * （实测有的学校只回自己的，别人的单根本不出现），而这个接口只认 mac，
 * 官方在 `WashingNetActivity.eventMsg("TIME_FINISH")` 里也是拿 `devMac` 直接查。
 *
 * 注意：**没有剩余秒数字段**。别人在用时的倒计时只能用
 * `modeTime - (now - consumeDate)` 估算（见 [estimateRemainingSec]）。
 */
data class OrderLastBean(
    @SerializedName(value = "deviceMac", alternate = ["macAddress"])
    val deviceMac: String? = null,
    @SerializedName(value = "money", alternate = ["modeMoney"])
    val money: Int = 0,
    @SerializedName(value = "perMoney", alternate = ["preDeductMoney"])
    val perMoney: String? = null,
    @SerializedName(value = "typeId", alternate = ["xfModel"])
    val typeId: Int = 0,
    @SerializedName(value = "typeName", alternate = ["modeName"])
    val typeName: String? = null,
    val accountId: String? = null,
    /** 下单时间，`yyyy-MM-dd HH:mm:ss`（官方直接拿去当账单时间显示） */
    val consumeDate: String? = null,
    val deviceId: Long = 0,
    val deviceName: String? = null,
    val liquidOrderNo: String? = null,
    /** 0 = 运行中，1 = 已完成，2 = 异常（官方 `w7()` 的颜色分支） */
    val liquidOrderStatus: Int = 0,
    val liquidOrderStatusDisplay: String? = null,
    val leftGoodsName: String? = null,
    val leftModeMoney: Int = 0,
    val leftModeName: String? = null,
    /** 本次模式总时长（秒），不是剩余时间 */
    val modeTime: Int = 0,
    val orderId: String? = null,
    val orderNo: String? = null,
    val rightGoodsName: String? = null,
    val rightModeMoney: Int = 0,
    val rightModeName: String? = null,
    val showLiquidBillInfo: Int = 0,
    val smallTypeId: Int = 0
) {
    val orderKey: String get() = liquidOrderNo?.takeIf { it.isNotBlank() }
        ?: orderNo?.takeIf { it.isNotBlank() }
        ?: ""

    val stageText: String get() = liquidOrderStatusDisplay?.takeIf { it.isNotBlank() }
        ?: typeName?.takeIf { it.isNotBlank() }
        ?: "设备运行中"

    /** `consumeDate` 解析出来的下单时刻；解析不了返回 0 */
    val startAtMs: Long get() = parseServerTime(consumeDate)

    /**
     * 有人正在用。
     *
     * 条件 = 有单号 + 状态 0；如果知道模式时长，再排掉「早就该结束却还挂在 0」的脏单
     * （超时 5 分钟以上就当它已经结束，避免一单卡住让绑定卡片永远显示使用中）。
     *
     * @param startedAtMs 亲眼观测到的启动时刻（轮询到订单开始跑）。传了就以它为准，
     *   因为 `consumeDate` 是**下单**时间，排队的单可能比真正上水晚十几分钟。
     */
    fun isRunning(nowMs: Long, startedAtMs: Long? = null): Boolean {
        if (liquidOrderStatus != 0) return false
        if (orderKey.isEmpty()) return false
        val startMs = startedAtMs?.takeIf { it > 0L } ?: startAtMs
        if (modeTime > 0 && startMs > 0L) {
            val elapsed = (nowMs - startMs) / 1000L
            if (elapsed > modeTime + STALE_GRACE_SEC) return false
        }
        return true
    }

    /**
     * 别人在用时的剩余秒数估算：`modeTime − 已用时长`。
     * 没有 `modeTime` 或起点解析不了时返回 0（界面显示「剩余时间未知」）。
     *
     * @param startedAtMs 观测到的真实启动时刻；不传就用 `consumeDate`（下单时间）。
     *   这是估算误差的唯一来源：下单 ≠ 上水，中间可能隔着上一个用户的余量。
     */
    fun estimateRemainingSec(nowMs: Long, startedAtMs: Long? = null): Int {
        if (modeTime <= 0) return 0
        val startMs = startedAtMs?.takeIf { it > 0L } ?: startAtMs
        if (startMs <= 0L) return 0
        val elapsed = ((nowMs - startMs) / 1000L).toInt().coerceAtLeast(0)
        return (modeTime - elapsed).coerceAtLeast(0)
    }

    companion object {
        /** 超时宽限：模式时长走完还挂 5 分钟以上就按「已结束」处理 */
        const val STALE_GRACE_SEC = 300
    }
}

/**
 * 从服务端文案里抠「还剩多久」。
 *
 * 服务端有 `liquidOrderStatusDisplay` 这类直接给人看的字段，实际文案由后端配置，
 * 见过「运行中」，也可能下发「剩余41分钟」——真有数字的话比任何估算都准。
 *
 * ⚠️ **必须带「剩/余/还有」才认**：模式名那类文本里也有「45分钟」（那是总时长，
 * 不是剩余），误当剩余会让卡片一直显示满时长。
 */
internal fun parseRemainingHint(raw: String?): Int {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return 0
    if (!text.contains("剩") && !text.contains("余") && !text.contains("还有")) return 0

    Regex("""(\d+)\s*小时""").find(text)?.let { h ->
        val hours = h.groupValues[1].toIntOrNull() ?: return@let
        val minutes = Regex("""小时\s*(\d+)\s*分""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val seconds = Regex("""分(?:钟)?\s*(\d+)\s*秒""").find(text)
            ?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }
    Regex("""(\d+)\s*分(?:钟)?(?:\s*(\d+)\s*秒)?""").find(text)?.let { m ->
        val minutes = m.groupValues[1].toIntOrNull() ?: return@let
        val seconds = m.groupValues.getOrNull(2)?.toIntOrNull() ?: 0
        return minutes * 60 + seconds
    }
    Regex("""(\d+)\s*秒""").find(text)?.let { m ->
        return m.groupValues[1].toIntOrNull() ?: 0
    }
    // 倒计时样式：`剩余 12:34` / `剩余 1:02:34`
    Regex("""(\d{1,2}):(\d{2})(?::(\d{2}))?""").find(text)?.let { m ->
        val first = m.groupValues[1].toIntOrNull() ?: return@let
        val second = m.groupValues[2].toIntOrNull() ?: return@let
        val third = m.groupValues.getOrNull(3)?.toIntOrNull()
        return if (third != null) first * 3600 + second * 60 + third else first * 60 + second
    }
    return 0
}

/**
 * 服务端时间串 → 毫秒时间戳。支持常见的几种格式，解析失败返回 0。
 *
 * 官方 `consumeDate` 是 `yyyy-MM-dd HH:mm:ss`（`OrderLastBean.consumeDate` 被直接
 * 当账单时间显示），但不同学校有过换格式的先例，这里多试几种。
 */
internal fun parseServerTime(raw: String?): Long {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return 0L
    if (text.all { it.isDigit() }) {
        val v = text.toLongOrNull() ?: return 0L
        // 10 位当秒、13 位当毫秒
        return when {
            v > 100_000_000_000L -> v
            v > 1_000_000_000L -> v * 1000L
            else -> 0L
        }
    }
    val normalized = text.replace('T', ' ')
    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss"
    )
    for (pattern in patterns) {
        val parsed = runCatching {
            java.text.SimpleDateFormat(pattern, java.util.Locale.CHINA)
                .apply { isLenient = false }
                .parse(normalized)
        }.getOrNull()
        if (parsed != null) return parsed.time
    }
    return 0L
}

data class WasherSession(
    val device: DeviceInfo,
    val mode: WasherMode,
    val orderId: String,
    val startedAtMs: Long = System.currentTimeMillis(),
    val preDeductMilliYuan: Int = 0
) {
    val preDeductYuan: Double get() = preDeductMilliYuan.coerceAtLeast(0) / 1000.0
}
