package io.horizontalsystems.walletkit.modules.approvals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.horizontalsystems.marketkit.models.BlockchainType
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.modules.nav3.HSPage
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.bottombars.ButtonsGroupVertical
import io.horizontalsystems.walletkit.uiv3.components.controls.HSButton
import io.horizontalsystems.walletkit.uiv3.components.info.TextBlock
import io.horizontalsystems.walletkit.ui.compose.components.HsDivider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class ApprovalSelectionReviewPage(
    val accountId: String,
    val blockchainType: BlockchainType,
    val targets: List<ApprovalTarget>,
    val tokens: Map<String, ApprovalTokenInfo> = emptyMap(),
) : HSPage() {
    override fun contentKey(): String {
        val selection = targets.map { it.key }.distinct().sorted().joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256").digest(selection.toByteArray()).joinToString("") { "%02x".format(it) }
        return "ApprovalSelection:$accountId:${blockchainType.uid}:$digest"
    }

    @Composable override fun GetContent(navigation: HSNavigation) {
        var submitted by rememberSaveable { mutableStateOf(mapOf<String, String>()) }
        val uniqueTargets = remember(targets) { targets.distinctBy { it.key } }
        val next = uniqueTargets.firstOrNull { it.key !in submitted }
        val accountState by App.accountManager.activeAccountStateFlow.collectAsState(initial = null)
        val signingAllowed = remember(accountState) {
            App.accountManager.activeAccount?.let { it.id == accountId && !it.isWatchAccount } == true
        }
        val reviewNext = navigation.slideFromRightForResult<ApprovalRevokePage.Result>(
            screenBuilder = { ApprovalRevokePage(accountId, blockchainType, requireNotNull(next)) },
            onResult = { result ->
                if (uniqueTargets.any { it.key == result.targetKey } &&
                    result.transactionHash.matches(Regex("^0x[0-9a-fA-F]{64}$"))) {
                    submitted = submitted + (result.targetKey to result.transactionHash)
                }
            },
        )
        HSScaffold(title = stringResource(R.string.ApprovalAccess_SelectionTitle), onBack = navigation::removeLastOrNull,
            bottomBar = { ButtonsGroupVertical {
                HSButton(modifier = Modifier.fillMaxWidth(),
                    title = stringResource(if (next == null) R.string.Button_Done else R.string.ApprovalAccess_Review),
                    enabled = next == null || signingAllowed,
                    onClick = { if (next == null) navigation.removeLastOrNull() else reviewNext() })
            } }) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    TextBlock(stringResource(R.string.ApprovalAccess_SeparateTransactions))
                    TextBlock(stringResource(R.string.ApprovalAccess_SubmittedCount, submitted.size, uniqueTargets.size))
                    if (!signingAllowed) TextBlock(stringResource(R.string.ApprovalAccess_WalletChanged))
                }
                items(uniqueTargets, key = { it.key }) { target ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ApprovalIdentity(target, tokens[target.contract.lowercase()] ?: ApprovalTokenInfo())
                        target.tokenId?.let { AccessText(stringResource(R.string.ApprovalAccess_TokenIdValue, it)) }
                        submitted[target.key]?.let { hash ->
                            AccessText(stringResource(R.string.ApprovalAccess_PendingConfirmation))
                            AccessText(hash)
                        }
                    }
                    HsDivider()
                }
            }
        }
    }
}

@Serializable
data class ApprovalContractPage(
    val accountId: String,
    val blockchainType: BlockchainType,
    val name: String?,
    val approvals: List<ActiveApproval>,
    val token: ApprovalTokenInfo = ApprovalTokenInfo(name = name),
) : HSPage() {
    override fun contentKey() = "ApprovalContract:$accountId:${blockchainType.uid}:${approvals.firstOrNull()?.target?.contract}"

    @Composable override fun GetContent(navigation: HSNavigation) {
        val accountState by App.accountManager.activeAccountStateFlow.collectAsState(initial = null)
        val signingAllowed = remember(accountState) {
            App.accountManager.activeAccount?.let { it.id == accountId && !it.isWatchAccount } == true
        }
        HSScaffold(title = name ?: stringResource(R.string.ApprovalAccess_UnknownToken), onBack = navigation::removeLastOrNull,
            bottomBar = { ButtonsGroupVertical {
                HSButton(modifier = Modifier.fillMaxWidth(), title = stringResource(R.string.Swap_Revoke),
                    enabled = signingAllowed && approvals.isNotEmpty(), onClick = {
                        navigation.slideFromRight(ApprovalSelectionReviewPage(accountId, blockchainType, approvals.map { it.target },
                            approvals.associate { it.target.contract.lowercase() to token }))
                    })
            } }) {
            LazyColumn {
                items(approvals, key = { it.target.key }) { approval ->
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ApprovalIdentity(approval.target, token)
                        if (approval.target.kind == ApprovalKind.Token) {
                            val amount = token.amount(approval.rawAllowance)
                            val exactAmount = amount?.stripTrailingZeros()?.toPlainString()?.let {
                                listOfNotNull(it, token.symbol?.takeIf(String::isNotBlank)).joinToString(" ")
                            } ?: stringResource(R.string.ApprovalAccess_RawAmountValue, approval.rawAllowance)
                            AccessText(stringResource(R.string.ApprovalAccess_ApprovalType,
                                if (approval.rawAllowance == ApprovalAbi.maxUint256.toString())
                                    stringResource(R.string.ApprovalAccess_UnlimitedValue) else exactAmount))
                            AccessText(stringResource(R.string.ApprovalAccess_TokenAmount, amount?.let {
                                App.numberFormatter.formatCoinShort(it, token.symbol, requireNotNull(token.decimals))
                            } ?: stringResource(R.string.ApprovalAccess_UnknownAmount)))
                        } else {
                            AccessText(stringResource(R.string.ApprovalAccess_ApprovalType, stringResource(approval.target.kind.titleRes)))
                            approval.target.tokenId?.let { AccessText(stringResource(R.string.ApprovalAccess_TokenIdValue, it)) }
                        }
                    }
                    HsDivider()
                }
            }
        }
    }
}

@Composable
private fun ApprovalIdentity(target: ApprovalTarget, token: ApprovalTokenInfo) {
    val spenderName by produceState<String?>(initialValue = null, key1 = target.spender) {
        try {
            value = withContext(Dispatchers.IO) { App.evmLabelManager.addressLabel(target.spender)?.takeIf(String::isNotBlank) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
    }
    AccessText(stringResource(R.string.ApprovalAccess_TokenNameValue,
        token.name ?: stringResource(R.string.ApprovalAccess_UnknownToken)))
    AccessText(stringResource(R.string.ApprovalAccess_Contract, target.contract))
    AccessText(stringResource(R.string.ApprovalAccess_Spender,
        spenderName ?: stringResource(R.string.ApprovalAccess_Unknown)))
    AccessText(stringResource(R.string.ApprovalAccess_SpenderContract, target.spender))
}
