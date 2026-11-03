package io.horizontalsystems.walletkit.modules.pin.unlock

import io.horizontalsystems.walletkit.IPinComponent
import io.horizontalsystems.walletkit.core.ILocalStorage
import io.horizontalsystems.walletkit.modules.pin.core.ILockoutManager
import io.horizontalsystems.walletkit.modules.pin.core.LockoutState
import io.horizontalsystems.walletkit.modules.pin.core.OneTimeTimer
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.Date

class PinConfirmViewModelTest {
    @Test fun `biometric success authorizes only the requested action without unlocking or switching users`() {
        val fixture = Fixture()
        val model = fixture.model()
        assertFalse(model.uiState.unlocked)
        model.onBiometricSuccess()
        assertTrue(model.uiState.unlocked)
        assertEquals(1, fixture.resets)
        assertEquals(0, fixture.unlockCalls)
        model.unlocked()
        assertFalse(model.uiState.unlocked)
    }

    @Test fun `biometrics require explicit opt in hardware support enabled setting passcode and unlocked app`() {
        val cases: List<(Fixture) -> Unit> = listOf(
            { it.allowed = false }, { it.supported = false }, { it.biometricsEnabled = false },
            { it.pinSet = false }, { it.locked = true },
        )
        cases.forEach { configure ->
            val fixture = Fixture().also(configure)
            val model = fixture.model()
            assertFalse(model.biometricEnabled)
            model.onBiometricSuccess()
            assertFalse(model.uiState.unlocked)
            assertEquals(0, fixture.resets)
        }
    }

    @Test fun `a late biometric result cannot bypass passcode lockout or changed security settings`() {
        listOf<(Fixture) -> Unit>(
            { it.lockout = LockoutState.Locked(Date(Long.MAX_VALUE)) },
            { it.biometricsEnabled = false }, { it.pinSet = false }, { it.locked = true },
        ).forEach { invalidate ->
            val fixture = Fixture()
            val model = fixture.model()
            assertTrue(model.biometricEnabled)
            invalidate(fixture)
            model.onBiometricSuccess()
            assertFalse(model.uiState.unlocked)
            assertEquals(0, fixture.resets)
        }
    }

    @Test fun `passcode fallback still validates the current user when biometrics are unavailable`() {
        val fixture = Fixture().also { it.supported = false }
        val model = fixture.model()
        "123456".forEach { model.onKeyClick(it.digitToInt()) }
        assertTrue(model.uiState.unlocked)
        assertEquals(1, fixture.validations)
        assertEquals(0, fixture.unlockCalls)
    }

    @Test fun `passcode input cannot authorize after lockout relock or removal of the passcode`() {
        listOf<(Fixture) -> Unit>(
            { it.lockout = LockoutState.Locked(Date(Long.MAX_VALUE)) },
            { it.locked = true }, { it.pinSet = false },
        ).forEach { invalidate ->
            val fixture = Fixture()
            val model = fixture.model()
            invalidate(fixture)
            "123456".forEach { model.onKeyClick(it.digitToInt()) }
            assertFalse(model.uiState.unlocked)
            assertEquals(0, fixture.validations)
        }
    }

    @Test fun `biometrics remain disabled for existing passcode-only callers`() {
        val fixture = Fixture()
        val model = PinConfirmViewModel(fixture.pin, fixture.manager, OneTimeTimer(), fixture.storage)
        assertFalse(model.biometricEnabled)
        model.onBiometricSuccess()
        assertFalse(model.uiState.unlocked)
    }

    private class Fixture {
        var allowed = true
        var supported = true
        var biometricsEnabled = true
        var pinSet = true
        var locked = false
        var lockout: LockoutState = LockoutState.Unlocked(null)
        var resets = 0
        var validations = 0
        var unlockCalls = 0
        val manager = object : ILockoutManager {
            override val currentState get() = lockout
            override fun dropFailedAttempts() { resets++ }
            override fun didFailUnlock() = Unit
        }
        val pin = Proxy.newProxyInstance(IPinComponent::class.java.classLoader, arrayOf(IPinComponent::class.java)) { _, method, args ->
            when (method.name) {
                "isPinSet" -> pinSet
                "isLocked" -> locked
                "isBiometricAuthEnabled" -> biometricsEnabled
                "validateCurrentLevel" -> { validations++; args!![0] == "123456" }
                "onBiometricUnlock", "unlock" -> { unlockCalls++; error("Action confirmation must not unlock or switch users") }
                else -> error("Unexpected PIN call: ${method.name}")
            }
        } as IPinComponent
        val storage = Proxy.newProxyInstance(ILocalStorage::class.java.classLoader, arrayOf(ILocalStorage::class.java)) { _, method, _ ->
            check(method.name == "getPinRandomized")
            false
        } as ILocalStorage
        fun model() = PinConfirmViewModel(pin, manager, OneTimeTimer(), storage,
            allowBiometrics = allowed, biometricAuthSupported = supported)
    }
}
