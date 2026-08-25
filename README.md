# Unstoppable Wallet

We dream of a world… A world where private property is untouchable and market access is unconditional.

That obsession led us to engineer a crypto wallet that is equally open to all, lives online forever and unconditionally protects your assets. Only the user is in control of the money.

Unstoppable is a powerful non-custodial multi-wallet for Bitcoin, Ethereum, Binance Smart Chain, Avalanche, Solana, Zcash, The Open Network several and other blockchains. It provides non-custodial crypto storage, on-chain decentralized swaps, institutional grade analytics for cryptocurrency markets, extensive privacy controls and human oriented design. 

It is built with care and adheres to best programming practices and implementation standards in cryptocurrency world. Fully implemented on Kotlin.

More at [https://unstoppable.money](https://unstoppable.money)

## Supported Android Versions

Devices with Android versions 8.1 and above

## Download

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/io.horizontalsystems.bankwallet/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=io.horizontalsystems.bankwallet)
[<img src="docs/images/badge_obtainium.png"
     alt="Get it on Obtainium"
     height="80">](https://apps.obtainium.imranr.dev/redirect.html?r=obtainium://add/https://github.com/horizontalsystems/unstoppable-wallet-android)

## Source Code

[https://github.com/horizontalsystems/unstoppable-wallet-android](https://github.com/horizontalsystems/unstoppable-wallet-android)

## License

This wallet is open source and available under the terms of the MIT License.


---

## Fork notes

This is a personal fork of [unstoppable-wallet-android](https://github.com/horizontalsystems/unstoppable-wallet-android) (MIT).
It is **not** affiliated with or endorsed by Horizontal Systems, and is not the Unstoppable Wallet.

App name: Open Swap. Package: `money.openswap.wallet` — installs alongside the original.

### Changes

- Restored the Uniswap V2, PancakeSwap V2 and QuickSwap swap providers, which upstream
  disabled. Tax and meme tokens overwhelmingly have liquidity in V2 pairs, not V3 pools.
- Fixed selling fee-on-transfer (tax) tokens, which previously failed with
  `PancakeSwap: K` / `UniswapV2: K`. Swaps now encode the router's
  `...SupportingFeeOnTransferTokens` functions. Requires the matching fork of
  [ethereum-kit-android](https://github.com/abelokoj/ethereum-kit-android).
- Enabled WalletConnect in the fdroid flavor.
- Swap provider name shown by default.

### Known behaviour

**Taxed tokens need higher slippage.** Quotes come from standard constant-product
math and do not account for a token's transfer tax, so the displayed output is
optimistic. If slippage is below the token's own sell tax, the swap reverts with
`INSUFFICIENT_OUTPUT_AMOUNT`. Raise slippage above the tax rate.

All ERC20-input V2 swaps route through the fee-on-transfer router functions,
including untaxed tokens. This is safe (for an untaxed token the result is
identical) but costs marginally more gas.

### Verifying a release

Release APKs are signed with:

    SHA-256: 277c10bbed425c98b8df4aa7372dfe483daeefdc6ddbc19b462ac83df8d8b295
    SHA-1:   5f1d0cf868588fd989ddcf05d0996c43a8cd7bd1

Check any download before installing:

    apksigner verify --print-certs app-fdroid-release.apk

If the SHA-256 does not match, do not install it.

### Warning

Unaudited software handling private keys and real funds. Use at your own risk.
