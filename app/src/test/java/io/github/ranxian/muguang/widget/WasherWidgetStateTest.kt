package io.github.ranxian.muguang.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WasherWidgetStateTest {

    private val activeJson = """
        {
          "device": {"deviceName": "洗衣机-320房", "snCode": "SN123"},
          "mode": {"typeName": "标准洗", "times": 1800, "money": 3000},
          "orderId": "T20261024001",
          "startedAtMs": 1000000,
          "preDeductMilliYuan": 3000
        }
    """.trimIndent()

    @Test
    fun `active session exposes remaining time and pre-deduct`() {
        val snap = WasherWidgetState.from(activeJson, nowMs = 1_000_000L + 65_000L)
        requireNotNull(snap)
        assertEquals("320房", snap.deviceName)
        assertEquals("标准洗", snap.modeName)
        assertEquals(1800 - 65, snap.remainingSec)
        assertEquals(3.0, snap.preDeductYuan, 0.0001)
        assertEquals(1_000_000L, snap.startedAtMs)
    }

    @Test
    fun `expired session is ignored`() {
        assertNull(WasherWidgetState.from(activeJson, nowMs = 1_000_000L + 1_800_000L))
        assertNull(WasherWidgetState.from(activeJson, nowMs = 1_000_000L + 9_999_999L))
    }

    @Test
    fun `malformed or partial json never throws`() {
        assertNull(WasherWidgetState.from(null, 1L))
        assertNull(WasherWidgetState.from("", 1L))
        assertNull(WasherWidgetState.from("{not json", 1L))
        assertNull(WasherWidgetState.from("""{"device":{"deviceName":"洗衣机"}}""", 1L))
        assertNull(WasherWidgetState.from("""{"device":{},"mode":{"times":600}}""", 1L))
    }

    @Test
    fun `session without mode times is ignored`() {
        val noTimes = """
            {"device":{"deviceName":"洗衣机-1"},"mode":{"typeName":"快洗"},"startedAtMs":123}
        """.trimIndent()
        assertNull(WasherWidgetState.from(noTimes, 1_000L))
    }

    @Test
    fun `fallback mode name when server omits it`() {
        val noName = """
            {"device":{"deviceName":"洗衣机-1"},"mode":{"times":600},"startedAtMs":1000}
        """.trimIndent()
        val snap = WasherWidgetState.from(noName, 2_000L)
        requireNotNull(snap)
        assertEquals("洗衣机", snap.modeName)
        assertEquals(599, snap.remainingSec)
    }

    @Test
    fun `formatRemaining`() {
        assertEquals("42秒", WasherWidgetState.formatRemaining(42))
        assertEquals("1分05秒", WasherWidgetState.formatRemaining(65))
        assertEquals("30分00秒", WasherWidgetState.formatRemaining(1800))
        assertEquals("0秒", WasherWidgetState.formatRemaining(-5))
    }
}
