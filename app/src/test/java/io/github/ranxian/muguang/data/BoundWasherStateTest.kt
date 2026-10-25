package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.model.OrderLastBean
import io.github.ranxian.muguang.model.WasherStatus
import io.github.ranxian.muguang.model.parseRemainingHint
import io.github.ranxian.muguang.model.parseServerTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundWasherStateTest {

    @Test
    fun `active owner shows remaining from server time`() {
        val status = WasherStatus(isOwner = true, modelName = "标准洗", modeTime = 1800, time = 605)
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertTrue(view.known)
        assertTrue(view.isActive)
        assertTrue(view.isOwner)
        assertEquals("使用中", view.statusLabel)
        assertEquals("标准洗", view.stageText)
        assertEquals(605, view.remainingSec)
    }

    @Test
    fun `someone else using still exposes remaining time`() {
        val status = WasherStatus(isOwner = false, modelName = "快洗", modeTime = 1200, time = 300)
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertTrue(view.isActive)
        assertFalse(view.isOwner)
        assertEquals("他人使用中", view.statusLabel)
        assertEquals(300, view.remainingSec)
    }

    @Test
    fun `others order without expireTimes shows unknown remaining instead of fake countdown`() {
        // 他人订单：无 time 且没有可信起点 → 不用 modeTime 编造倒计时
        val status = WasherStatus(isOwner = false, modeTime = 1200, timeIds = "T9")
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertTrue(view.isActive)
        assertEquals("他人使用中", view.statusLabel)
        assertEquals(0, view.remainingSec)
    }

    @Test
    fun `expireTimes alternate name parses as time`() {
        // Gson 走 alternate 名（v3 接口实际下发 expireTimes），
        // 这里用 Gson 直接反序列化验证别名生效
        val gson = com.google.gson.Gson()
        val status = gson.fromJson(
            """{"isOwner":false,"expireTimes":420,"timeIds":"T1","modelName":"标准洗"}""",
            WasherStatus::class.java
        )
        assertEquals(420, status.time)
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertTrue(view.isActive)
        assertEquals("他人使用中", view.statusLabel)
        assertEquals(420, view.remainingSec)
    }

    @Test
    fun `idle device shows idle and no countdown`() {
        val status = WasherStatus(isOwner = true)
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertTrue(view.known)
        assertFalse(view.isActive)
        assertEquals("空闲", view.statusLabel)
        assertEquals(0, view.remainingSec)
        assertEquals("等待使用", view.stageText)
    }

    @Test
    fun `failed query is unknown rather than idle`() {
        val view = BoundWasherState.from(null, sessionStartedAtMs = null, nowMs = 1_000L, failed = true)
        assertFalse(view.known)
        assertEquals("状态未知", view.statusLabel)
        assertEquals(0, view.remainingSec)
    }

    @Test
    fun `remaining falls back to modeTime minus own session start`() {
        // looksActive 要求订单存在（timeIds / liquidOrderStatus / time 三者之一），
        // 只有 time>0 之外的"有订单但服务端还没回剩余秒"才会落到 modeTime 回退
        val status = WasherStatus(isOwner = true, modeTime = 1800, timeIds = "T123")
        val view = BoundWasherState.from(
            status,
            sessionStartedAtMs = 1_000_000L,
            nowMs = 1_000_000L + 120_000L,
            failed = false
        )
        assertEquals(1800 - 120, view.remainingSec)
    }

    @Test
    fun `remaining stays unknown for others order without server time`() {
        val status = WasherStatus(isOwner = false, modeTime = 1800, time = 0)
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertEquals(0, view.remainingSec)
    }

    @Test
    fun `idle controller marker renders as idle not unknown`() {
        // WasherController 对 errorCode==0 且 data==null 返回全零标记对象；
        // 它必须被投影为「空闲」而不是「状态未知」
        val status = WasherStatus(isOwner = true)
        val view = BoundWasherState.from(status, sessionStartedAtMs = null, nowMs = 1_000L, failed = false)
        assertTrue(view.known)
        assertEquals("空闲", view.statusLabel)
    }

    // ── 双查：设备视角 lastOrder/byMac 补「别人在用却显示空闲」的洞 ──

    @Test
    fun `other users running order from lastOrder beats idle status`() {
        // 现象：别人正在洗，washer/status 却回空闲（errorCode 0）
        val idleStatus = WasherStatus(isOwner = true)
        val startedMs = 1_000_000L
        val lastOrder = OrderLastBean(
            accountId = "someone-else",
            consumeDate = format(startedMs),
            liquidOrderNo = "L2026",
            liquidOrderStatus = 0,
            modeTime = 1800,
            typeName = "标准洗"
        )
        val view = BoundWasherState.from(
            status = idleStatus,
            lastOrder = lastOrder,
            sessionStartedAtMs = null,
            nowMs = startedMs + 600_000L,
            failed = false,
            myAccountId = "me"
        )
        assertTrue(view.isActive)
        assertFalse(view.isOwner)
        assertEquals("他人使用中", view.statusLabel)
        // 1800 - 600 = 1200 秒
        assertEquals(1200, view.remainingSec)
    }

    @Test
    fun `own running order from lastOrder is reported as mine`() {
        val startedMs = 2_000_000L
        val lastOrder = OrderLastBean(
            accountId = "me",
            consumeDate = format(startedMs),
            liquidOrderNo = "L1",
            liquidOrderStatus = 0,
            modeTime = 1200
        )
        val view = BoundWasherState.from(
            status = null,
            lastOrder = lastOrder,
            sessionStartedAtMs = null,
            nowMs = startedMs + 60_000L,
            failed = false,
            myAccountId = "me"
        )
        assertTrue(view.isActive)
        assertTrue(view.isOwner)
        assertEquals("使用中", view.statusLabel)
        assertEquals(1140, view.remainingSec)
    }

    @Test
    fun `finished last order is not treated as running`() {
        val startedMs = 3_000_000L
        val lastOrder = OrderLastBean(
            consumeDate = format(startedMs),
            liquidOrderNo = "L2",
            liquidOrderStatus = 1,
            modeTime = 1200
        )
        val view = BoundWasherState.from(
            status = null,
            lastOrder = lastOrder,
            sessionStartedAtMs = null,
            nowMs = startedMs + 60_000L,
            failed = false,
            myAccountId = "me"
        )
        assertFalse(view.isActive)
        assertEquals("空闲", view.statusLabel)
        assertEquals(0, view.remainingSec)
    }

    @Test
    fun `stale order past its duration drops back to idle`() {
        // 脏单：状态还是 0，但模式时长早就走完了（超过 5 分钟宽限）
        val startedMs = 4_000_000L
        val lastOrder = OrderLastBean(
            consumeDate = format(startedMs),
            liquidOrderNo = "L3",
            liquidOrderStatus = 0,
            modeTime = 600
        )
        val view = BoundWasherState.from(
            status = null,
            lastOrder = lastOrder,
            sessionStartedAtMs = null,
            nowMs = startedMs + (600 + OrderLastBean.STALE_GRACE_SEC + 1) * 1000L,
            failed = false
        )
        assertFalse(view.isActive)
        assertEquals("空闲", view.statusLabel)
    }

    @Test
    fun `server remaining wins over lastOrder estimate`() {
        val startedMs = 5_000_000L
        val status = WasherStatus(isOwner = false, time = 420, timeIds = "T1")
        val lastOrder = OrderLastBean(
            consumeDate = format(startedMs),
            liquidOrderNo = "L4",
            liquidOrderStatus = 0,
            modeTime = 1800
        )
        val view = BoundWasherState.from(
            status = status,
            lastOrder = lastOrder,
            sessionStartedAtMs = null,
            nowMs = startedMs + 60_000L,
            failed = false
        )
        assertEquals(420, view.remainingSec)
    }

    @Test
    fun `both sources failing stays unknown not idle`() {
        val view = BoundWasherState.from(
            status = null,
            lastOrder = null,
            sessionStartedAtMs = null,
            nowMs = 1_000L,
            failed = true
        )
        assertFalse(view.known)
        assertEquals("状态未知", view.statusLabel)
    }

    @Test
    fun `lastOrder json uses official alternate names`() {
        val bean = com.google.gson.Gson().fromJson(
            """{"macAddress":"AA:BB","modeMoney":300,"preDeductMoney":"3.00",
               "xfModel":7,"modeName":"快洗","liquidOrderNo":"L9",
               "liquidOrderStatus":0,"modeTime":900,"consumeDate":"2026-10-24 10:00:00"}""",
            OrderLastBean::class.java
        )
        assertEquals("AA:BB", bean.deviceMac)
        assertEquals(300, bean.money)
        assertEquals("3.00", bean.perMoney)
        assertEquals(7, bean.typeId)
        assertEquals("快洗", bean.typeName)
        assertEquals("L9", bean.orderKey)
        assertEquals(900, bean.modeTime)
        assertTrue(bean.startAtMs > 0L)
    }

    @Test
    fun `parseServerTime accepts common formats and rejects garbage`() {
        assertTrue(parseServerTime("2026-10-24 10:00:00") > 0L)
        assertTrue(parseServerTime("2026-10-24T10:00:00") > 0L)
        assertTrue(parseServerTime("2026/10/24 10:00:00") > 0L)
        assertEquals(0L, parseServerTime(""))
        assertEquals(0L, parseServerTime(null))
        assertEquals(0L, parseServerTime("not-a-time"))
    }

    // ── 剩余时间精度：服务器时钟 / 文案剩余 / 观测启动 / 估算标记 ──

    @Test
    fun `consumeDate only estimate is flagged as estimate`() {
        // 别人在用、只知道下单时间：界面必须标「约」，不能当倒计时
        val startedMs = 6_000_000L
        val lastOrder = OrderLastBean(
            consumeDate = format(startedMs), liquidOrderNo = "L5",
            liquidOrderStatus = 0, modeTime = 2700
        )
        val view = BoundWasherState.from(
            status = null, lastOrder = lastOrder, sessionStartedAtMs = null,
            nowMs = startedMs + 1_020_000L, failed = false, myAccountId = "me"
        )
        assertEquals(1680, view.remainingSec)   // 2700 - 1020
        assertTrue(view.remainingIsEstimate)
    }

    @Test
    fun `observed start overrides late consumeDate`() {
        // 用户实测：机器剩 41 分钟，按下单时间算只有 28 分钟。
        // 差别 13 分钟 = 下单后排队，真正上水晚 13 分钟。
        val orderMs = 7_000_000L
        val actuallyStartedMs = orderMs + 13 * 60_000L
        val lastOrder = OrderLastBean(
            consumeDate = format(orderMs), liquidOrderNo = "L6",
            liquidOrderStatus = 0, modeTime = 2700
        )
        val view = BoundWasherState.from(
            status = null, lastOrder = lastOrder, sessionStartedAtMs = null,
            nowMs = actuallyStartedMs + 4 * 60_000L, failed = false,
            startedAtOverrideMs = actuallyStartedMs, startIsObserved = true
        )
        assertEquals(41 * 60, view.remainingSec)
        assertFalse(view.remainingIsEstimate)
    }

    @Test
    fun `queued prediction is still marked as estimate`() {
        // 上一单还没洗完 → 预测的上水时刻，仍要标「约」
        val orderMs = 8_000_000L
        val predictedStartMs = orderMs + 13 * 60_000L
        val lastOrder = OrderLastBean(
            consumeDate = format(orderMs), liquidOrderNo = "L7",
            liquidOrderStatus = 0, modeTime = 1800
        )
        val view = BoundWasherState.from(
            status = null, lastOrder = lastOrder, sessionStartedAtMs = null,
            nowMs = orderMs + 60_000L, failed = false,
            startedAtOverrideMs = predictedStartMs, startIsObserved = false
        )
        // 还没到预测上水时间：剩余夹在总时长以内
        assertEquals(1800, view.remainingSec)
        assertTrue(view.remainingIsEstimate)
    }

    @Test
    fun `server time is exact and not flagged`() {
        val status = WasherStatus(isOwner = false, time = 2460, timeIds = "T2", modeTime = 2700)
        val view = BoundWasherState.from(
            status = status, lastOrder = null, sessionStartedAtMs = null,
            nowMs = 9_000_000L, failed = false
        )
        assertEquals(2460, view.remainingSec)
        assertFalse(view.remainingIsEstimate)
    }

    @Test
    fun `server time larger than mode duration is capped`() {
        // 个别校区把「已用」塞进 time；超过总时长的值不可信
        val status = WasherStatus(isOwner = false, time = 3600, timeIds = "T3", modeTime = 2700)
        val view = BoundWasherState.from(
            status = status, sessionStartedAtMs = null, nowMs = 9_100_000L, failed = false
        )
        assertEquals(2700, view.remainingSec)
    }

    @Test
    fun `remaining hint in status display text is used`() {
        // 服务端文案里带剩余时间时直接采信，比任何估算都准
        val status = WasherStatus(
            isOwner = false, time = 0, timeIds = "T4", modeTime = 2700,
            liquidOrderStatusDisplay = "剩余41分钟"
        )
        val view = BoundWasherState.from(
            status = status, sessionStartedAtMs = null, nowMs = 9_200_000L, failed = false
        )
        assertEquals(41 * 60, view.remainingSec)
        assertFalse(view.remainingIsEstimate)
    }

    @Test
    fun `parseRemainingHint understands server wording and ignores durations`() {
        assertEquals(41 * 60, parseRemainingHint("剩余41分钟"))
        assertEquals(41 * 60, parseRemainingHint("还剩 41 分"))
        assertEquals(60 * 60 + 2 * 60, parseRemainingHint("剩余 1 小时 2 分钟"))
        assertEquals(41 * 60 + 30, parseRemainingHint("剩余41分30秒"))
        assertEquals(30, parseRemainingHint("剩余 30 秒"))
        assertEquals(12 * 60 + 34, parseRemainingHint("剩余 12:34"))
        // 模式名里的「45分钟」是总时长，不是剩余——必须拒绝
        assertEquals(0, parseRemainingHint("标准洗45分钟"))
        assertEquals(0, parseRemainingHint("运行中"))
        assertEquals(0, parseRemainingHint(null))
    }

    @Test
    fun `order placed time is exposed for fallback display`() {
        // 别人在用但只有下单时间时，界面要显示「HH:mm 下单 · 已过 …」，
        // 这个时刻必须原样暴露给 UI（服务器时间，不做本机时区/时钟换算）
        val orderMs = 10_000_000L
        val lastOrder = OrderLastBean(
            consumeDate = format(orderMs), liquidOrderNo = "L8",
            liquidOrderStatus = 0, modeTime = 2700
        )
        val view = BoundWasherState.from(
            status = null, lastOrder = lastOrder, sessionStartedAtMs = null,
            nowMs = orderMs + 17 * 60_000L, failed = false
        )
        assertEquals(orderMs, view.orderPlacedAtMs)
        assertTrue(view.remainingIsEstimate)
        assertEquals("", BoundWasherState.formatHourMinute(0L))
        assertTrue(BoundWasherState.formatHourMinute(orderMs).matches(Regex("""\d{2}:\d{2}""")))
    }

    @Test
    fun `order placed time is zero when idle`() {
        val lastOrder = OrderLastBean(
            consumeDate = format(11_000_000L), liquidOrderNo = "L9",
            liquidOrderStatus = 1, modeTime = 1200
        )
        val view = BoundWasherState.from(
            status = null, lastOrder = lastOrder, sessionStartedAtMs = null,
            nowMs = 11_100_000L, failed = false
        )
        assertFalse(view.isActive)
        assertEquals(0L, view.orderPlacedAtMs)
    }

    private fun format(ms: Long): String =
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(ms))

    @Test
    fun `countdown ticks down and floors at zero`() {        assertEquals(600, BoundWasherState.countdown(600, 1_000L, 1_000L))
        assertEquals(595, BoundWasherState.countdown(600, 1_000L, 1_000L + 5_000L))
        assertEquals(0, BoundWasherState.countdown(600, 1_000L, 1_000L + 999_000L))
        assertEquals(0, BoundWasherState.countdown(0, 1_000L, 2_000L))
    }

    @Test
    fun `formatClock`() {
        assertEquals("0:00", BoundWasherState.formatClock(0))
        assertEquals("1:05", BoundWasherState.formatClock(65))
        assertEquals("30:00", BoundWasherState.formatClock(1800))
        assertEquals("1:02:05", BoundWasherState.formatClock(3725))
        assertEquals("0:00", BoundWasherState.formatClock(-3))
    }
}
