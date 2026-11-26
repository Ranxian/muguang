package io.github.ranxian.muguang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 蓝牙「同一时刻只能有一台表在用水」的守卫。
 *
 * 背景：App 只有一条 BLE 连接，`connect()` 会先 `disconnect()`。A 表用水期间
 * 开 B 表会掐掉 A 的连接并把会话覆盖，A 的阀就再也关不上了。
 */
class BleSessionGuardTest {

    private val sessionJson =
        """{"snCode":"SN-A","mac":"C0:15:83:04:D3:00","protocolType":"1","randomNumber":"1","consumeDate":"2026-11-19 10:00:00","orderNo":"132"}"""

    @Test
    fun `另一台设备有未结束订单时拦截`() {
        val s = BleSessionGuard.otherActiveSession(
            sessionJson = sessionJson,
            targetSn = "SN-B",
            isRunning = { it == "SN-A" }
        )
        assertNotNull(s)
        assertEquals("SN-A", s!!.snCode)
    }

    @Test
    fun `同一台设备不算冲突（应走恢复用水）`() {
        assertNull(
            BleSessionGuard.otherActiveSession(sessionJson, "SN-A", isRunning = { true })
        )
    }

    @Test
    fun `会话存在但订单已结束则不拦`() {
        assertNull(
            BleSessionGuard.otherActiveSession(
                sessionJson, "SN-B",
                isRunning = { false },
                startedAt = { 0L }
            )
        )
    }

    @Test
    fun `订单仓库没记录但仍有开阀时间戳也算活跃`() {
        assertNotNull(
            BleSessionGuard.otherActiveSession(
                sessionJson, "SN-B",
                isRunning = { false },
                startedAt = { 1_700_000_000_000L }
            )
        )
    }

    @Test
    fun `空串与坏 JSON 不崩也不拦`() {
        assertNull(BleSessionGuard.otherActiveSession("", "SN-B", isRunning = { true }))
        assertNull(BleSessionGuard.otherActiveSession("{not json", "SN-B", isRunning = { true }))
    }
}
