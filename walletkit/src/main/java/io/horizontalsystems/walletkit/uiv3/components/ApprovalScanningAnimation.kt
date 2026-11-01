package io.horizontalsystems.walletkit.uiv3.components

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.modules.approvals.ApprovalScanAnimation
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme

@Composable
internal fun ApprovalScanningAnimation(animation: ApprovalScanAnimation) {
    val background = ComposeAppTheme.colors.tyler
    val resource = when (animation) {
        ApprovalScanAnimation.Scanner -> if (background.luminance() < .5f) {
            R.raw.approval_scanning_dark
        } else {
            R.raw.approval_scanning_light
        }
        ApprovalScanAnimation.Together -> R.raw.approval_scanning_together
        ApprovalScanAnimation.Tom -> R.raw.approval_scanning_tom
        ApprovalScanAnimation.Jerry -> R.raw.approval_scanning_jerry
    }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
    val contentModifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(5f / 3f)

    Box(
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 16.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (animation == ApprovalScanAnimation.Scanner) {
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resource))
            LottieAnimation(
                composition = composition,
                isPlaying = playing,
                restartOnPlay = false,
                iterations = LottieConstants.IterateForever,
                // The approved 1.5x speed is already baked into the local assets.
                speed = 1f,
                safeMode = true,
                modifier = contentModifier,
            )
        } else {
            val context = LocalContext.current
            val request = remember(context, resource) {
                ImageRequest.Builder(context).data(resource).crossfade(false).build()
            }
            val painter = rememberAsyncImagePainter(request)
            val drawable = (painter.state as? AsyncImagePainter.State.Success)?.result?.drawable as? Animatable
            DisposableEffect(drawable, playing) {
                if (playing) drawable?.start() else drawable?.stop()
                onDispose { drawable?.stop() }
            }
            Image(painter = painter, contentDescription = null, modifier = contentModifier)
        }
    }
}
