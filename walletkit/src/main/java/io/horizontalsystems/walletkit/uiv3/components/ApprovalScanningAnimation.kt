package io.horizontalsystems.walletkit.uiv3.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.modules.approvals.ApprovalScanAnimation
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme
import kotlin.math.min

@Composable
internal fun ApprovalScanningAnimation(animation: ApprovalScanAnimation) {
    val background = ComposeAppTheme.colors.tyler
    val dark = background.luminance() < .5f
    val scannerResource = if (dark) R.raw.approval_scanning_dark else R.raw.approval_scanning_light
    val videoResource = when (animation) {
        ApprovalScanAnimation.Scanner -> null
        ApprovalScanAnimation.Together -> if (dark) R.raw.approval_scanning_together_dark else R.raw.approval_scanning_together_light
        ApprovalScanAnimation.Tom -> if (dark) R.raw.approval_scanning_tom_dark else R.raw.approval_scanning_tom_light
        ApprovalScanAnimation.Jerry -> if (dark) R.raw.approval_scanning_jerry_dark else R.raw.approval_scanning_jerry_light
        ApprovalScanAnimation.Original -> if (dark) R.raw.approval_scanning_original_dark else R.raw.approval_scanning_original_light
    }
    val posterResource = when (animation) {
        ApprovalScanAnimation.Scanner -> null
        ApprovalScanAnimation.Together -> if (dark) R.drawable.approval_scanning_together_dark_poster else R.drawable.approval_scanning_together_light_poster
        ApprovalScanAnimation.Tom -> if (dark) R.drawable.approval_scanning_tom_dark_poster else R.drawable.approval_scanning_tom_light_poster
        ApprovalScanAnimation.Jerry -> if (dark) R.drawable.approval_scanning_jerry_dark_poster else R.drawable.approval_scanning_jerry_light_poster
        ApprovalScanAnimation.Original -> if (dark) R.drawable.approval_scanning_original_dark_poster else R.drawable.approval_scanning_original_light_poster
    }
    var videoFailed by remember(videoResource) { mutableStateOf(false) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val playing = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()
    val contentModifier = Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(5f / 3f)

    Box(
        modifier = Modifier.fillMaxWidth().background(background).padding(horizontal = 16.dp)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (videoResource == null || posterResource == null || videoFailed) {
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(scannerResource))
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
            var videoReady by remember(videoResource, playing) { mutableStateOf(false) }
            Box(contentModifier) {
                if (playing) {
                    key(videoResource) {
                        AndroidView(
                            factory = { context ->
                                ApprovalScanVideoView(
                                    context,
                                    videoResource,
                                    onReady = { videoReady = true },
                                    onFailure = { videoFailed = true },
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                            onReset = null,
                            onRelease = { it.release() },
                        )
                    }
                }
                if (!playing || !videoReady) {
                    Image(
                        painter = painterResource(posterResource),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().background(background),
                    )
                }
            }
        }
    }
}

private class ApprovalScanVideoView(
    context: Context,
    private val resource: Int,
    private val onReady: () -> Unit,
    private val onFailure: () -> Unit,
) : TextureView(context), TextureView.SurfaceTextureListener {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var disposed = false
    private var videoWidth = 0
    private var videoHeight = 0

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        if (disposed) return
        try {
            val created = MediaPlayer()
            player = created
            surface = Surface(texture)
            created.setSurface(surface)
            created.setOnVideoSizeChangedListener { current, w, h ->
                if (player === current && !disposed) {
                    videoWidth = w
                    videoHeight = h
                    fitVideo()
                }
            }
            created.setOnPreparedListener { current ->
                if (player === current && !disposed) {
                    try {
                        current.isLooping = true
                        current.setVolume(0f, 0f)
                        current.start()
                    } catch (_: Exception) {
                        fail()
                    }
                }
            }
            created.setOnInfoListener { current, what, _ ->
                if (player === current && !disposed && what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                    onReady()
                }
                false
            }
            created.setOnErrorListener { current, _, _ ->
                if (player === current && !disposed) fail()
                true
            }
            requireNotNull(context.resources.openRawResourceFd(resource)).use { descriptor ->
                created.setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
            }
            created.prepareAsync()
        } catch (_: Exception) {
            fail()
        }
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = fitVideo()

    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releasePlayer()
        return true
    }

    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit

    private fun fitVideo() {
        if (width == 0 || height == 0 || videoWidth == 0 || videoHeight == 0) return
        val scale = min(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply {
            setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f)
        })
    }

    private fun fail() {
        releasePlayer()
        if (!disposed) onFailure()
    }

    private fun releasePlayer() {
        val current = player
        player = null
        current?.setOnPreparedListener(null)
        current?.setOnErrorListener(null)
        current?.setOnInfoListener(null)
        current?.setOnVideoSizeChangedListener(null)
        current?.release()
        surface?.release()
        surface = null
    }

    fun release() {
        disposed = true
        surfaceTextureListener = null
        releasePlayer()
    }
}
