package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.OrderLastBean
import io.github.ranxian.muguang.model.WasherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 双机绑定的三块新逻辑：
 * - [BoundWasherStore]：1.0 单台 JSON 的迁移与列表序列化
 * - [BoundOrderTracker]：每台设备一份的订单起点观测（重点验「两台互不串台」）
 * - [BoundWasherState.cardSubline]：首页卡片每行小字的分档
 */
class BoundWasherMultiTest {

    // ── BoundWasherStore ──

    private fun washer(sn: String, name: String = "洗衣机-3F-02") = DeviceInfo(
        deviceId = 1,
        deviceName = name,
        snCode = sn,
        macAddress = "AA:BB:CC:DD:EE:01",
        withholdMoney = 3.0,
        onlineStatusId = 1,
        bigTypeId = 6,
        bigTypeName = "洗衣机",
    )

    @Test
    fun `legacy single binding migrates into a one-item list`() {
        val legacy = com.google.gson.Gson().toJson(washer("SN-A"))
        val parsed = BoundWasherStore.parse(arrayJson = "", legacySingleJson = legacy)
        assertEquals(listOf("SN-A"), parsed.map { it.snCode })
    }

    @Test
    fun `legacy blank sn or non-washer is dropped instead of resurrected`() {
        val gson = com.google.gson.Gson()
        // 非洗衣机（bigTypeId 缺失、名字不带洗衣机）不应进入绑定列表
        val notWasher = gson.toJson(washer("SN-A").copy(bigTypeId = null, bigTypeName = null, deviceName = "热水器-3F"))
        assertTrue(BoundWasherStore.parse("", notWasher).isEmpty())
        assertTrue(BoundWasherStore.parse("", "").isEmpty())
        assertTrue(BoundWasherStore.parse("not json", "").isEmpty())
    }

    @Test
    fun `array format wins and legacy is ignored when present`() {
        val gson = com.google.gson.Gson()
        val array = gson.toJson(listOf(washer("SN-A"), washer("SN-B", "洗衣机-3F-05")))
        val legacy = gson.toJson(washer("SN-OLD"))
        val parsed = BoundWasherStore.parse(array, legacy)
        assertEquals(listOf("SN-A", "SN-B"), parsed.map { it.snCode })
    }

    @Test
    fun `toJson keeps binding order for round trip`() {
        val list = listOf(washer("SN-B", "洗衣机-3F-05"), washer("SN-A"))
        val back = BoundWasherStore.parse(BoundWasherStore.toJson(list), "")
        assertEquals(listOf("SN-B", "SN-A"), back.map { it.snCode })
    }

    // ── BoundOrderTracker：两台设备各一份，观测状态不共用 ──

    private fun runningOrder(
        key: String,
        placedAtMs: Long,
        modeTimeSec: Int = 1800
    ): OrderLastBean {
        val placed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(placedAtMs))
        return OrderLastBean(
            liquidOrderNo = key,
            consumeDate = placed,
            modeTime = modeTimeSec,
            liquidOrderStatus = 0
        )
    }

    @Test
    fun `order switch observed as midpoint between polls`() {
        val t = BoundOrderTracker()
        val t0 = 1_000_000L
        // 第一轮：旧单还剩 5 秒就到时长（没到脏单宽限，仍算在跑）
        t.observe(runningOrder("ORD-1", t0 - 1_795_000), t0)
        // 第二轮：45 秒后换成新单——旧单这 45 秒里自然结束，
        // 切换只能发生在两次轮询之间 → 中点，且算「亲眼观测」
        val (start, observed) = t.observe(runningOrder("ORD-2", t0 + 40_000), t0 + 45_000)
        assertTrue(observed)
        assertTrue(start in (t0 + 1) until (t0 + 45_000))
    }

    @Test
    fun `queued order start predicted at previous order end`() {
        val t = BoundOrderTracker()
        val t0 = 1_000_000L
        // 上一单：t0 开始，跑 30 分钟
        t.observe(runningOrder("ORD-1", t0, modeTimeSec = 1800), t0)
        // 下一轮看到新单——上一单还剩 29 分钟没洗完，新单在排队
        val (start, observed) = t.observe(runningOrder("ORD-2", t0 + 60_000, modeTimeSec = 1200), t0 + 60_000)
        assertFalse(observed)
        assertEquals(t0 + 1800_000L, start)
    }

    @Test
    fun `two trackers never share observation state`() {
        val trackerA = BoundOrderTracker()
        val trackerB = BoundOrderTracker()
        val t0 = 1_000_000L

        // A 亲眼看到 ORD-A2 的切换（A1 只剩几秒，45 秒间隙里自然结束）；
        // B 只看到自己的 ORD-B1 一直在跑
        trackerA.observe(runningOrder("ORD-A1", t0 - 1_795_000), t0)
        val (startA, observedA) = trackerA.observe(runningOrder("ORD-A2", t0 + 30_000), t0 + 45_000)
        val (startB, observedB) = trackerB.observe(runningOrder("ORD-B1", t0 - 30_000), t0 + 45_000)

        assertTrue(observedA)
        // B 第一次看到自己的单，没有切换可看 → 交给 consumeDate 估算（不谎称观测）
        assertFalse(observedB)
        assertEquals(0L, startB)
        assertTrue(startA > 0L)
    }

    @Test
    fun `reset forgets previous order`() {
        val t = BoundOrderTracker()
        val t0 = 1_000_000L
        t.observe(runningOrder("ORD-1", t0 - 60_000), t0)
        t.reset()
        // reset 后同一单再出现：没有历史可续，只能按新观测走
        val (start, observed) = t.observe(runningOrder("ORD-1", t0 - 120_000), t0 + 45_000)
        assertFalse(observed)
        assertEquals(0L, start)
    }

    @Test
    fun `failed poll keeps last known start`() {
        val t = BoundOrderTracker()
        val t0 = 1_000_000L
        t.observe(runningOrder("ORD-1", t0 - 60_000), t0)
        val (midStart, _) = t.observe(runningOrder("ORD-2", t0 + 30_000), t0 + 45_000)
        // 下一轮查询失败（null）：不清观测，恢复后还能续上 ORD-2
        val (kept, keptObserved) = t.observe(null, t0 + 90_000)
        assertEquals(midStart, kept)
        assertTrue(keptObserved)
    }

    // ── cardSubline：首页「我的洗衣机」卡片每行的小字 ──

    private fun view(
        known: Boolean = true,
        isActive: Boolean = true,
        isOwner: Boolean = true,
        remainingSec: Int = 0,
        estimated: Boolean = false,
        placedAtMs: Long = 0L,
        stage: String = "脱水"
    ) = BoundWasherView(
        statusLabel = if (!known) "状态未知" else if (isActive) (if (isOwner) "使用中" else "他人使用中") else "空闲",
        stageText = stage,
        remainingSec = remainingSec,
        isActive = isActive,
        isOwner = isOwner,
        known = known,
        remainingIsEstimate = estimated,
        orderPlacedAtMs = placedAtMs
    )

    @Test
    fun `failed fetch says unknown not idle`() {
        assertEquals("未能获取设备状态", BoundWasherState.cardSubline(view(known = false), 0L, 0L))
    }

    @Test
    fun `idle machine shows idle`() {
        assertEquals("空闲", BoundWasherState.cardSubline(view(isActive = false), 0L, 0L))
    }

    @Test
    fun `exact remaining ticks down between refreshes`() {
        val v = view(remainingSec = 605)
        assertEquals("剩余 10:05", BoundWasherState.cardSubline(v, updatedAtMs = 1_000L, nowMs = 1_000L))
        // 10 秒过去：本地倒数续走，不用等服务端
        assertEquals("剩余 09:55", BoundWasherState.cardSubline(v, updatedAtMs = 1_000L, nowMs = 11_000L))
    }

    @Test
    fun `others order shows placed-at instead of estimated countdown`() {
        // parse 与 format 都走默认时区，这里同样用默认时区造数，机器时区无关
        val placed = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            .parse("2026-10-25 14:32:00")!!.time
        val v = view(isOwner = false, remainingSec = 800, estimated = true, placedAtMs = placed)
        val expected = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(placed)) + " 下单"
        assertEquals(expected, BoundWasherState.cardSubline(v, 1_000L, placed + 60_000))
    }

    @Test
    fun `own estimated remaining is shown with 约`() {
        val v = view(remainingSec = 725, estimated = true)
        assertEquals("约剩 12:05", BoundWasherState.cardSubline(v, 1_000L, 1_000L))
    }

    @Test
    fun `no remaining falls back to stage text`() {
        assertEquals("脱水", BoundWasherState.cardSubline(view(remainingSec = 0), 0L, 0L))
        assertEquals(
            "运行中",
            BoundWasherState.cardSubline(view(remainingSec = 0, stage = ""), 0L, 0L)
        )
    }

    @Test
    fun `others order with exact server time still shows countdown`() {
        // 有的校区 status 对他人订单也回精确剩余秒：那就不退回「下单时间」
        val v = view(isOwner = false, remainingSec = 300, estimated = false, placedAtMs = 1L)
        assertEquals("剩余 05:00", BoundWasherState.cardSubline(v, 1_000L, 1_000L))
    }

    // washer status 冒烟：保证测试里用的 WasherStatus 语义没漂移
    @Test
    fun `washer status fixture still active`() {
        val status = WasherStatus(isOwner = true, modeTime = 1800, time = 605)
        assertTrue(status.looksActive)
    }
}
