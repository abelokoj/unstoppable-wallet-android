# Open Swap v0.53.3

Build 190 improves PancakeSwap cross-chain quotes, adds authentication to approval revocations, refreshes scan animations and restores larger home-page token icons.

## 🔄 Updated

- **PancakeSwap Cross-chain:** Improved quote selection with broader liquidity-pool discovery, multi-hop routes and split swaps on supported EVM networks. Source swaps, bridging and destination swaps now use a more consistent comparison of expected output and minimum received.
- **Scan animation order:** Successive Revoke Access scans now cycle through Tom and Jerry together, Tom and Jerry Original, the crypto-document scanner, Tom alone and Jerry alone. The rotation continues between visits and follows the app's light or dark appearance.
- **Original animation:** Refreshed the original Tom and Jerry ground-search animation. The crypto scanner and the other animation styles are unchanged.

## 🐛 Fixed

- **Revoke Access:** Require fresh biometric confirmation when enabled, with wallet-passcode fallback, before each revocation. Canceling or leaving authentication does not submit a transaction. Wallets without a passcode are prompted to set one up first.
- **Balances icons:** Restored the larger token icons on the home page after an upstream update reduced their size. Blockchain badges and sync-error indicators are retained.

## ⚠️ Notes

PancakeSwap Cross-chain remains a preview. Pool, bridge, provider and network fees may apply; no Open Swap developer or referral fee is added. Quotes can change before confirmation, and identical prices to the PancakeSwap website are not guaranteed. Funded settlement and refund-recovery testing remain pending.

Existing wallet signing and Tor preferences are retained. Enabled but disconnected Tor is not bypassed for supported routing requests.

Physical-device testing of the new authentication flow is still pending.

---
# Open Swap v0.53.2

Build 189 improves approval discovery, adds animated scanning and makes token swaps easier to read.

## ✨ Added

- **Basic Coin Info:** View an unlisted token's name, symbol, network, token standard, decimals and full copyable contract or mint address. Open its block explorer and see total supply when available on supported EVM and Solana networks.
- **Scan animations:** Revoke Access rotates between the crypto-document scanner, Tom and Jerry together, Tom alone, Jerry alone and the original ground-search cartoon. The selection continues between visits, follows the app's light or dark appearance and respects disabled system animations.
- **Full swap amounts:** Tap either main swap amount to reveal its complete value inline. The input remains editable and the received amount stays read-only, without opening a separate popup.

## 🔄 Updated

- **Smaller scan animations:** Replaced the large cartoon files with compact videos and still frames for reduced-motion settings. The crypto scanner is unchanged and is also used if video playback fails.
- **Relay Bridge:** Updated the route rating from Fair to Good. Provider restrictions, fees and settlement risks still apply.
- **Android packages:** Development and release APKs now use the F-Droid distribution.
- **Swap amounts:** Both main amount displays use up to 10 characters, retaining meaningful digits and familiar K, M, B and T abbreviations. Very small or large values use scientific notation when needed. Formatting preserves the exact amount used for quotes, review and signing.
- **Swap routes:** Provider names and received amounts share one line, with amounts aligned right and selection indicators beside the time and rating below. The swap-page exchange rate uses the available space beside the provider and wraps when necessary. Existing fonts, sizes and colors are retained.
- **Revoke confirmation:** Revoke Access follows the wallet's existing token-approval confirmation and signing flow.

## 🐛 Fixed

- **Provider icon:** Added the missing NEAR Confidential swap-provider icon.
- **Approval RPC fallback:** Permission checks try the selected network provider first, then other available built-in and custom RPCs when a response is empty or unavailable. Failed checks remain visible as incomplete, rather than being reported as no approvals found.
- **BNB Smart Chain approvals:** Scanning fetches fresh address transaction history independently of history stored on the phone. Historical receipt lookup can recover when an RPC omits an old transaction, and current permissions are checked before display.
- **Scanning feedback:** The checking message and animation remain visible during repeat scans while previous results are still listed, then disappear when the operation finishes.
- **Approval details:** Fixed crashes when opening very large or unlimited approvals, corrected large-number formatting and improved text-selection stability.
- **Coin Info:** Fixed crashes when opening information for tokens missing from the market catalogue. Recognized tokens retain their existing market page.

## ⚠️ Notes

Approval discovery depends on provider availability and history coverage; some older or relayed approvals may not appear. Fallback checks can contact multiple configured providers. Scanning follows your Tor preference. When Tor is enabled, a disconnected Tor connection is not bypassed. With Tor off, network providers can see your public wallet address and IP address.

Basic token details do not add missing market prices, charts or profiles; these still depend on catalogue coverage. Total supply is shown only when it can be verified through the selected provider.

---
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
