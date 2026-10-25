package io.github.ranxian.muguang.data

import io.github.ranxian.muguang.model.BillDTO
import io.github.ranxian.muguang.model.BillItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillHistoryCacheTest {
    @Test
    fun `cache key is isolated by account`() {
        assertEquals("billCache_user-a", BillHistoryCache.keyFor("user-a"))
        assertEquals("billCache_user-b", BillHistoryCache.keyFor("user-b"))
    }

    @Test
    fun `round trips bill history`() {
        val bills = listOf(
            BillItem(BillDTO("1", "2026-09-22 10:00:00", "1.20", "热水器", "order-1"))
        )

        val decoded = BillHistoryCache.decode(BillHistoryCache.encode(bills))

        assertEquals(bills, decoded)
        assertTrue(BillHistoryCache.decode("not-json").isEmpty())
    }
}
