package io.horizontalsystems.walletkit.modules.coin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.alternativeImageUrl
import io.horizontalsystems.walletkit.core.chain.ChainRegistry
import io.horizontalsystems.walletkit.core.iconPlaceholder
import io.horizontalsystems.walletkit.core.imageUrl
import io.horizontalsystems.walletkit.core.isNative
import io.horizontalsystems.walletkit.helpers.HudHelper
import io.horizontalsystems.walletkit.modules.coin.ui.CoinScreenTitle
import io.horizontalsystems.walletkit.modules.nav3.HSNavigation
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.CellUniversalLawrenceSection
import io.horizontalsystems.walletkit.ui.helpers.LinkHelper
import io.horizontalsystems.walletkit.ui.helpers.TextHelper
import io.horizontalsystems.walletkit.uiv3.components.HSScaffold
import io.horizontalsystems.walletkit.uiv3.components.cell.CellPrimary
import io.horizontalsystems.walletkit.uiv3.components.cell.CellRightControlsIconButton
import io.horizontalsystems.walletkit.uiv3.components.controls.ButtonVariant
import io.horizontalsystems.walletkit.uiv3.components.controls.HSButton
import io.horizontalsystems.walletkit.uiv3.components.info.TextBlock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.math.BigDecimal
import java.net.URI

@Composable
internal fun BasicTokenInfoScreen(token: Token, navigation: HSNavigation) {
    val context = LocalContext.current
    val view = LocalView.current
    val reference = remember(token) { token.basicTokenReference() }
    val supply by produceState<BigDecimal?>(null, token.tokenQuery.id) {
        value = try {
            withTimeout(15_000) { withContext(Dispatchers.IO) { ChainRegistry[token.blockchainType]?.tokenTotalSupply(token) } }
        } catch (_: TimeoutCancellationException) { null }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    HSScaffold(title = token.coin.code, onBack = navigation::removeLastOrNull) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            CoinScreenTitle(token.coin.name, null, token.coin.imageUrl, token.coin.alternativeImageUrl, token.iconPlaceholder)
            Spacer(Modifier.height(12.dp))
            CellUniversalLawrenceSection(buildList<@Composable () -> Unit> {
                add { TokenDetail(stringResource(R.string.Balance_Network), token.blockchain.name) }
                if (token.basicTokenStandard() != null || token.type.isNative) add {
                    TokenDetail(stringResource(R.string.CoinInfo_Standard), token.basicTokenStandard() ?: stringResource(R.string.CoinPlatforms_Native))
                }
                add { TokenDetail(stringResource(R.string.AddToken_Decimals), token.decimals.toString()) }
                reference?.let { reference -> add {
                    TokenDetail(stringResource(reference.title), reference.value) {
                        TextHelper.copyText(reference.value)
                        HudHelper.showSuccessMessage(view, R.string.Hud_Text_Copied)
                    }
                } }
                supply?.let { amount -> add {
                    TokenDetail(stringResource(R.string.CoinPage_TotalSupply),
                        App.numberFormatter.formatCoinShort(amount, token.coin.code, token.decimals))
                } }
            })
            reference?.explorerUrl?.let { url ->
                Spacer(Modifier.height(24.dp))
                HSButton(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), variant = ButtonVariant.Secondary,
                    title = stringResource(R.string.TransactionInfo_ButtonViewOnExplorerName, URI(url).host),
                    onClick = { LinkHelper.openLinkInAppBrowser(context, url) })
            }
            TextBlock(stringResource(R.string.CoinInfo_MarketUnavailable))
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TokenDetail(label: String, value: String, onCopy: (() -> Unit)? = null) {
    CellPrimary(middle = {
        Column {
            Text(label, color = ComposeAppTheme.colors.grey, style = ComposeAppTheme.typography.subhead)
            Spacer(Modifier.height(4.dp))
            Text(value, color = ComposeAppTheme.colors.leah, style = ComposeAppTheme.typography.bodyR)
        }
    }, right = if (onCopy == null) null else { { CellRightControlsIconButton(R.drawable.copy_filled_24,
        ComposeAppTheme.colors.leah, stringResource(R.string.Button_CopyAddress), onCopy) } }, onClick = onCopy)
}
