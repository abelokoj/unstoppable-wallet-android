package io.horizontalsystems.walletkit.modules.multiswap

import org.junit.Assert.assertEquals
import org.junit.Test

class SwapViewModelPercentOptionsTest {

    @Test
    fun `fixed percentage options always include full balance`() {
        assertEquals(listOf(25, 50, 75, 100), FixedSwapPercentOptions)
    }
}
