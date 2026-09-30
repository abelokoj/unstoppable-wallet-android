package io.horizontalsystems.walletkit.modules.settings.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppFontTest {

    @Test
    fun `stored font values round trip through stable raw keys`() {
        AppFont.entries.forEach { font ->
            assertEquals(font, AppFont.fromRaw(font.raw))
        }
    }

    @Test
    fun `unknown stored font value falls back through null`() {
        assertNull(AppFont.fromRaw("removed-font"))
    }

    @Test
    fun `font options retain existing keys and include the two new families`() {
        assertEquals(AppFont.Default, AppFont.fromRaw("default"))
        assertEquals(AppFont.System, AppFont.fromRaw("system"))
        assertEquals(AppFont.Pretendard, AppFont.fromRaw("pretendard"))
        assertEquals(AppFont.SourceSans3, AppFont.fromRaw("source_sans_3"))
        assertEquals(AppFont.PublicSans, AppFont.fromRaw("public_sans"))
        assertEquals(5, AppFont.entries.size)
    }

    @Test
    fun `stored font-size values round trip through stable raw keys`() {
        (AppFontSize.MIN_PERCENT..AppFontSize.MAX_PERCENT).forEach { percentage ->
            val size = AppFontSize.fromPercentage(percentage)!!
            assertEquals(size, AppFontSize.fromRaw(size.raw))
        }
    }

    @Test
    fun `unknown stored font-size value falls back through null`() {
        assertNull(AppFontSize.fromRaw("removed-size"))
    }

    @Test
    fun `legacy font-size values preserve their original scale`() {
        assertEquals(AppFontSize.fromPercentage(95), AppFontSize.fromRaw("small"))
        assertEquals(AppFontSize.Default, AppFontSize.fromRaw("default"))
        assertEquals(AppFontSize.fromPercentage(125), AppFontSize.fromRaw("large"))
    }

    @Test
    fun `font-size changes one percent and stops at safe bounds`() {
        val minimum = AppFontSize.fromPercentage(AppFontSize.MIN_PERCENT)!!
        val maximum = AppFontSize.fromPercentage(AppFontSize.MAX_PERCENT)!!

        assertEquals(minimum, minimum.previous())
        assertEquals(AppFontSize.fromPercentage(86), minimum.next())
        assertEquals(AppFontSize.fromPercentage(139), maximum.previous())
        assertEquals(maximum, maximum.next())
    }

    @Test
    fun `font-size rejects manually entered values outside safe bounds`() {
        assertNull(AppFontSize.fromPercentage(AppFontSize.MIN_PERCENT - 1))
        assertNull(AppFontSize.fromPercentage(AppFontSize.MAX_PERCENT + 1))
    }
}
