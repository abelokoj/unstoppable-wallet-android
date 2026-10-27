package io.horizontalsystems.walletkit.modules.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.uiv3.components.controls.HSButton
import io.horizontalsystems.walletkit.ui.compose.components.ButtonSecondaryDefault
import io.horizontalsystems.walletkit.ui.compose.components.FormsInput
import io.horizontalsystems.walletkit.ui.compose.components.HsDivider
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.info.TextBlock
import kotlinx.serialization.Serializable

@Serializable
data object ApprovalAccessPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val model = viewModel<ApprovalAccessViewModel>(factory = ApprovalAccessViewModel.Factory())
        val state = model.state
        HSScaffold(title = stringResource(R.string.ApprovalAccess_Title), onBack = navigation::removeLastOrNull) {
            LazyColumn(modifier = Modifier.imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    TextBlock(stringResource(R.string.ApprovalAccess_Intro))
                    TextBlock(stringResource(R.string.ApprovalAccess_Privacy))
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        AccessText(model.account?.name ?: stringResource(R.string.ApprovalAccess_NoWallet))
                        var open by remember { mutableStateOf(false) }
                        TextButton(onClick = { open = true }, enabled = !state.busy && !state.accountChanged) {
                            AccessText(state.network?.name ?: stringResource(R.string.ApprovalAccess_Network))
                        }
                        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                            model.networks.forEach { network ->
                                DropdownMenuItem(onClick = { open = false; model.select(network) }) { AccessText(network.name) }
                            }
                        }
                        state.owner?.let { AccessText(it) }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HSButton(
                            modifier = Modifier.fillMaxWidth(),
                            title = stringResource(if (state.busy) R.string.ApprovalAccess_Scanning else R.string.ApprovalAccess_Scan),
                            onClick = model::scan,
                            enabled = !state.busy && !state.accountChanged && state.owner != null,
                        )
                        if (state.busy) {
                            ButtonSecondaryDefault(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.ApprovalAccess_Stop), onClick = model::cancel)
                        }
                    }
                    state.message?.let { TextBlock(stringResource(it)) }
                    if (state.owner == null) TextBlock(stringResource(R.string.ApprovalAccess_UnsupportedAccount))
                    state.scannedFrom?.let { TextBlock(stringResource(R.string.ApprovalAccess_Progress, it)) }
                    if (state.scanned) {
                        TextBlock(if (state.nextBlock == null) stringResource(R.string.ApprovalAccess_Complete, state.head ?: 0)
                            else stringResource(R.string.ApprovalAccess_Partial, state.nextBlock))
                        if (state.head != state.latestHead) TextBlock(stringResource(R.string.ApprovalAccess_NewBlocksPending))
                        if (state.failedChecks > 0) TextBlock(stringResource(R.string.ApprovalAccess_FailedChecks, state.failedChecks))
                        if (state.approvals.isEmpty()) TextBlock(stringResource(R.string.ApprovalAccess_Empty))
                    }
                }
                items(state.approvals, key = { it.target.key }) { approval ->
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HsDivider()
                        AccessText(stringResource(approval.target.kind.titleRes))
                        AccessText(stringResource(R.string.ApprovalAccess_Contract, approval.target.contract))
                        AccessText(stringResource(R.string.ApprovalAccess_Spender, approval.target.spender))
                        approval.target.tokenId?.let { AccessText(stringResource(R.string.ApprovalAccess_TokenIdValue, it)) }
                        if (approval.target.kind == ApprovalKind.Token) {
                            AccessText(if (approval.rawAllowance == ApprovalAbi.maxUint256.toString()) stringResource(R.string.ApprovalAccess_Unlimited)
                                else stringResource(R.string.ApprovalAccess_RawAllowance, approval.rawAllowance))
                        }
                        ButtonSecondaryDefault(
                            modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.ApprovalAccess_Review),
                            enabled = !state.busy && !state.accountChanged && model.account?.isWatchAccount == false,
                            onClick = {
                                val account = model.account ?: return@ButtonSecondaryDefault
                                val network = state.network ?: return@ButtonSecondaryDefault
                                navigation.slideFromRight(ApprovalRevokePage(account.id, network.type, approval.target))
                            },
                        )
                    }
                }
                item {
                    ManualApprovalCheck(networkKey = state.network?.uid, enabled = !state.busy && !state.accountChanged && state.owner != null, onCheck = model::check)
                    if (model.account?.isWatchAccount == true) TextBlock(stringResource(R.string.ApprovalAccess_WatchOnly))
                    TextBlock(stringResource(R.string.ApprovalAccess_Limits))
                }
            }
        }
    }
}

@Composable
internal fun AccessText(text: String) {
    SelectionContainer {
        Text(text, color = ComposeAppTheme.colors.leah, style = ComposeAppTheme.typography.bodyR)
    }
}

internal val ApprovalKind.titleRes: Int get() = when (this) {
    ApprovalKind.Token -> R.string.ApprovalAccess_Token
    ApprovalKind.Nft -> R.string.ApprovalAccess_Nft
    ApprovalKind.NftCollection -> R.string.ApprovalAccess_NftCollection
}

@Composable
private fun ManualApprovalCheck(networkKey: String?, enabled: Boolean, onCheck: (ApprovalTarget) -> Unit) {
    var contract by rememberSaveable(networkKey) { mutableStateOf("") }
    var spender by rememberSaveable(networkKey) { mutableStateOf("") }
    var tokenId by rememberSaveable(networkKey) { mutableStateOf("") }
    var kind by rememberSaveable(networkKey) { mutableStateOf(ApprovalKind.Token) }
    var show by rememberSaveable { mutableStateOf(false) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { show = !show }) { AccessText(stringResource(R.string.ApprovalAccess_Manual)) }
        if (show) {
            TextBlock(stringResource(R.string.ApprovalAccess_ManualInfo))
            var expanded by remember { mutableStateOf(false) }
            Row { TextButton(onClick = { expanded = true }, enabled = enabled) { AccessText(stringResource(kind.titleRes)) }
                DropdownMenu(expanded, { expanded = false }) {
                    ApprovalKind.entries.forEach { entry -> DropdownMenuItem(onClick = { kind = entry; expanded = false }) { AccessText(stringResource(entry.titleRes)) } }
                }
            }
            FormsInput(initial = contract, hint = stringResource(R.string.ApprovalAccess_ContractHint), enabled = enabled, singleLine = true, maxLength = 42, onValueChange = { contract = it.trim(); invalid = false })
            FormsInput(initial = spender, hint = stringResource(R.string.ApprovalAccess_SpenderHint), enabled = enabled, singleLine = true, maxLength = 42, onValueChange = { spender = it.trim(); invalid = false })
            if (kind == ApprovalKind.Nft) FormsInput(initial = tokenId, hint = stringResource(R.string.ApprovalAccess_TokenId), enabled = enabled, singleLine = true, maxLength = 78, onValueChange = { tokenId = it.trim(); invalid = false })
            if (invalid) TextBlock(stringResource(R.string.ApprovalAccess_Invalid))
            ButtonSecondaryDefault(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.ApprovalAccess_Check), enabled = enabled, onClick = {
                try {
                    val target = ApprovalTarget(ApprovalAbi.address(contract), ApprovalAbi.address(spender), kind,
                        tokenId.takeIf { kind == ApprovalKind.Nft })
                    ApprovalAbi.validate(target)
                    onCheck(target)
                } catch (_: IllegalArgumentException) { invalid = true }
            })
        }
    }
}
