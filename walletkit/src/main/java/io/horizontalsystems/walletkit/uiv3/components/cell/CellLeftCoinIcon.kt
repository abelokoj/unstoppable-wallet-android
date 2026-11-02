package io.horizontalsystems.walletkit.uiv3.components.cell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.horizontalsystems.marketkit.models.Token
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.imageUrl
import io.horizontalsystems.walletkit.core.isNative
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import io.horizontalsystems.walletkit.ui.compose.components.CoinImage
import io.horizontalsystems.walletkit.ui.compose.components.HsImage

private val iconSize = 40.dp
private val badgeIconSize = 16.dp
private val badgeBorder = 1.dp
private val badgeSize = badgeIconSize + badgeBorder * 2

// warning_filled_24 insets its octagon by 2 of the 24 viewport units on every side, so a layer has
// to be scaled by this much for the octagon itself — rather than its bounding box — to come out at
// the intended size. Both layers overflow the badge box, hence requiredSize: a plain size() would
// be coerced into the box's constraints and both would collapse to the same size, erasing the
// border entirely.
private const val syncFailedGlyphScale = 24f / 20f
private val syncFailedIconSize = badgeIconSize * syncFailedGlyphScale
private val syncFailedBorderSize = badgeSize * syncFailedGlyphScale

// The badge overhang stays independent of the requested coin size so it reads as a separate mark.
private val badgeOverhang = 3.dp

private val badgeIconShape = RoundedCornerShape(4.dp)
private val badgePlateShape = RoundedCornerShape(4.dp + badgeBorder)

/**
 * Coin icon with the blockchain it lives on marked in the bottom-right corner.
 *
 * A failed sync takes over that corner: the chain is implied by the coin the row already names,
 * while the failure is the thing the user has to act on.
 */
@Composable
fun CellLeftCoinIcon(
    token: Token?,
    syncFailed: Boolean = false,
    iconAlpha: Float = 1f,
    onClickSyncError: (() -> Unit)? = null,
    size: Dp = iconSize,
) {
    val clickable = if (syncFailed && onClickSyncError != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, color = ComposeAppTheme.colors.leah),
            onClick = onClickSyncError
        )
    } else {
        Modifier
    }

    Box(modifier = Modifier.size(size).then(clickable)) {
        CoinImage(
            token = token,
            modifier = Modifier
                .size(size)
                .alpha(iconAlpha),
        )

        when {
            // The border traces the octagon instead of sitting behind it as a plate: it is the same
            // silhouette grown by the border width. It has to be the solid one, so that it also
            // backs the exclamation mark, which is a cut-out in the red layer above.
            syncFailed -> Badge {
                Icon(
                    modifier = Modifier.requiredSize(syncFailedBorderSize),
                    painter = painterResource(R.drawable.warning_octagon_24),
                    contentDescription = null,
                    tint = ComposeAppTheme.colors.lawrence,
                )
                Icon(
                    modifier = Modifier.requiredSize(syncFailedIconSize),
                    painter = painterResource(R.drawable.warning_filled_24),
                    contentDescription = stringResource(R.string.BalanceSyncError_Title),
                    tint = ComposeAppTheme.colors.lucian,
                )
            }

            token != null && !token.type.isNative -> Badge {
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .background(ComposeAppTheme.colors.lawrence, badgePlateShape)
                )
                HsImage(
                    url = token.blockchainType.imageUrl,
                    placeholder = R.drawable.ic_platform_placeholder_24,
                    modifier = Modifier
                        .size(badgeIconSize)
                        .clip(badgeIconShape)
                        .alpha(iconAlpha),
                )
            }
        }
    }
}

/**
 * Positions a badge in the icon's bottom-right corner. The badge draws its own border, because the
 * border follows the badge's own outline — a rounded square for a chain icon, the octagon for a
 * failed sync.
 */
@Composable
private fun BoxScope.Badge(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = badgeOverhang, y = badgeOverhang)
            .size(badgeSize),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
