package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.model.OrderLastBean

/**
 * 「绑定洗衣机的订单是什么时候真正开始洗」的观测器，一台设备一个实例。
 *
 * 服务端不给别人订单的剩余秒，`consumeDate` 又只是**下单**时间：上一单还没洗完就
 * 付钱的单会排队，真正上水可能晚十几分钟——按 `consumeDate` 算就会少报十几分钟
 * （用户实测：机器剩 41 分钟，卡片说 28 分钟）。
 *
 * 两条路把它定准：
 * - 轮询看到订单号切换 → 启动就在上一轮和这一轮之间，取中点；
 * - 上一单还在跑就出现了新单 → 新单在排队，启动时间 ≈ 上一单的结束时间。
 *
 * 以前这套状态是 [io.github.ranxian.muguang.ui.MainViewModel] 的一组散字段，
 * 只能跟踪一台设备；支持双机绑定后按 snCode 各配一个 tracker，
 * 逻辑本身原样搬过来（含全部判据与注释），行为不变。
 */
class BoundOrderTracker {

    /** 正在盯的那一单的订单号 */
    private var orderKey: String = ""
    /** 已确定的启动时刻（服务器时钟） */
    private var startedAtMs: Long = 0L
    /** 上一轮轮询看到的订单号与服务器时刻，用来判断「订单号是不是这一轮才换的」 */
    private var lastPollOrderKey: String = ""
    private var lastPollServerMs: Long = 0L
    /** 上一轮正在跑的那单（订单号 / 总时长 / 启动时刻），用来判断新单是不是在排队 */
    private var prevRunningKey: String = ""
    private var prevRunningModeTime: Int = 0
    private var prevRunningStartMs: Long = 0L

    /**
     * 喂进这一轮的 `lastOrder/byMac` 响应，返回 (启动时刻毫秒, 是否亲眼观测到)。
     * 第二个值为 false 时那个时刻只是预测，界面仍要标「约」。
     *
     * lastOrder == null 既可能是「没有单」也可能是「这次查询失败」，分不出来，
     * 所以不清状态：真没单的话状态会在下一轮正常收敛，别因为一次抖动把观测到的启动时刻洗掉。
     */
    fun observe(lastOrder: OrderLastBean?, nowMs: Long): Pair<Long, Boolean> {
        if (lastOrder == null) {
            return startedAtMs to (startedAtMs > 0L)
        }

        val currentKey = lastOrder.orderKey
        val runningNow = lastOrder.isRunning(nowMs)

        var startMs = 0L
        var observed = false
        if (runningNow && currentKey.isNotEmpty()) {
            if (currentKey == orderKey && startedAtMs > 0L) {
                // 之前就盯着这一单，启动时刻已经定下来了
                startMs = startedAtMs
                observed = true
            } else {
                val prevEndMs = if (prevRunningKey.isNotEmpty() && prevRunningKey != currentKey &&
                    prevRunningModeTime > 0 && prevRunningStartMs > 0L
                ) {
                    prevRunningStartMs + prevRunningModeTime * 1000L
                } else {
                    0L
                }
                // 只有上一轮刚看过（3 分钟内）才算「亲眼看到切换」；
                // 放太久的话中点会落在很久以前，反而更离谱
                val sawSwitch = lastPollOrderKey.isNotEmpty() && currentKey != lastPollOrderKey &&
                    lastPollServerMs > 0L && nowMs - lastPollServerMs <= 180_000L
                when {
                    // 上一单还没洗完：这单在排队，真正上水在上一单结束之后
                    prevEndMs > nowMs -> {
                        startMs = prevEndMs
                        observed = false
                    }
                    // 亲眼看到订单号在这一轮切换：启动发生在两次轮询之间
                    sawSwitch -> {
                        startMs = (lastPollServerMs + nowMs) / 2
                        observed = true
                    }
                    // 第一次看到这一单，不知道它什么时候开始的 → 交给 consumeDate 估算
                    else -> startMs = 0L
                }
            }
        }

        orderKey = if (runningNow) currentKey else ""
        startedAtMs = startMs

        if (runningNow) {
            prevRunningKey = currentKey
            prevRunningModeTime = lastOrder.modeTime
            prevRunningStartMs = startMs.takeIf { it > 0L } ?: lastOrder.startAtMs
        } else {
            prevRunningKey = ""
            prevRunningModeTime = 0
            prevRunningStartMs = 0L
        }
        lastPollOrderKey = currentKey
        lastPollServerMs = nowMs
        return startMs to observed
    }

    /** 解绑 / 切项目时清干净，别把 A 设备的观测串到 B 设备上 */
    fun reset() {
        orderKey = ""
        startedAtMs = 0L
        lastPollOrderKey = ""
        lastPollServerMs = 0L
        prevRunningKey = ""
        prevRunningModeTime = 0
        prevRunningStartMs = 0L
    }
}
