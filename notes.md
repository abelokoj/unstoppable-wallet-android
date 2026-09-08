## Open Swap 0.50.2

Fork of Unstoppable Wallet 0.50.1. Not affiliated with Horizontal Systems.

### Swap
- Provider icons restored across 18 providers
- Two-row route picker showing provider, time and risk score
- Uniswap V2, PancakeSwap V2 and QuickSwap providers restored
- Fee-on-transfer (tax) token swaps work

### Fees removed
- All integrator fees are zero. Upstream took 100 bps (1%) in release builds.
- THORChain and Maya affiliate tags removed. These were written into the on-chain memo, so every swap was permanently tagged.
- 1inch referrer and fee parameters dropped entirely. Per 1inch's own docs, setting them makes fee-on-transfer swaps always fail, so this also fixes tax tokens on that provider.

### Currency
- NGN (Nigerian Naira) supported. Prices are fetched in USD and converted with a single exchange rate, rather than querying a third party with your holdings.

### Interface
- Six new launcher icons: BTC, BCH, ETH, BNB, Polygon, THORChain
- Larger token icons and text; improved secondary text contrast
- Swap page amounts left-aligned with the token selector on the right
- Fees shown in your base currency by default, tap to switch

### Known limitations
- Taxed tokens need slippage set above the token's own sell tax, or the swap reverts with INSUFFICIENT_OUTPUT_AMOUNT. Quotes do not account for transfer taxes.
- The NGN exchange rate is a market rate, not the CBN official figure.

### Verify before installing

    SHA-256: 277c10bbed425c98b8df4aa7372dfe483daeefdc6ddbc19b462ac83df8d8b295
    apksigner verify --print-certs open-swap-v0.50.2.apk

Unaudited software handling private keys and real funds. Use at your own risk.
