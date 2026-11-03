package io.horizontalsystems.walletkit.modules.pin.ui

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.modules.pin.unlock.BiometricPromptUtils.createBiometricPrompt

@Composable
fun BiometricPromptDialog(
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onError: (Int) -> Unit,
    negativeButtonText: String = stringResource(R.string.Button_Cancel),
) {
    val title = stringResource(R.string.BiometricAuth_DialogTitle)
    val promptInfo = remember(title, negativeButtonText) {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(negativeButtonText)
            .setConfirmationRequired(false)
            .build()
    }
    val context = LocalContext.current as? FragmentActivity
    val success by rememberUpdatedState(onSuccess)
    val error by rememberUpdatedState(onError)
    DisposableEffect(context, promptInfo) {
        var active = true
        val prompt = context?.let {
            createBiometricPrompt(it,
                onSuccess = { result -> if (active) success(result) },
                onError = { code -> if (active) error(code) },
            )
        }
        if (prompt == null) {
            error(BiometricPrompt.ERROR_HW_UNAVAILABLE)
        } else {
            try {
                prompt.authenticate(promptInfo)
            } catch (_: IllegalStateException) {
                error(BiometricPrompt.ERROR_CANCELED)
            }
        }
        onDispose {
            // A canceled or departed confirmation must not accept a late callback.
            active = false
            prompt?.cancelAuthentication()
        }
    }
}
