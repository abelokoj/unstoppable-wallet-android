package io.horizontalsystems.walletkit.modules.settings.appearance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.compose.ui.text.font.FontFamily
import com.google.gson.annotations.SerializedName
import io.horizontalsystems.walletkit.R
import io.horizontalsystems.walletkit.core.App
import io.horizontalsystems.walletkit.modules.theme.ThemeService
import io.horizontalsystems.walletkit.ui.compose.TranslatableString
import io.horizontalsystems.walletkit.ui.compose.WithTranslatableTitle
import io.horizontalsystems.walletkit.ui.compose.manropeFont
import io.horizontalsystems.walletkit.ui.compose.pretendardStdFont

object AppearanceModule {

    class Factory() : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val launchScreenService = LaunchScreenService(App.localStorage)
            val appIconService = AppIconService(App.localStorage)
            val themeService = ThemeService(App.localStorage)
            return AppearanceViewModel(
                launchScreenService,
                appIconService,
                themeService,
                App.balanceViewTypeManager,
                App.localStorage,
                App.languageManager,
                App.currencyManager,
            ) as T
        }
    }

}

/**
 * App-wide typeface preference. Keeping the mapping here means the custom-font feature can be
 * removed by deleting this enum, its four font resources and the single theme parameter without
 * touching individual text components.
 */
enum class AppFont(
    val raw: String,
    override val title: TranslatableString,
    val fontFamily: FontFamily,
) : WithTranslatableTitle {
    Default("default", TranslatableString.ResString(R.string.Appearance_Font_Default), manropeFont),
    System("system", TranslatableString.ResString(R.string.Appearance_Font_System), FontFamily.Default),
    Pretendard("pretendard", TranslatableString.ResString(R.string.Appearance_Font_Pretendard), pretendardStdFont);

    companion object {
        fun fromRaw(raw: String): AppFont? = entries.find { it.raw == raw }
    }
}

@JvmInline
value class AppFontSize private constructor(val percentage: Int) {
    val raw: String
        get() = percentage.toString()

    val scale: Float
        get() = percentage / 100f

    fun previous(): AppFontSize = fromPercentage((percentage - 1).coerceAtLeast(MIN_PERCENT))!!

    fun next(): AppFontSize = fromPercentage((percentage + 1).coerceAtMost(MAX_PERCENT))!!

    companion object {
        const val MIN_PERCENT = 85
        const val MAX_PERCENT = 140
        const val DEFAULT_PERCENT = 110

        val Default = AppFontSize(DEFAULT_PERCENT)

        fun fromPercentage(percentage: Int): AppFontSize? =
            percentage.takeIf { it in MIN_PERCENT..MAX_PERCENT }?.let(::AppFontSize)

        fun fromRaw(raw: String): AppFontSize? = when (raw) {
            // Migrate the original three-choice setting without changing the user's scale.
            "small" -> AppFontSize(95)
            "default" -> Default
            "large" -> AppFontSize(125)
            else -> raw.toIntOrNull()?.let(::fromPercentage)
        }
    }
}

enum class AppIcon(val icon: Int, val titleText: String, val isDeprecated: Boolean = false) : WithTranslatableTitle {
    Main(R.drawable.launcher_main_preview, "Main"),
    Dark(R.drawable.launcher_dark_preview, "Dark"),
    Mono(R.drawable.launcher_mono_preview, "Mono"),
    Ball8(R.drawable.launcher_8ball_preview, "8ball"),
    Monero(R.drawable.launcher_monero_preview, "monero"),
    ZCash(R.drawable.launcher_zcash_preview, "ZCash"),
    Pepe(R.drawable.launcher_pepe_preview, "Pepe"),
    Doge(R.drawable.launcher_doge_preview, "Doge"),
    Punk(R.drawable.launcher_punk_preview, "Punk"),
    Ape(R.drawable.launcher_ape_preview, "#1874"),
    // Open Swap fork: removed from the picker. Kept as a deprecated entry so the manifest
    // alias still resolves for anyone with it active; setAppIcon migrates them to Main.
    Plflag(R.drawable.launcher_plflag_preview, "Plflag", true),
    // Open Swap fork: removed from the picker. Kept as a deprecated entry so the manifest
    // alias still resolves for anyone with it active; setAppIcon migrates them to Main.
    Sinwar(R.drawable.launcher_sinwar_preview, "Sinwar", true),
    Yeschad(R.drawable.launcher_yeschad_preview, "Yeschad"),
    Gigachad(R.drawable.launcher_gigachad_preview, "Gigachad"),
    Btc(R.drawable.launcher_btc_preview, "Bitcoin"),
    Bch(R.drawable.launcher_bch_preview, "Bitcoin Cash"),
    Eth(R.drawable.launcher_eth_preview, "Ethereum"),
    Bnb(R.drawable.launcher_bsc_preview, "BNB Chain"),
    Polygon(R.drawable.launcher_pol_preview, "Polygon"),
    Thorchain(R.drawable.launcher_thor_preview, "THORChain"),
    //deprecated icons with manifest aliases should stay in app for 2 releases before removal
    //remove deprecated below in 0.47
    Leo(R.drawable.launcher_leo_preview, "Leo", true),
    Mustang(R.drawable.launcher_mustang_preview, "Mustang", true),
    Yak(R.drawable.launcher_yak_preview, "Yak", true);

    override val title: TranslatableString
        get() = TranslatableString.PlainString(titleText)

    val launcherName: String
        get() = "${App.instance.packageName}.${this.name}LauncherAlias"


    companion object {
        private val map = values().associateBy(AppIcon::name)
        private val titleMap = values().associateBy(AppIcon::titleText)

        fun getActiveIcons(): List<AppIcon> = entries.filter { !it.isDeprecated }
        fun fromString(type: String?): AppIcon? = map[type]
        fun fromTitle(title: String?): AppIcon? = titleMap[title]
    }
}

enum class PriceChangeInterval(val raw: String, override val title: TranslatableString): WithTranslatableTitle {
    @SerializedName("hour_24")
    LAST_24H("hour_24", TranslatableString.ResString(R.string.Market_PriceChange_24H)),
    @SerializedName("midnight_utc")
    FROM_UTC_MIDNIGHT("midnight_utc", TranslatableString.ResString(R.string.Market_PriceChange_Utc));

    companion object {
        fun fromRaw(raw: String): PriceChangeInterval? {
            return entries.find { it.raw == raw }
        }
    }
}
