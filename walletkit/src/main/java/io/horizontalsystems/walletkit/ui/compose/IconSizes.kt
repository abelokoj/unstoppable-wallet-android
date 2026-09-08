package io.horizontalsystems.walletkit.ui.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Open Swap fork: central icon sizes, split by context.
 *
 * Upstream hardcoded these at every call site, so changing one meant hunting through
 * a dozen files and missing several. Split per context rather than one global value,
 * because a transaction row and a bottom sheet want different weights even when they
 * happen to share a number today.
 *
 * Two forms of each where needed: a [Dp] for `Modifier.size(...)`, and an [Int] for
 * CellLeftImage, whose @ImageSize annotation restricts it to 20, 24, 32 or 48. Keep
 * each pair in sync, and keep the Int values inside that IntDef.
 */
object IconSizes {

    /** Balance list, swap page, token pickers, send/approve confirmations. Upstream: 32. */
    val Token: Dp = 48.dp
    const val TokenInt: Int = 48

    /** Market lists: Top 100, Favourites, gainers, search, TVL, platforms. Upstream: 32. */
    const val MarketRow: Int = 48

    /** Transaction history rows. Upstream: 32 inside a 42dp container. */
    val TransactionRow: Dp = 48.dp

    /** The container the transaction row icon sits in; must exceed [TransactionRow]. */
    val TransactionRowContainer: Dp = 58.dp

    /**
     * Paired overlapping icons, e.g. a swap's sold/received tokens drawn as two
     * offset circles. Deliberately smaller than [TransactionRow] since two must fit
     * inside the same container. Upstream: 24.
     */
    val TransactionRowPaired: Dp = 33.dp

    /** Transaction info page: the "You sent" / "You got" and single-token rows. */
    val TransactionInfo: Dp = 48.dp

    /** Coin detail pages: markets list, treasuries. Upstream: 32. */
    const val CoinInfoRow: Int = 48

    /** Swap provider logos in the route picker. */
    val Provider: Dp = 32.dp
    const val ProviderInt: Int = 32

    /**
     * Compact contexts where a full-size icon unbalances the row: bottom sheets,
     * inline account rows, refund pickers. Upstream: 24; unchanged.
     */
    val Compact: Dp = 24.dp
    const val CompactInt: Int = 24

    /** Blockchain/platform badges shown alongside a token. */
    val Badge: Dp = 20.dp
    const val BadgeInt: Int = 20
}
