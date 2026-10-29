# Open Swap v0.53.1

This update fixes a startup crash affecting saved custom tokens.

## 🐛 Fixed

- **Startup stability:** Fixed a crash when opening the app after an update, including when loading custom tokens saved by earlier versions.
- **Custom tokens:** An invalid saved token no longer prevents other custom tokens from loading. Existing wallet data is preserved.

---
# Open Swap v0.53.0

This update brings upstream Unstoppable Wallet 0.53.0 to Open Swap while retaining its current appearance, direct swap providers and route ratings.

## ✨ Added

- **NEAR wallets:** Manage native NEAR and NEP-141 tokens, restore supported named accounts and add watch-only accounts. Account selection checks whether your recovery phrase has full access.
- **More swap routes:** Added XSwap and made NEAR Confidential available from the ordinary Swap screen, alongside the existing providers.
- **Automatic receiving tokens:** After a successful swap submission to your own wallet, supported receiving tokens are enabled so they can appear in Balances.

## 🔄 Updated

- **Swap, Private Send and CrossPay:** Updated hosted routes to preview an offer before creating an order. Committed deposits use the confirmed amount and current fee settings, and submitted transactions remain trackable after an uncertain network response.
- **Zcash balances and shielding:** Improved spendable-balance handling and full-balance sending. Send, swap and CrossPay offer a shielding step when needed.
- **Route protection:** MEV protection is offered only where the selected route supports it. Existing Open Swap providers, icons, ratings and swap controls remain available.
- **Network reliability:** Included upstream Bitcoin/eCash synchronization, Monero reconnection and Tron fee-estimation improvements.

## 🐛 Fixed

- **NEAR token safety:** Unknown token history retains its contract identity and stays suspicious even when it claims to come from a saved contact. Failed metadata reads show raw units, and failed token-balance verification cannot become an available balance.
- **NEAR restore and wallet switching:** Invalid backup account identifiers are rejected before saving, discovery requests are bounded, and delayed token discovery cannot enable tokens in a different wallet.
- **Address scanning and names:** Moved slow address checks off the main screen thread, replaced stale QR-scan results and retained ENS recognition alongside NEAR names.

## ⚠️ Notes

NEAR account and history test coverage remains limited. Funded sends, controlled failure recovery and iOS parity have not been verified. Discovery and network providers receive the public information needed for account lookup, balances, history and transaction submission. Tor traffic has not been independently captured.

Provider, pool, bridge and network fees and availability limits still apply. The existing PancakeSwap Cross-chain preview and refund-testing limits remain unchanged; route ratings do not guarantee safety. Direct Across integration remains deferred.

---
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
