package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.model.OrderLastBean
import io.github.ranxian.muguang.model.WasherStatus
import io.github.ranxian.muguang.model.parseRemainingHint

/**
 * 已绑定洗衣机在界面上的投影。
 *
 * 与「正在使用的洗衣机」（[io.github.ranxian.muguang.model.WasherSession]）不同：
 * 绑定只是记住一台设备，**不要求是本机下的单**——别人正在洗也应当能看到
 * 状态与剩余时间。
 *
 * ## 为什么是两个数据源
 *
 * - `order/tcpDevice/washer/status` 是**本机视角**：有的学校对别人的订单直接回
 *   `errorCode 0`（= 空闲），绑定卡片因此把「别人正在洗」显示成「空闲」。
 * - `order/query/lastOrder/byMac` 是**设备视角**：只认 mac，谁的单都回，但没有
 *   剩余秒数字段。
 *
 * 所以先按设备视角判「有没有人在用」，再用本机视角的 `time`/`expireTimes` 拿准确
 * 倒计时；拿不到就用 `modeTime − (now − consumeDate)` 估算。
 */
data class BoundWasherView(
    /** 空闲 / 使用中 / 他人使用中 / 状态未知 */
    val statusLabel: String,
    val stageText: String,
    /** 剩余秒数；0 表示未知或未在使用 */
    val remainingSec: Int,
    val isActive: Boolean,
    val isOwner: Boolean,
    /** 是否真的拿到了服务端状态（false 时其余字段都是占位） */
    val known: Boolean,
    /**
     * 剩余时间是不是「估」出来的。
     *
     * 服务端对别人的订单没有剩余秒，只能 `modeTime − 已用时长` 推；起点还是下单时间，
     * 排队单能差十几分钟。**所以界面不显示估算的剩余时间**，改显示「对方几点下单、
     * 已过多久」——那两个数都是服务端事实，不会错。
     */
    val remainingIsEstimate: Boolean = false,
    /** 对方下单时刻（服务器时间 ms）。0 = 未知。别人的单展示「HH:mm 下单 · 已过 …」 */
    val orderPlacedAtMs: Long = 0L
)

/** 剩余秒 + 这个数是不是估算 */
private data class Remaining(val sec: Int, val estimated: Boolean)

object BoundWasherState {

    /**
     * @param status 本机视角状态（`washer/status`；null = 网络/解析失败）
     * @param lastOrder 设备视角最近一单（`lastOrder/byMac`；null = 没有单或查询失败）
     * @param myAccountId 当前登录的 `accountId`，用来判断那单是不是自己的；null = 归因未知
     * @param startedAtOverrideMs 轮询亲眼看到这一单开始跑的时刻（服务器时钟）；null = 没看到
     * @param startIsObserved [startedAtOverrideMs] 是观测值（true）还是预测值（false）。
     *   预测值是「上一单还没洗完，这单在排队」推出来的上水时刻，仍要标「约」。
     * @param nowMs 当前时刻，**必须用服务器时钟**（见 `NetworkModule.serverNowMs()`）
     */
    fun from(
        status: WasherStatus?,
        lastOrder: OrderLastBean? = null,
        sessionStartedAtMs: Long? = null,
        nowMs: Long = System.currentTimeMillis(),
        failed: Boolean = false,
        myAccountId: String? = null,
        startedAtOverrideMs: Long? = null,
        startIsObserved: Boolean = false
    ): BoundWasherView {
        if (failed || (status == null && lastOrder == null)) {
            return BoundWasherView(
                statusLabel = "状态未知",
                stageText = "未能获取设备状态",
                remainingSec = 0,
                isActive = false,
                isOwner = true,
                known = false
            )
        }

        val statusActive = status?.looksActive == true
        val orderActive = lastOrder?.isRunning(nowMs, startedAtOverrideMs) == true
        val active = statusActive || orderActive

        val owner = when {
            !active -> true
            // 本机视角接口自己会带 isOwner，优先信它
            statusActive && status != null -> status.isOwner
            else -> {
                val orderAccount = lastOrder?.accountId?.takeIf { it.isNotBlank() }
                val mine = myAccountId?.takeIf { it.isNotBlank() }
                // 归因不明时不说「他人」——宁可中性显示「使用中」
                if (orderAccount != null && mine != null) orderAccount == mine else true
            }
        }

        val label = when {
            !active -> "空闲"
            !owner -> "他人使用中"
            else -> "使用中"
        }

        val remaining = if (!active) Remaining(0, false) else remainingDetail(
            status = status,
            lastOrder = lastOrder,
            statusActive = statusActive,
            orderActive = orderActive,
            sessionStartedAtMs = sessionStartedAtMs,
            startedAtOverrideMs = startedAtOverrideMs,
            startIsObserved = startIsObserved,
            nowMs = nowMs
        )

        // 服务端的 stageText 兜底是「设备运行中」——那是对「真在跑」的兜底；
        // 空闲设备显示这句会误导，这里换成中性文案
        val stage = when {
            !active -> "等待使用"
            statusActive && status != null ->
                status.stageText.takeIf { it.isNotBlank() } ?: "设备运行中"
            orderActive && lastOrder != null ->
                lastOrder.stageText.takeIf { it.isNotBlank() } ?: "设备运行中"
            else -> "设备运行中"
        }

        return BoundWasherView(
            statusLabel = label,
            stageText = stage,
            remainingSec = remaining.sec,
            isActive = active,
            isOwner = owner,
            known = true,
            remainingIsEstimate = remaining.estimated,
            orderPlacedAtMs = if (active) lastOrder?.startAtMs ?: 0L else 0L
        )
    }

    /**
     * 剩余秒数，按可信度排序：
     *
     * 1. 本机视角的 `time`（= `expireTimes`，官方 `CountdownView.setTime(time)` 直接当
     *    倒计时初值）——服务端精确值。
     * 2. 服务端文案里明写「剩余 X 分钟」（`liquidOrderStatusDisplay`）——也是精确值。
     * 3. 轮询亲眼看到的启动时刻：`modeTime − (now − 观测时刻)`，误差最多一个轮询间隔。
     * 4. 别人在用的兜底估算：`modeTime − (now − consumeDate)`——**下单时间≠上水时间**，
     *    排队的单会偏小十几分钟，所以标成估算。
     * 5. 自己的单但服务端还没回剩余秒：`modeTime − 本机已用时长`。
     */
    private fun remainingDetail(
        status: WasherStatus?,
        lastOrder: OrderLastBean?,
        statusActive: Boolean,
        orderActive: Boolean,
        sessionStartedAtMs: Long?,
        startedAtOverrideMs: Long?,
        startIsObserved: Boolean,
        nowMs: Long
    ): Remaining {
        // 1. 服务端剩余秒。个别校区会把「已用」塞进 time，超过总时长的值不可信，夹一下
        if (status != null && status.time > 0) {
            val capped = if (status.modeTime > 0) status.time.coerceAtMost(status.modeTime) else status.time
            return Remaining(capped.coerceAtLeast(0), false)
        }

        // 2. 服务端文案里带剩余时间
        parseRemainingHint(status?.liquidOrderStatusDisplay).takeIf { it > 0 }
            ?.let { return Remaining(it, false) }
        parseRemainingHint(lastOrder?.liquidOrderStatusDisplay).takeIf { it > 0 }
            ?.let { return Remaining(it, false) }

        // 3. 看到了这一单的开始（观测或排队预测）——比下单时间可靠得多
        if (orderActive && lastOrder != null && startedAtOverrideMs != null && startedAtOverrideMs > 0L) {
            val seen = lastOrder.estimateRemainingSec(nowMs, startedAtOverrideMs)
            if (seen > 0) return Remaining(seen, !startIsObserved)
        }

        // 4. 按下单时间估算（唯一的大误差来源）
        if (orderActive && lastOrder != null) {
            val estimated = lastOrder.estimateRemainingSec(nowMs)
            if (estimated > 0) return Remaining(estimated, true)
        }

        // 5. 自己的单：本机按下开始的时刻
        if (statusActive && status != null && status.modeTime > 0 && status.isOwner &&
            sessionStartedAtMs != null && sessionStartedAtMs > 0L
        ) {
            val elapsed = ((nowMs - sessionStartedAtMs) / 1000L).toInt().coerceAtLeast(0)
            return Remaining((status.modeTime - elapsed).coerceAtLeast(0), false)
        }
        return Remaining(0, false)
    }

    /** 剩余秒数（不含「是否估算」标记，测试与旧调用用） */
    fun remainingSeconds(
        status: WasherStatus?,
        lastOrder: OrderLastBean?,
        statusActive: Boolean,
        orderActive: Boolean,
        sessionStartedAtMs: Long?,
        nowMs: Long,
        startedAtOverrideMs: Long? = null
    ): Int = remainingDetail(
        status = status,
        lastOrder = lastOrder,
        statusActive = statusActive,
        orderActive = orderActive,
        sessionStartedAtMs = sessionStartedAtMs,
        startedAtOverrideMs = startedAtOverrideMs,
        startIsObserved = false,
        nowMs = nowMs
    ).let { it.sec }

    /** 兼容旧调用：只有本机视角状态时的剩余秒数 */
    fun remainingSeconds(status: WasherStatus, sessionStartedAtMs: Long?, nowMs: Long): Int {
        if (status.time > 0) return status.time
        if (status.modeTime > 0 && status.isOwner &&
            sessionStartedAtMs != null && sessionStartedAtMs > 0L
        ) {
            val elapsed = ((nowMs - sessionStartedAtMs) / 1000L).toInt().coerceAtLeast(0)
            return (status.modeTime - elapsed).coerceAtLeast(0)
        }
        return 0
    }

    /**
     * 两次刷新之间的本地倒数：刷新后 [baseSec] 秒，过 [updatedAtMs] 后每秒减一。
     * 刷新失败/未启用倒数（base 或时间戳非正）时原样返回。
     */
    fun countdown(baseSec: Int, updatedAtMs: Long, nowMs: Long): Int {
        if (baseSec <= 0 || updatedAtMs <= 0L) return baseSec.coerceAtLeast(0)
        val elapsed = ((nowMs - updatedAtMs) / 1000L).toInt().coerceAtLeast(0)
        return (baseSec - elapsed).coerceAtLeast(0)
    }

    /** `12:05`；超过 1 小时显示 `1:02:05` */
    fun formatClock(sec: Int): String {
        val total = sec.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }

    /**
     * 首页「我的洗衣机」卡片里每台设备下面那行小字（纯函数，单测覆盖）。
     *
     * 展示优先级——只说服务端能给的事实：
     * 1. 拉取失败 → 「未能获取设备状态」，不能落「空闲」骗人说机器能用
     * 2. 空闲 → 「空闲」
     * 3. 服务端精确剩余秒 → 「剩余 12:05」（两次刷新之间用本地倒数续走）
     * 4. 别人的单 → 「14:32 下单」——估算值不显示（排队单能差十几分钟），
     *    下单时间是服务端事实，不会错
     * 5. 自己的单但只剩估算 → 「约剩 12:05」，标「约」
     * 6. 都没有 → 阶段文案兜底
     */
    fun cardSubline(view: BoundWasherView, updatedAtMs: Long, nowMs: Long): String = when {
        !view.known -> "未能获取设备状态"
        !view.isActive -> "空闲"
        view.remainingSec > 0 && !view.remainingIsEstimate ->
            "剩余 " + paddedClock(countdown(view.remainingSec, updatedAtMs, nowMs))
        !view.isOwner && view.orderPlacedAtMs > 0 ->
            formatHourMinute(view.orderPlacedAtMs) + " 下单"
        view.remainingSec > 0 ->
            "约剩 " + paddedClock(countdown(view.remainingSec, updatedAtMs, nowMs))
        else -> view.stageText.ifBlank { "运行中" }
    }

    /**
     * `05:00` / `1:02:05`——卡片行内专用：分钟补成两位，行宽稳定，
     * 「剩余 05:00」和「剩余 12:05」在卡片里对得整齐。
     * [formatClock] 不补零且别处共用，这里不改它。
     */
    private fun paddedClock(sec: Int): String {
        val total = sec.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** 服务器时间戳 → `21:03`（下单时刻用；服务器时钟已校准，分秒可信） */
    fun formatHourMinute(ms: Long): String =
        if (ms <= 0L) "" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
            .format(java.util.Date(ms))
}
