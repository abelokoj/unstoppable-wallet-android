package io.horizontalsystems.walletkit.modules.approvals

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.horizontalsystems.marketkit.models.Blockchain
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.TranslatableString
import io.horizontalsystems.walletkit.ui.compose.components.FormsInput
import io.horizontalsystems.walletkit.ui.compose.components.HsCheckbox
import io.horizontalsystems.walletkit.ui.compose.components.HsDivider
import io.horizontalsystems.walletkit.ui.compose.components.HsImageCircle
import io.horizontalsystems.walletkit.ui.compose.components.MenuItem
import io.horizontalsystems.walletkit.modules.evmnetwork.EvmNetworkPage
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.bottombars.ButtonsGroupVertical
import io.horizontalsystems.walletkit.uiv3.components.cell.CellLeftCoinIcon
import io.horizontalsystems.walletkit.uiv3.components.controls.*
import io.horizontalsystems.walletkit.uiv3.components.info.TextBlock
import kotlinx.serialization.Serializable

@Serializable
data object ApprovalAccessPage : HSPage() {
    @Composable
    override fun GetContent(navigation: HSNavigation) {
        val model = viewModel<ApprovalAccessViewModel>(factory = ApprovalAccessViewModel.Factory())
        val state = model.state
        val canInteract = !state.busy && !state.accountChanged
        var manual by rememberSaveable(state.network?.uid) { mutableStateOf(false) }
        androidx.activity.compose.BackHandler(state.selectedContracts.isNotEmpty()) { model.clearSelection() }
        MaterialTheme(colors = MaterialTheme.colors.copy(
            surface = ComposeAppTheme.colors.lawrence, onSurface = ComposeAppTheme.colors.leah,
        )) {
        HSScaffold(
            title = stringResource(R.string.ApprovalAccess_Title),
            onBack = { if (state.selectedContracts.isNotEmpty()) model.clearSelection() else navigation.removeLastOrNull() },
            menuItems = listOfNotNull(
                state.network?.let { network -> MenuItem(
                    TranslatableString.ResString(R.string.BlockchainSettings_Title), R.drawable.ic_settings_24,
                    onClick = { navigation.slideFromBottom(EvmNetworkPage(network)) }) },
                MenuItem(TranslatableString.ResString(R.string.Info_Title), R.drawable.ic_info_24,
                    onClick = { navigation.slideFromRight(ApprovalAccessHelpPage) }),
            ),
            bottomBar = {
                if (state.selectedContracts.isNotEmpty()) {
                    Column(Modifier.background(ComposeAppTheme.colors.tyler).imePadding()) {
                        HsDivider()
                        ButtonsGroupVertical {
                            HSButton(modifier = Modifier.fillMaxWidth(),
                                title = stringResource(R.string.ApprovalAccess_RevokeSelected, state.selectedApprovals.size),
                                enabled = canInteract && model.account?.isWatchAccount == false && state.selectedApprovals.isNotEmpty(),
                                onClick = {
                                    val account = model.account
                                    val network = state.network
                                    if (account != null && network != null) navigation.slideFromRight(
                                        ApprovalSelectionReviewPage(account.id, network.type, state.selectedApprovals.map { it.target },
                                            state.items.filter { it.contract in state.selectedContracts }.associate { it.contract to it.tokenInfo }))
                                })
                        }
                    }
                }
            },
        ) {
            Column(Modifier.fillMaxSize().then(if (state.selectedContracts.isEmpty()) Modifier.imePadding() else Modifier)) {
                Column(Modifier.background(ComposeAppTheme.colors.lawrence).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(model.account?.name ?: stringResource(R.string.ApprovalAccess_NoWallet),
                        color = ComposeAppTheme.colors.grey, style = ComposeAppTheme.typography.subhead)
                    NetworkSelector(state.network, model.networks, canInteract, model::select)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        HSButton(modifier = Modifier.fillMaxWidth(),
                            title = stringResource(when {
                                state.busy -> R.string.ApprovalAccess_Stop
                                state.scanned -> R.string.ApprovalAccess_ContinueScan
                                else -> R.string.ApprovalAccess_Scan
                            }), enabled = !state.accountChanged && state.owner != null,
                            onClick = { if (state.busy) model.cancel() else model.scan() })
                        HSButton(modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary,
                            title = stringResource(R.string.ApprovalAccess_ManualShort),
                            enabled = canInteract && state.owner != null, onClick = { manual = !manual })
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        state.message?.let { TextBlock(stringResource(it)) }
                        if (state.owner == null) TextBlock(stringResource(R.string.ApprovalAccess_UnsupportedAccount))
                        if (model.account?.isWatchAccount == true) TextBlock(stringResource(R.string.ApprovalAccess_WatchOnly))
                        if (state.busy) TextBlock(stringResource(R.string.ApprovalAccess_Scanning))
                        if (state.scanned) {
                            if (state.nextBlock != null) {
                                if (state.transactionHistoryOnly) TextBlock(stringResource(R.string.ApprovalAccess_HistoryPartial))
                                else TextBlock(stringResource(R.string.ApprovalAccess_Partial, state.nextBlock))
                            }
                            if (state.head != state.latestHead) TextBlock(stringResource(R.string.ApprovalAccess_NewBlocksPending))
                            if (state.failedChecks > 0) TextBlock(stringResource(R.string.ApprovalAccess_FailedChecks, state.failedChecks))
                            if (state.items.isEmpty() && state.message == null && state.failedChecks == 0) TextBlock(stringResource(R.string.ApprovalAccess_Empty))
                        }
                    }
                    if (manual) item { ManualApprovalCheck(state.network?.uid, canInteract && state.owner != null, model::check) }
                    if (state.items.isNotEmpty()) item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (state.selectedContracts.isEmpty()) R.string.ApprovalAccess_SelectHint else R.string.ApprovalAccess_Selected,
                                state.selectedContracts.size), modifier = Modifier.weight(1f),
                                color = ComposeAppTheme.colors.grey, style = ComposeAppTheme.typography.caption)
                            HSButton(size = ButtonSize.Small, variant = ButtonVariant.Secondary, enabled = canInteract,
                                title = stringResource(if (state.selectedContracts.isEmpty()) R.string.ApprovalAccess_SelectAll else R.string.ApprovalAccess_ClearSelection),
                                onClick = { if (state.selectedContracts.isEmpty()) model.selectAll() else model.clearSelection() })
                        }
                    }
                    items(state.items, key = { it.contract }) { item ->
                        LaunchedEffect(state.network?.uid, state.busy, item.contract) { model.loadMetadata(item.contract) }
                        ApprovalContractRow(item, state.network?.name.orEmpty(), state.selectedContracts.isNotEmpty(),
                            item.contract in state.selectedContracts, canInteract, { model.toggleSelection(item.contract) }) {
                            val account = model.account
                            val network = state.network
                            if (account != null && network != null) navigation.slideFromRight(ApprovalContractPage(
                                account.id, network.type, item.token?.coin?.name ?: item.name, item.approvals, item.tokenInfo))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun NetworkSelector(network: Blockchain?, networks: List<Blockchain>, enabled: Boolean, onSelect: (Blockchain) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        HSButton(modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary,
            title = network?.name ?: stringResource(R.string.ApprovalAccess_Network),
            icon = painterResource(R.drawable.arrow_s_down_24), enabled = enabled, onClick = { expanded = true })
        DropdownMenu(expanded, { expanded = false },
            modifier = Modifier.background(ComposeAppTheme.colors.lawrence).heightIn(max = 400.dp)) {
            networks.forEach { choice -> DropdownMenuItem(onClick = { expanded = false; onSelect(choice) }) {
                Text(choice.name, color = ComposeAppTheme.colors.leah, style = ComposeAppTheme.typography.bodyR)
            } }
        }
    }
}

@Composable
private fun ApprovalContractRow(item: ApprovalAccessItem, network: String, selecting: Boolean,
    selected: Boolean, enabled: Boolean, onSelect: () -> Unit, onDetails: () -> Unit) {
    val selectLabel = stringResource(R.string.ApprovalAccess_SelectHint)
    Row(Modifier.fillMaxWidth().combinedClickable(enabled = enabled,
        role = if (selecting) Role.Checkbox else Role.Button, onLongClickLabel = selectLabel, onLongClick = onSelect,
        onClick = { if (selecting) onSelect() else onDetails() })
        .semantics(mergeDescendants = true) {
            if (selecting) toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
        }
        .background(if (selected) ComposeAppTheme.colors.blade else ComposeAppTheme.colors.lawrence).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (item.imageUrl != null) HsImageCircle(Modifier.size(40.dp), item.imageUrl, placeholder = R.drawable.coin_placeholder)
        else CellLeftCoinIcon(item.token)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.token?.coin?.name ?: item.name ?: stringResource(R.string.ApprovalAccess_UnknownToken),
                color = ComposeAppTheme.colors.leah, style = ComposeAppTheme.typography.headline2,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.token?.coin?.code?.takeIf { it.isNotBlank() }, network.takeIf { it.isNotBlank() }).joinToString(" · "),
                color = ComposeAppTheme.colors.grey, style = ComposeAppTheme.typography.subhead,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${item.contract.take(8)}…${item.contract.takeLast(6)}",
                color = ComposeAppTheme.colors.grey, style = ComposeAppTheme.typography.caption)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.ApprovalAccess_PermissionCount, item.approvals.size),
                color = ComposeAppTheme.colors.grey, style = ComposeAppTheme.typography.caption)
            if (selecting) HsCheckbox(selected, enabled) { onSelect() }
            else Icon(painterResource(R.drawable.arrow_m_right_24), contentDescription = null, tint = ComposeAppTheme.colors.grey)
        }
    }
}

@Serializable
data object ApprovalAccessHelpPage : HSPage() {
    @Composable override fun GetContent(navigation: HSNavigation) {
        HSScaffold(title = stringResource(R.string.ApprovalAccess_Title), onBack = navigation::removeLastOrNull) {
            LazyColumn { item {
                TextBlock(stringResource(R.string.ApprovalAccess_Intro))
                TextBlock(stringResource(R.string.ApprovalAccess_Privacy))
                TextBlock(stringResource(R.string.ApprovalAccess_HistoryLimits))
                TextBlock(stringResource(R.string.ApprovalAccess_NamesHelp))
                TextBlock(stringResource(R.string.ApprovalAccess_SeparateTransactions))
                TextBlock(stringResource(R.string.ApprovalAccess_Limits))
            } }
        }
    }
}

@Composable
internal fun AccessText(text: String) {
    SelectionContainer { Text(text, color = ComposeAppTheme.colors.leah, style = ComposeAppTheme.typography.bodyR) }
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
    var invalid by rememberSaveable(networkKey) { mutableStateOf(false) }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            HSButton(modifier = Modifier.fillMaxWidth(), variant = ButtonVariant.Secondary,
                title = stringResource(kind.titleRes), enabled = enabled, onClick = { expanded = true })
            DropdownMenu(expanded, { expanded = false }, modifier = Modifier.background(ComposeAppTheme.colors.lawrence)) {
                ApprovalKind.entries.forEach { entry -> DropdownMenuItem(onClick = { kind = entry; expanded = false }) {
                    Text(stringResource(entry.titleRes), color = ComposeAppTheme.colors.leah)
                } }
            }
        }
        FormsInput(initial = contract, hint = stringResource(R.string.ApprovalAccess_ContractHint), enabled = enabled, singleLine = true,
            maxLength = 42, onValueChange = { contract = it.trim(); invalid = false })
        FormsInput(initial = spender, hint = stringResource(R.string.ApprovalAccess_SpenderHint), enabled = enabled, singleLine = true,
            maxLength = 42, onValueChange = { spender = it.trim(); invalid = false })
        if (kind == ApprovalKind.Nft) FormsInput(initial = tokenId, hint = stringResource(R.string.ApprovalAccess_TokenId),
            enabled = enabled, singleLine = true, maxLength = 78, onValueChange = { tokenId = it.trim(); invalid = false })
        if (invalid) TextBlock(stringResource(R.string.ApprovalAccess_Invalid))
        HSButton(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.ApprovalAccess_Check), enabled = enabled,
            onClick = {
                try {
                    val target = ApprovalTarget(ApprovalAbi.address(contract), ApprovalAbi.address(spender), kind,
                        tokenId.takeIf { kind == ApprovalKind.Nft })
                    ApprovalAbi.validate(target)
                    onCheck(target)
                } catch (_: IllegalArgumentException) { invalid = true }
            })
    }
}
