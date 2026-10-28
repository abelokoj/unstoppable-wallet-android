# Open Swap

A self-custody, multi-chain Android wallet with decentralized swaps, cross-chain routes and privacy controls.

Forked from [Unstoppable Wallet](https://github.com/horizontalsystems/unstoppable-wallet-android) with source updated through upstream version 0.53.0. Open Swap is an independent personal fork and is not affiliated with or endorsed by Horizontal Systems.

[Download APK](https://github.com/abelokoj/unstoppable-wallet-android/releases/latest) • [Release Notes](RELEASE_NOTES.md) • [Features](#-features) • [Security](#-security-and-privacy) • [Verify a Release](#-verify-a-release)

---

## 🚀 Overview

Open Swap builds on Unstoppable Wallet's non-custodial multi-chain foundation while retaining Open Swap features for swapping, token discovery, wallet customization and approval management. It installs alongside the official Unstoppable Wallet under the package name `money.openswap.wallet`.

## ✨ Features

### 🔄 Swaps and Cross-chain Routes

- **PancakeSwap Cross-chain Preview:** Swap supported tokens between Ethereum, BNB Smart Chain, Base, Arbitrum, zkSync and Solana. Routes can trade tokens before or after bridging.
- **Relay Cross-chain Swaps:** Swap between supported EVM networks through Relay while honoring the app's Tor privacy setting when enabled.
- **Direct Swap Providers:** Retains Uniswap V2 and V3, PancakeSwap V2 and V3, QuickSwap and other available providers, including fee-on-transfer token support on V2 routes.
- **Token Discovery:** Paste a contract address into Swap or Coin Manager to find a supported token and enable it directly. Manual token entry remains available.

### 🛡️ Wallet Security and Control

- **Revoke Access:** Review and revoke supported EVM token and NFT approvals from Balances or Security Center. Inspect token and spender details, then confirm each revocation with the existing signing flow.
- **Full-balance Controls:** Use 100% in Standard Send, Private Send and Swap while the app reserves the required network fee.
- **Cross-wallet Token Management:** Manually added tokens remain available when you switch wallets, while each wallet controls whether that token is enabled.
- **Shared EVM Contacts:** Contacts saved on one EVM network are available across compatible EVM networks.

### 🎨 Everyday Use

- **Flexible Appearance:** Choose Manrope, System, Pretendard, Source Sans 3 or Public Sans, adjust font size, and use your selected app icon as the launch splash.
- **Faster Wallet Switching:** Search from the long-press wallet switcher and see the active wallet with a circled checkmark.
- **More Chains and Sending Options:** Includes NEAR and NEP-141 tokens with named-account selection, XRP with trust-line assets, Arc with native USDC, CrossPay, watch-only THORChain and MayaChain wallets, Zcash Names and unified sending.

---

## 📥 Download

1. Open the [latest release](https://github.com/abelokoj/unstoppable-wallet-android/releases/latest) and download the signed Open Swap APK.
2. Verify the certificate fingerprint below before installing.
3. Open the APK on an Android 9 or newer device and allow installation from your chosen source if Android asks.
4. Install updates over the existing Open Swap app to retain its data. Do not install an APK with a different certificate.

Open Swap installs alongside the official Unstoppable Wallet. It does not replace or alter the official app or its wallet data.

---

## 🔐 Verify a Release

Official Open Swap release APKs use this signing certificate:

```text
SHA-256: 277c10bbed425c98b8df4aa7372dfe483daeefdc6ddbc19b462ac83df8d8b295
SHA-1:   5f1d0cf868588fd989ddcf05d0996c43a8cd7bd1
```

Check a downloaded APK before installing it:

```text
apksigner verify --print-certs open-swap-v0.53.0.apk
```

Do not install the file if the SHA-256 fingerprint does not match.

---

## ⚠️ Security and Privacy

Open Swap is self-custody software that handles private keys and real funds. Use only amounts you can afford to risk and verify transaction details, token contracts and spender addresses before signing.

PancakeSwap Cross-chain is a preview. Availability depends on liquidity and provider limits, and pool, bridge, provider and network fees may apply. The Excellent route label is not a security guarantee. Funded settlement and refund recovery have not been tested. Direct Across integration remains in development and is not a working provider in this release.

NEAR device flows, funded sends and failure recovery have not been exercised in the 0.53.0 integration. Account discovery shares the derived public key with FastNEAR; configured services receive public account, contract and transaction data needed for their requests. See the [NEAR review record](docs/chain-integration/near/review.md) for the repairs and remaining checks.

For fee-on-transfer tokens, set slippage above the token's own transfer tax. Quotes do not account for token taxes, so a lower tolerance can cause the swap to fail.

Tor protects supported network requests only while enabled and connected. External providers and configured RPC services can still receive the information necessary to quote, simulate or submit transactions.

---

## 📚 Project Information

- [Release notes](RELEASE_NOTES.md) describe each published version.
- [Changelog](CHANGELOG.md) records implementation, validation and known limitations.
- [PancakeSwap Cross-chain details](docs/PancakeSwap-CrossChain.md) documents current route coverage and its limits.
- [Unstoppable Wallet](https://github.com/horizontalsystems/unstoppable-wallet-android) is the upstream project.
- Open Swap is available under the [MIT License](LICENSE).
