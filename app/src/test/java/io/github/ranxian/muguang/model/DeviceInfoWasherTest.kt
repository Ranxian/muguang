package io.github.ranxian.muguang.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceInfoWasherTest {

    private fun dev(name: String, bigTypeId: Int? = null) = DeviceInfo(
        deviceId = 1,
        deviceName = name,
        snCode = "SN1",
        macAddress = "AA:BB:CC:DD:EE:FF",
        withholdMoney = 0.0,
        onlineStatusId = 1,
        bigTypeId = bigTypeId
    )

    @Test
    fun `bigTypeId 6 marks laundry even without name keyword`() {
        assertTrue(dev("3号楼-320", bigTypeId = 6).isLaundry)
    }

    @Test
    fun `name keyword marks laundry`() {
        assertTrue(dev("洗烘一体机-1").isLaundry)
        assertTrue(dev("洗衣机-320房").isLaundry)
    }

    @Test
    fun `water heater and drinking water are not laundry`() {
        assertFalse(dev("热水器-320房").isLaundry)
        val drink = dev("直饮热水-1", bigTypeId = 5)
        assertFalse(drink.isLaundry)
        assertTrue(drink.isDrinkingWater)
    }

    @Test
    fun `washer display name strips type prefix`() {
        assertEquals("320房", dev("洗衣机-320房", bigTypeId = 6).displayName)
    }
}
