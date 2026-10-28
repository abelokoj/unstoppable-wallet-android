package io.horizontalsystems.walletkit.modules.multiswap.providers

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenSwapProviderRatingsTest {
    @Test
    fun `upstream metadata cannot replace configured Open Swap ratings`() {
        val serverScores = SwapRouteScores.from(listOf(
            UnstoppableAPI.Response.Provider(provider = "JUPITER", amlPolicy = "fair"),
            UnstoppableAPI.Response.Provider(provider = "RELAY", amlPolicy = "excellent"),
        ))

        assertEquals(RiskLevel.FAIR, serverScores.riskLevel("u_JUPITER"))
        assertEquals(RiskLevel.EXCELLENT, UProvider.Jupiter.riskLevel)
        assertEquals(RiskLevel.EXCELLENT, UProvider.Across.riskLevel)
        assertEquals(RiskLevel.FAIR, UProvider.Relay.riskLevel)
        assertEquals(RiskLevel.GOOD, UProvider.Lizex.riskLevel)
        assertEquals(RiskLevel.FAIR, UProvider.NearConfidential.riskLevel)
        assertEquals(RiskLevel.FAIR, UProvider.XSwap.riskLevel)
    }
}
