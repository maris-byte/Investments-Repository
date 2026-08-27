package com.marisbyte.invest

import com.marisbyte.invest.ui.components.parseDecimalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {

    @Test
    fun `accepts german and english notation`() {
        assertEquals(1234.56, parseDecimalInput("1.234,56")!!, 1e-9)
        assertEquals(1234.56, parseDecimalInput("1234.56")!!, 1e-9)
        assertEquals(12.5, parseDecimalInput("12,5")!!, 1e-9)
        assertEquals(0.00042, parseDecimalInput("0,00042")!!, 1e-12)
    }

    @Test
    fun `treats a lone thousands separator correctly`() {
        assertEquals(1234.0, parseDecimalInput("1.234")!!, 1e-9)
        assertEquals(1.23, parseDecimalInput("1.23")!!, 1e-9)
    }

    @Test
    fun `rejects invalid input`() {
        assertNull(parseDecimalInput(""))
        assertNull(parseDecimalInput("   "))
        assertNull(parseDecimalInput("abc"))
    }
}
