package io.horizontalsystems.walletkit.ui.compose

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Open Swap fork: central icon sizes.
 *
 * Upstream hardcodes these at every call site, so changing one meant hunting through a
 * dozen files and missing several. Anything used in more than one place belongs here.
 *
 * Two forms of each: a [Dp] for `Modifier.size(...)`, and an [Int] for components like
 * CellLeftImage, whose @ImageSize annotation restricts it to 20, 24, 32 or 48. Keep the
 * pair in sync -- the Int values must stay within that IntDef or the build warns.
 */
object IconSizes {

    /**
     * Token and coin icons in primary contexts: balance list, swap page, token pickers,
     * market lists, transaction confirmations. Upstream used 32 throughout.
     */
    val Token: Dp = 48.dp
    const val TokenInt: Int = 48

    /**
     * Compact contexts where a full-size icon would unbalance the row: bottom sheets,
     * inline account rows, refund pickers. Upstream used 24; unchanged.
     */
    val TokenCompact: Dp = 24.dp
    const val TokenCompactInt: Int = 24

    /** Swap provider logos in the route picker. Restored from upstream 0.48.5 at 24. */
    val Provider: Dp = 32.dp
    const val ProviderInt: Int = 32

    /** Blockchain/platform badges shown alongside a token. */
    val Badge: Dp = 20.dp
    const val BadgeInt: Int = 20
}
