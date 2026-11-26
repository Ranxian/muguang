package io.github.ranxian.muguang.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * 金额格式必须与系统语言无关：`"%.3f".format()` 用默认 Locale，
 * 德语/法语下会输出 `0,025`，用户会读错金额。
 */
class MoneyFormatTest {

    private lateinit var original: Locale

    @Before
    fun setUp() {
        original = Locale.getDefault()
        Locale.setDefault(Locale.GERMANY)
    }

    @After
    fun tearDown() {
        Locale.setDefault(original)
    }

    @Test
    fun `德语环境仍用点号小数点`() {
        // 设计口径：保留 3 位、末位为 0 收敛到 2 位
        assertEquals("5.00", MoneyFormat.format(5.0))
        assertEquals("0.025", MoneyFormat.format(0.025))
        assertEquals("10.15", MoneyFormat.format(10.150))
        assertEquals("¥0.025", MoneyFormat.withSymbol(0.025))
    }
}
