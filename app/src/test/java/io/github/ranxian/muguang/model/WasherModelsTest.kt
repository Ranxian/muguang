package io.github.ranxian.muguang.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 他人订单 → 模式反查（`matchWasherMode`）：
 * `lastOrder/byMac` 的 `typeId`（= `xfModel`，与模式列表同一编号空间）和
 * `money`（订单价格，厘）对 `/device/mode/list` 反查，还原「对面用什么模式洗」
 * 与权威时长；`typeName` / `modeTime` 缺失时这是唯一来源。
 */
class WasherModelsTest {

    private val modes = listOf(
        WasherMode(typeId = 17, typeName = "标准洗", money = 3000, times = 2700),
        WasherMode(typeId = 18, typeName = "快洗", money = 2000, times = 1500),
        WasherMode(typeId = 19, typeName = "单脱水", money = 1000, times = 420),
    )

    private fun order(money: Int = 0, typeId: Int = 0, modeTime: Int = 0) = OrderLastBean(
        liquidOrderNo = "L1",
        liquidOrderStatus = 0,
        money = money,
        typeId = typeId,
        modeTime = modeTime,
    )

    @Test
    fun `typeId 精确命中优先`() {
        assertEquals("快洗", matchWasherMode(modes, order(typeId = 18))?.typeName)
        assertEquals(1500, matchWasherMode(modes, order(typeId = 18))?.times)
    }

    @Test
    fun `价格唯一命中时无需 typeId`() {
        val m = matchWasherMode(modes, order(money = 1000))
        assertEquals("单脱水", m?.typeName)
        assertEquals(420, m?.times)
    }

    @Test
    fun `价格撞车用 modeTime 秒数当第二键`() {
        val modes2 = modes + WasherMode(typeId = 20, typeName = "超强洗", money = 2000, times = 2400)
        val m = matchWasherMode(modes2, order(money = 2000, modeTime = 1500))
        assertEquals("快洗", m?.typeName)
    }

    @Test
    fun `typeId 在模式表里查不到时回落价格`() {
        // 学校改过模式表，老订单的 typeId 已不存在
        val m = matchWasherMode(modes, order(money = 3000, typeId = 99))
        assertEquals("标准洗", m?.typeName)
    }

    @Test
    fun `匹配不上或没价格返回 null`() {
        assertNull(matchWasherMode(modes, order(money = 9999)))
        assertNull(matchWasherMode(modes, order(money = 0)))
        assertNull(matchWasherMode(emptyList(), order(money = 3000)))
    }

    @Test
    fun `只认主模式，投放子模块不参与反查`() {
        val withSub = modes + WasherMode(typeId = 88, typeName = "投放", money = 3000, times = 0, positionId = 1)
        assertEquals("标准洗", matchWasherMode(withSub, order(money = 3000))?.typeName)
    }
}
