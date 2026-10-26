package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.model.DeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「我的热水器」的三块新逻辑：
 * - [DeviceInfo.isShowerHeater]：检测/手动绑定的判据（必须排除洗衣机、饮水机、洗手台）
 * - [BoundHeaterStore]：单个 DeviceInfo 的持久化与寝室归属校验
 * - [BoundHeaterState]：queryUsing 三态 → 界面文案（查询失败绝不能说成空闲）
 */
class BoundHeaterTest {

    private fun heater(
        name: String = "热水器-龙川北苑-3号楼南-3层-320",
        sn: String = "SN20241024000009",
        bigTypeId: Int? = 1,
    ) = DeviceInfo(
        deviceId = 2,
        deviceName = name,
        snCode = sn,
        macAddress = "AA:BB:CC:DD:EE:09",
        withholdMoney = 3.0,
        onlineStatusId = 1,
        bigTypeId = bigTypeId,
        bigTypeName = "热水器",
    )

    // ── 判据：什么设备算「淋浴热水器」 ──

    @Test
    fun `常见热水器命名都是淋浴热水器`() {
        assertTrue(heater("热水器-学生公寓-1号楼-3层-301").isShowerHeater)
        assertTrue(heater("热水表-龙川北苑-3号楼南-3层-320房").isShowerHeater)
        assertTrue(heater("热水变--1栋宿舍-11层-1001").isShowerHeater)
        // 没有类型前缀、只有房间信息的设备也算（kind = OTHER）
        assertTrue(heater("3号楼-3层-320").isShowerHeater)
    }

    @Test
    fun `洗手台不是淋浴热水器`() {
        // 洗手台的名字里也有「热水器」，但它管的是洗漱，不是洗澡
        assertFalse(heater("洗手台54-龙川北苑-3号楼南-3层-320洗手台").isShowerHeater)
        assertFalse(heater("洗手台-3层-320").isShowerHeater)
    }

    @Test
    fun `洗衣机和饮水机不参与热水器检测`() {
        assertFalse(heater("洗衣机-3F-02", bigTypeId = 6).isShowerHeater)
        assertFalse(heater("洗衣机-3F-02", bigTypeId = null).isShowerHeater)
        assertFalse(heater("直饮热水-1号楼-2层-201", bigTypeId = 5).isShowerHeater)
        assertFalse(heater("开水机-1号楼-2层-201", bigTypeId = null).isShowerHeater)
    }

    // ── 存储：解析/序列化/寝室归属 ──

    @Test
    fun `序列化后能原样解析回来`() {
        val original = heater()
        val parsed = BoundHeaterStore.parse(BoundHeaterStore.toJson(original))
        assertEquals(original.snCode, parsed?.snCode)
        assertEquals(original.deviceName, parsed?.deviceName)
        assertEquals(original.macAddress, parsed?.macAddress)
    }

    @Test
    fun `空串和垃圾 JSON 都解析成 null`() {
        assertNull(BoundHeaterStore.parse(null))
        assertNull(BoundHeaterStore.parse(""))
        assertNull(BoundHeaterStore.parse("   "))
        assertNull(BoundHeaterStore.parse("not-json"))
        assertNull(BoundHeaterStore.parse("{}"))
    }

    @Test
    fun `snCode 为空的记录当没绑`() {
        // 半条记录比没记录更危险：设备名在、但查不了状态
        assertNull(BoundHeaterStore.parse(BoundHeaterStore.toJson(heater(sn = ""))))
    }

    @Test
    fun `解绑后 toJson 是空串，parse 回 null`() {
        val json = BoundHeaterStore.toJson(null)
        assertEquals("", json)
        assertNull(BoundHeaterStore.parse(json))
    }

    @Test
    fun `寝室为空时不限制热水器归属`() {
        assertTrue(BoundHeaterStore.stillInRoom("", heater()))
    }

    @Test
    fun `同寝室保留、换寝室丢弃`() {
        assertTrue(
            BoundHeaterStore.stillInRoom(
                "热水器-龙川北苑-3号楼南-3层-320",
                heater("热水器-龙川北苑-3号楼南-3层-320")
            )
        )
        assertFalse(
            BoundHeaterStore.stillInRoom(
                "热水器-龙川北苑-3号楼南-3层-320",
                heater("热水器-龙川北苑-3号楼南-3层-321")
            )
        )
        // 楼栋都不同：不能因为房间号一样就认成同一间
        assertFalse(
            BoundHeaterStore.stillInRoom(
                "热水表-龙川北苑-3号楼南-3层-320房",
                heater("热水表-龙川北苑-3号楼北-3层-320房")
            )
        )
    }

    // ── 状态机：queryUsing 三态 → 文案 ──

    @Test
    fun `无单是空闲`() {
        val v = BoundHeaterState.fromQuery(active = false, isOwner = null)
        assertEquals("空闲", v.statusLabel)
        assertFalse(v.isActive)
        assertTrue(v.known)
    }

    @Test
    fun `他人的单不认领`() {
        val v = BoundHeaterState.fromQuery(active = true, isOwner = false)
        assertEquals("使用中 · 他人订单", v.statusLabel)
        assertTrue(v.isActive)
        assertFalse(v.isOwner)
        assertTrue(v.known)
    }

    @Test
    fun `自己的单标我的订单`() {
        val v = BoundHeaterState.fromQuery(active = true, isOwner = true)
        assertEquals("使用中 · 我的订单", v.statusLabel)
        assertTrue(v.isActive)
        assertTrue(v.isOwner)
    }

    @Test
    fun `307 没带 isOwner 时只说使用中，不认领也不甩锅`() {
        val v = BoundHeaterState.fromQuery(active = true, isOwner = null)
        assertEquals("使用中", v.statusLabel)
        assertTrue(v.isActive)
        assertFalse(v.isOwner)
        assertTrue(v.known)
    }

    @Test
    fun `读取中和查询失败都不能当成已知状态`() {
        assertEquals("读取中…", BoundHeaterState.reading.statusLabel)
        assertFalse(BoundHeaterState.reading.known)
        assertEquals("未能获取状态", BoundHeaterState.failed.statusLabel)
        assertFalse(BoundHeaterState.failed.known)
        // 失败态绝不能被聚合逻辑当成「空闲」参与「均空闲」判断
        assertFalse(BoundHeaterState.failed.isActive)
    }
}
