# Open Swap v0.52.3

- **XRP and Arc:** Added XRP, including trust-line tokens, and Arc with native USDC support.
- **CrossPay:** Pay with one supported token while the recipient receives another.
- **A unified Send experience:** Updated sending across networks with shared amount controls, one confirmation screen and automatic network-fee deduction when sending your full balance.
- **Zcash improvements:** Added Zcash Names and updated the synchronization engine.
- **Privacy and signing improvements:** Updated Tor routing and recovery, strengthened swap-recipient checks, improved WalletConnect transaction warnings and corrected session handling after wallet deletion. Cross-chain Solana validation also rejects unexpected changes to token account permissions and extension data.
- **Backup compatibility:** Corrected private-key exports for consistent restoration across platforms.
- **Revoke Access:** Open approval management from Balances or Security Center, starting on BNB Smart Chain. Review token names, icons, full contract addresses, known spender names and exact or unlimited allowances, with readable token amounts and larger scanning controls. Select multiple contracts and revoke their approvals through separate reviewed transactions, each with its own network fee. Scans use the wallet's configured network providers; historical discovery can be incomplete when providers restrict older logs. Unknown spenders remain labeled Unknown.
- **Wallet and history improvements:** Added watch-only THORChain and MayaChain wallets, more detailed Private Send and CrossPay history, and more accurate token transaction history. The wallet switcher now marks the selected wallet with a circled check.
- **Send maximum:** Added 100% shortcuts to Standard and Private Send. Private Send calculates a receive amount that leaves room for the quoted provider cost, refundable buffer and required network fee, with the final amounts shown before signing.
- **PancakeSwap Cross-chain preview:** Swap supported tokens between Ethereum, BNB Smart Chain, Base, Arbitrum, zkSync and Solana. Routes can trade tokens before or after bridging, with the usual transaction review and signing. Availability depends on supported pools, liquidity and provider limits. The route is labeled Excellent. Network, pool and bridge fees apply. This preview has not been tested through funded settlement or refund recovery; the route rating is not a security guarantee.
- **Fiat amount entry:** The cursor starts after the currency symbol when entering a swap amount in your selected currency.
- **Provider icons:** Added the Lizex icon to swap routes.
- **Swap layout:** Token selectors sit on the right, with amounts on the left. Percentage shortcuts stay at the bottom, including an active 100% option for native tokens with network fees reserved during confirmation. The selected provider's icon and name open route selection.
- **Open Swap customization:** Retained Relay, contract-address token discovery, custom tokens across wallets, shared EVM contacts, wallet-switcher search, font options, text sizing and custom swap percentages.

Across support remains in development and is not included as a working direct integration in this release.

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
