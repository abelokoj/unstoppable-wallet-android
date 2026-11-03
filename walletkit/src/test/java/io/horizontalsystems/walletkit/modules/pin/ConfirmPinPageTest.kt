package io.horizontalsystems.walletkit.modules.pin

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ConfirmPinPageTest {
    @Test fun `each confirmation gets fresh state and existing callers remain passcode only`() {
        val first = ConfirmPinPage()
        val second = ConfirmPinPage()
        assertFalse(first.allowBiometrics)
        assertFalse(first.screenshotEnabled)
        assertNotEquals(first.contentKey(), second.contentKey())
        assertTrue(ConfirmPinPage(allowBiometrics = true).allowBiometrics)
    }

    @Test fun `restored confirmation keeps its authentication mode and result destination`() {
        val page = ConfirmPinPage(allowBiometrics = true).also { it.resultKey = "revocation-result" }
        val restored = Json.decodeFromString(ConfirmPinPage.serializer(),
            Json.encodeToString(ConfirmPinPage.serializer(), page))
        assertTrue(restored.allowBiometrics)
        assertEquals(page.contentKey(), restored.contentKey())
        assertEquals(page.resultKey, restored.resultKey)
        assertFalse(restored.screenshotEnabled)
    }
}
