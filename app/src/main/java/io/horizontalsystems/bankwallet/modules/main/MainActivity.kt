package io.horizontalsystems.bankwallet.modules.main

import android.animation.ValueAnimator
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.compose.setContent
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.core.BaseActivity
import io.horizontalsystems.walletkit.modules.nav3.EntryPage
import io.horizontalsystems.walletkit.modules.nav3.Nav3
import io.horizontalsystems.walletkit.modules.settings.appearance.AppIcon
import io.horizontalsystems.walletkit.ui.compose.ComposeAppTheme

class MainActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // With no fixed splash drawable in Theme.App.Starting, Android 12+ starts from the
        // enabled launcher alias. Replace the compat splash view too so older Android versions
        // and deep-link launches use the icon persisted in Appearance.
        splashScreen.setOnExitAnimationListener { splashView ->
            val selectedIcon = App.localStorage.appIcon
                ?.takeUnless(AppIcon::isDeprecated)
                ?: AppIcon.Thorchain
            (splashView.iconView as? ImageView)?.setImageResource(selectedIcon.icon)

            if (ValueAnimator.areAnimatorsEnabled()) {
                splashView.view.animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_DURATION_MS)
                    .withEndAction(splashView::remove)
                    .start()
            } else {
                splashView.remove()
            }
        }

        setContent {
            val appFont by App.localStorage.appFontFlow.collectAsStateWithLifecycle()
            val appFontSize by App.localStorage.appFontSizeFlow.collectAsStateWithLifecycle()
            ComposeAppTheme(fontFamily = appFont.fontFamily, fontScale = appFontSize.scale) {
                Nav3(EntryPage)
            }
        }
    }

    private companion object {
        const val SPLASH_EXIT_DURATION_MS = 150L
    }
}
