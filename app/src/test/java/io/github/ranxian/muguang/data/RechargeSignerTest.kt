package io.github.ranxian.muguang.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RechargeSignerTest {

    /**
     * 官方下单时参与签名的就这四个字段。
     *
     * 期望值是按「key 升序 + key/value 直拼 + `sign-kailu=` + MD5 小写」离线算出来的；
     * 谁改动拼接顺序、大小写、或者把 sk 换成 native 里另一段候选字符串，
     * 这个用例都会当场红掉。
     */
    private val official = mapOf(
        "loginCode" to "LC-TEST",
        "payWay" to "2",
        "rechargeMoney" to "10",
        "telPhone" to "13479706620"
    )

    @Test
    fun `signature matches the official algorithm`() {
        assertEquals("fce3e48862d3f79766f1de729e5dae1b", RechargeSigner.sign(official))
    }

    @Test
    fun `signature does not use the native library's other candidate key`() {
        assertFalse(RechargeSigner.sign(official) == "81d648bd15c8f626f4e8baf9259e9b9c")
    }

    @Test
    fun `signature ignores map insertion order`() {
        val reversed = linkedMapOf<String, String?>()
        official.entries.reversed().forEach { reversed[it.key] = it.value }
        assertEquals(RechargeSigner.sign(official), RechargeSigner.sign(reversed))
    }

    /**
     * 官方 `ek.e0.e` 对 null 的处理是「拼 key、跳过 value」——**不是**删掉这个 key。
     * 所以 null 等价于空串，而不是等价于「没这个字段」。
     */
    @Test
    fun `null value is signed as empty string, key still present`() {
        val withNull = official + ("payWay" to null)
        assertEquals(RechargeSigner.sign(official + ("payWay" to "")), RechargeSigner.sign(withNull))
    }

    @Test
    fun `md5 is lowercase 32 hex chars`() {
        val hash = RechargeSigner.md5Hex("abc")
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hash)
    }
}

class RechargeAmountsTest {

    @Test
    fun `accepts the shapes the server actually sends`() {
        assertEquals("10", RechargeAmounts.normalize("10.0"))
        assertEquals("10", RechargeAmounts.normalize("10"))
        assertEquals("10.5", RechargeAmounts.normalize("010.50"))
        assertEquals("0.01", RechargeAmounts.normalize("0.01"))
        assertEquals("10000", RechargeAmounts.normalize("10000"))
        assertEquals("10", RechargeAmounts.normalize(" 10 "))
    }

    /**
     * 这些值发出去时服务端的反应是「无效金额」甚至 `-2 网络出错了`——
     * 用户看到的是一句毫无信息量的报错，所以必须在本地拦掉。
     */
    @Test
    fun `rejects values the server cannot validate`() {
        assertNull(RechargeAmounts.normalize(null))
        assertNull(RechargeAmounts.normalize(""))
        assertNull(RechargeAmounts.normalize("   "))
        assertNull(RechargeAmounts.normalize("0"))
        assertNull(RechargeAmounts.normalize("0.00"))
        assertNull(RechargeAmounts.normalize("abc"))
        assertNull(RechargeAmounts.normalize("-5"))
        assertNull(RechargeAmounts.normalize("+5"))
        assertNull(RechargeAmounts.normalize("1e3"))
        assertNull(RechargeAmounts.normalize("0x10"))
        assertNull(RechargeAmounts.normalize("10.999"))
        assertNull(RechargeAmounts.normalize("10000.01"))
        assertNull(RechargeAmounts.normalize("1 0"))
    }

    @Test
    fun `toBigDecimal follows normalize`() {
        assertNotNull(RechargeAmounts.toBigDecimal("10.0"))
        assertNull(RechargeAmounts.toBigDecimal("0"))
        assertEquals(0, RechargeAmounts.toBigDecimal("10.50")!!.compareTo("10.5".toBigDecimal()))
    }
}

class RechargeChannelTest {

    @Test
    fun `keeps only channels this app can actually finish`() {
        assertEquals(
            listOf(RechargeChannel.ALIPAY, RechargeChannel.JUHE),
            RechargeChannel.fromPayWays(listOf("2", "26"))
        )
        assertEquals(emptyList<RechargeChannel>(), RechargeChannel.fromPayWays(listOf("23", "33", "24")))
    }

    @Test
    fun `empty or missing list yields no channel`() {
        assertEquals(emptyList<RechargeChannel>(), RechargeChannel.fromPayWays(null))
        assertEquals(emptyList<RechargeChannel>(), RechargeChannel.fromPayWays(emptyList()))
    }

    @Test
    fun `result order is stable regardless of server order`() {
        assertEquals(
            RechargeChannel.fromPayWays(listOf("2", "26", "36")),
            RechargeChannel.fromPayWays(listOf("36", "26", "2"))
        )
    }

    @Test
    fun `whitespace in server values is tolerated`() {
        assertEquals(listOf(RechargeChannel.ALIPAY), RechargeChannel.fromPayWays(listOf(" 2 ")))
    }

    @Test
    fun `payWay lookup round trips`() {
        RechargeChannel.entries.forEach {
            assertEquals(it, RechargeChannel.ofPayWay(it.payWay))
        }
        assertNull(RechargeChannel.ofPayWay("999"))
    }
}

class AlipayResultTest {

    @Test
    fun `known result statuses are mapped`() {
        assertEquals(AlipayResult.SUCCESS, AlipayResult.of("9000"))
        assertEquals(AlipayResult.PROCESSING, AlipayResult.of("8000"))
        assertEquals(AlipayResult.CANCELLED, AlipayResult.of("6001"))
        assertEquals(AlipayResult.NETWORK_ERROR, AlipayResult.of("6002"))
        assertEquals(AlipayResult.FAILED, AlipayResult.of("4000"))
        assertEquals(AlipayResult.REPEAT, AlipayResult.of("5000"))
    }

    /**
     * 未知状态**不能**当成失败：SDK 返回了没见过的码时，钱可能已经扣了，
     * 说「失败」会诱导用户再付一次。
     */
    @Test
    fun `unknown or missing status falls back to unknown`() {
        assertEquals(AlipayResult.UNKNOWN, AlipayResult.of(null))
        assertEquals(AlipayResult.UNKNOWN, AlipayResult.of(""))
        assertEquals(AlipayResult.UNKNOWN, AlipayResult.of("9999"))
    }

    @Test
    fun `surrounding whitespace is tolerated`() {
        assertEquals(AlipayResult.SUCCESS, AlipayResult.of(" 9000 "))
    }
}
