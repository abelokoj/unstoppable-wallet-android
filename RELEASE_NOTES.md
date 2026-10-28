# Open Swap v0.52.3

Updated Open Swap to upstream Unstoppable Wallet version **0.52.2**, while retaining Open Swap's custom features and appearance options.

## New Features

- **PancakeSwap cross-chain swaps:** Added a preview supporting token swaps between Ethereum, BNB Smart Chain, Base, Arbitrum, zkSync and Solana. Routes can trade tokens before or after bridging and use validated transactions, exact token approvals and the wallet's existing signing flow. The route is labeled Excellent.
- **Revoke Access:** Added a tool for reviewing and revoking token and NFT approvals on supported EVM networks. Open it from Balances or Security Center, review token names, icons, full contract addresses, known spender names and exact or unlimited allowances, and select multiple approvals for revocation. Each approval uses a separate reviewed transaction and network fee. Scans use the wallet's configured network providers, start on BNB Smart Chain and may be incomplete when providers restrict older logs. Unrecognized spenders are labeled Unknown.
- **XRP and Arc:** Added XRP with trust-line token support and Arc with native USDC support.
- **CrossPay:** Pay with one supported token while the recipient receives another.
- **Full-balance sending:** Added a 100% shortcut to Standard and Private Send. Private Send reserves the quoted provider cost, refundable buffer and required network fee, with final amounts shown before signing.
- **More watch-only wallets:** Added support for watch-only THORChain and MayaChain wallets.

## Improvements and Fixes

- **Send experience:** Unified sending across networks with shared amount controls, a consistent confirmation screen and network-fee handling for full-balance transfers.
- **Swap controls:** Restored percentage shortcuts to the bottom of the swap page and fixed the 100% option for native tokens, with network fees reserved during confirmation. Token selectors appear on the right and amounts on the left. The selected provider's icon and name open route selection.
- **Fiat amount entry:** Fixed the cursor position so it appears after the currency symbol and before the placeholder zero.
- **Provider icons:** Added the missing Lizex exchange icon.
- **Wallet selection:** The wallet switcher now identifies the selected wallet with a circled checkmark.
- **Privacy and transaction validation:** Preserved Tor routing, improved connection recovery and swap-recipient checks, and strengthened Solana validation to reject unexpected token-account permission or extension changes. Protected Solana instruction data in optimized release builds.
- **WalletConnect:** Improved transaction warnings and session cleanup after wallet deletion.
- **Zcash:** Added Zcash Names and updated the synchronization engine.
- **Backups and history:** Corrected private-key export compatibility and improved token, Private Send and CrossPay transaction history.
- **Open Swap customization:** Retained Relay, contract-address token discovery, custom tokens across wallets, shared EVM contacts, wallet-switcher search, font choices, text sizing and custom swap percentages.

## Cross-chain Preview

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
