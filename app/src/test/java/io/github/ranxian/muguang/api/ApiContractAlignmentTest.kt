package io.github.ranxian.muguang.api

import io.github.ranxian.muguang.model.CloseOrderResult
import io.github.ranxian.muguang.model.DeviceInfo
import io.github.ranxian.muguang.model.DownRateResult
import io.github.ranxian.muguang.model.OrderStatus
import io.github.ranxian.muguang.model.UnpaidBill
import io.github.ranxian.muguang.model.UseCodeData
import io.github.ranxian.muguang.model.WalletData
import io.github.ranxian.muguang.model.WasherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官方 6.5.29 的 DTO 主键/别名对齐。
 *
 * 这些字段错一个，用户侧表现是「设备列表空白」「余额 0」「明明在用却恢复不了订单」，
 * 而单测是唯一能在没有具体学校样本时提前发现的办法。
 */
class ApiContractAlignmentTest {

    @Test
    fun `设备信息兼容官方 PascalCase 主键与空串金额`() {
        val json = """
            {"DevID":12,"DevName":"洗衣机-3F-02","snCode":"SN1","devMac":"AA:BB:CC:DD:EE:FF",
             "withholdMoney":"","onlineStatusId":1,"DevTypeID":6,"DevTypeName":"洗衣机",
             "Dsbtypeid":6,"DsbName":"洗烘设备","devTypeStatus":7}
        """
        val d = apiGson.fromJson(json, DeviceInfo::class.java)
        assertEquals(12, d.deviceId)
        assertEquals("洗衣机-3F-02", d.deviceName)
        assertEquals("AA:BB:CC:DD:EE:FF", d.macAddress)
        // 空串不能让整条响应解析失败
        assertEquals(0.0, d.withholdMoney, 0.0)
        assertEquals(6, d.smallTypeId)
        assertEquals(6, d.bigTypeId)
        assertEquals(7, d.devTypeStatus)
        assertTrue(d.isHaierDevice)
    }

    @Test
    fun `开阀结果兼容 timeIds 主键`() {
        val json = """
            {"timeIds":"13200000000000000001","perMoney":3000,"deviceSncode":"SN1",
             "consumeDT":"2026-11-19 10:00:00","state":1,"result":0}
        """
        val r = apiGson.fromJson(json, DownRateResult::class.java)
        assertEquals("13200000000000000001", r.orderNo)
        assertEquals(3000.0, r.preDeductMoney)
        assertEquals("SN1", r.snCode)
        assertEquals("2026-11-19 10:00:00", r.consumeDate)
    }

    @Test
    fun `使用中查询兼容 timeIds`() {
        val r = apiGson.fromJson(
            """{"timeIds":"T-1","isOwner":true,"perMoney":1000}""",
            OrderStatus::class.java
        )
        assertEquals("T-1", r.orderNo)
        assertEquals(1000, r.perMoney)
    }

    @Test
    fun `钱包兼容 accountMoney 主键`() {
        val w = apiGson.fromJson(
            """{"accountMoney":7000,"accountGivenMoney":0,"money":"7.000"}""",
            WalletData::class.java
        )
        assertEquals(7000.0, w.accountRealMoney, 0.0)
    }

    @Test
    fun `未支付账单兼容 upMoney`() {
        val b = apiGson.fromJson(
            """{"upMoney":2.5,"consumeDate":"2026-11-19 10:00:00","description":"洗浴"}""",
            UnpaidBill::class.java
        )
        assertEquals("2.5", b.consumeMoney)
        assertEquals("2026-11-19 10:00:00", b.consumeDate)
    }

    @Test
    fun `使用码兼容 randomCode`() {
        val u = apiGson.fromJson("""{"useCode":"1234","randomCode":"234"}""", UseCodeData::class.java)
        assertEquals("234", u.useCodeRandom)
    }

    @Test
    fun `洗衣机状态兼容 xfModelName 与 preDeductMoney`() {
        val s = apiGson.fromJson(
            """{"xfModelName":"标准洗","preDeductMoney":3000,"expireTimes":600}""",
            WasherStatus::class.java
        )
        assertEquals("标准洗", s.modelName)
        assertEquals(3000, s.perMoney)
        assertEquals(600, s.time)
    }

    @Test
    fun `关阀结果兼容 upMoney 与 fishTime`() {
        val c = apiGson.fromJson(
            """{"orderNo":"O1","upMoney":3.0,"fishTime":"2026-11-19 10:30:00"}""",
            CloseOrderResult::class.java
        )
        assertEquals(3.0, c.consumeMoney)
        assertEquals("2026-11-19 10:30:00", c.consumeTime)
        assertTrue(c.orderNo == "O1")
    }
}
