package io.horizontalsystems.walletkit.modules.multiswap.providers

import io.horizontalsystems.walletkit.R
import org.junit.Assert.assertEquals
import org.junit.Test

class UProviderIconTest {

    @Test
    fun `Jupiter exposes its provider icon`() {
        assertEquals(R.drawable.swap_provider_jupiter, UProvider.Jupiter.icon)
    }
}
