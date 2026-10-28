# Open Swap v0.52.3

## 🔄 Updated

- **Upstream Unstoppable Wallet 0.52.2:** Updated Open Swap to include the upstream 0.52.2 release, while retaining Open Swap's custom features and appearance options.
- **Send and swap experience:** Unified sending across networks with shared amount controls and consistent confirmation screens. Restored swap percentage shortcuts to the bottom of the page, kept the 100% option available for native tokens and moved token selectors to the right.
- **Privacy, security and reliability:** Improved Tor connection recovery, swap-recipient checks, WalletConnect warnings and session cleanup. Strengthened Solana validation to reject unexpected token-account permission or extension changes, and protected instruction data in optimized builds.
- **Wallets and history:** Added Zcash Names, updated Zcash synchronization, corrected private-key export compatibility and improved token, Private Send and CrossPay history.
- **Open Swap customization:** Retained Relay, contract-address token discovery, custom tokens across wallets, shared EVM contacts, wallet-switcher search, font choices, text sizing and custom swap percentages.

## ✨ Added

- **PancakeSwap Cross-chain Preview:** Swap supported tokens between Ethereum, BNB Smart Chain, Base, Arbitrum, zkSync and Solana. Routes can trade tokens before or after bridging and use validated transactions, exact token approvals and the existing signing flow. The route is labeled Excellent.
- **Revoke Access:** Review and revoke token and NFT approvals on supported EVM networks from Balances or Security Center. See token and spender details, select multiple approvals, and confirm each revocation as a separate transaction.
- **XRP and Arc:** Added XRP with trust-line token support and Arc with native USDC support.
- **CrossPay:** Pay with one supported token while the recipient receives another.
- **Full-balance sending:** Added a 100% shortcut to Standard and Private Send. Private Send reserves the quoted provider cost, refundable buffer and required network fee before you sign.
- **Watch-only wallets:** Added support for watch-only THORChain and MayaChain wallets.

## 🐛 Fixed

- **Native-token swaps:** Fixed the 100% option so it remains available while reserving the required network fee.
- **Fiat amount entry:** Fixed the cursor position so it appears after the currency symbol and before the placeholder zero.
- **Provider icon:** Added the missing Lizex exchange icon.
- **Wallet selection:** The wallet switcher now identifies the selected wallet with a circled checkmark.

## ⚠️ Cross-chain Preview

PancakeSwap route availability depends on supported pools, liquidity and provider limits. Network, pool and bridge fees apply. Funded settlement and refund recovery have not been tested, and the Excellent rating is not a security guarantee.

Direct Across integration remains in development and is not included as a working provider in this release.

---

# Open Swap v0.51.2

- **Relay cross-chain swaps:** Swap between supported EVM networks through Relay, with transaction validation and support for the app's Tor privacy setting.
- **Find tokens by contract address:** Paste a contract address into swap search or Coin Manager to discover supported tokens and enable them directly. Manual token entry remains available.
- **Custom tokens across wallets:** Manually added tokens remain available in Coin Manager when switching wallets. Choose which tokens to enable separately for each wallet.
- **EVM contacts across chains:** Saved EVM addresses are now available across compatible networks, while preserving any chain-specific contact entries.
- **Search in Switch Wallet:** Quickly find a wallet from the long-press wallet switcher using a fixed bottom search bar, with smoother scrolling.
- **Improved swap controls:** Jupiter's provider icon is now visible, Relay's icon sizing is consistent, and percentage selectors, including 100%, remain available for native tokens. Network gas fees still apply.
- **More appearance options:** Choose Default (Manrope), System, Pretendard, Source Sans 3 or Public Sans fonts. Adjust text size in 1% increments or enter a percentage directly, with a reset option. The launch splash also follows your selected app icon.

Across support remains in development and is not included as a working direct integration in this release.
