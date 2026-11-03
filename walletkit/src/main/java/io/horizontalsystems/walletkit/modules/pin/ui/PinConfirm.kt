package io.horizontalsystems.walletkit.modules.pin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.modules.pin.unlock.PinConfirmViewModel
import io.horizontalsystems.walletkit.modules.pin.unlock.PinUnlockModule
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold

@Composable
fun PinConfirm(
    onSuccess: () -> Unit,
    onCancel: () -> Unit,
    allowBiometrics: Boolean = false,
) {
    val viewModel = viewModel<PinConfirmViewModel>(factory = PinConfirmViewModel.Factory(allowBiometrics))
    var showBiometricPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(allowBiometrics) {
        showBiometricPrompt = viewModel.biometricEnabled &&
            viewModel.uiState.inputState is PinUnlockModule.InputState.Enabled
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        showBiometricPrompt = false
        viewModel.unlocked()
    }
    LaunchedEffect(viewModel.uiState.unlocked) {
        if (viewModel.uiState.unlocked) {
            viewModel.unlocked()
            onSuccess()
        }
    }
    if (showBiometricPrompt) {
        BiometricPromptDialog(
            negativeButtonText = stringResource(R.string.Unlock_EnterPasscode),
            onSuccess = {
                showBiometricPrompt = false
                viewModel.onBiometricSuccess()
            },
            onError = { showBiometricPrompt = false },
        )
    }

    HSScaffold(
        title = stringResource(R.string.Unlock_Title),
        onBack = onCancel,
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .fillMaxSize()
        ) {
            PinTopBlock(
                modifier = Modifier.weight(1f),
                title = stringResource(R.string.Unlock_EnterPasscode),
                enteredCount = viewModel.uiState.enteredCount,
                showShakeAnimation = viewModel.uiState.showShakeAnimation,
                inputState = viewModel.uiState.inputState,
                onShakeAnimationFinish = { viewModel.onShakeAnimationFinish() }
            )

            PinNumpad(
                pinRandomized = viewModel.pinRandomized,
                showBiometricScanner = viewModel.biometricEnabled,
                showBiometricPrompt = { showBiometricPrompt = viewModel.biometricEnabled },
                onNumberClick = { number -> viewModel.onKeyClick(number) },
                onDeleteClick = { viewModel.onDelete() },
                inputState = viewModel.uiState.inputState,
                updatePinRandomized = { random -> viewModel.updatePinRandomized(random) }
            )
        }
    }
}