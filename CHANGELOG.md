# Open Swap — change log

A personal fork of [unstoppable-wallet-android](https://github.com/horizontalsystems/unstoppable-wallet-android) (MIT). Not affiliated with or endorsed by Horizontal Systems.

**App name:** Open Swap · **Package:** `money.openswap.wallet` · **Signing SHA-256:** `277c10bbed425c98b8df4aa7372dfe483daeefdc6ddbc19b462ac83df8d8b295`

## Repositories

| Repo | Branch / pin | Base | Notes |
|---|---|---|---|
| [unstoppable-wallet-android](https://github.com/abelokoj/unstoppable-wallet-android) | `master` | upstream `0.50.1` | the app |
| [ethereum-kit-android](https://github.com/abelokoj/ethereum-kit-android) | `feature/fee-on-transfer` @ `11b92fc` | `665021b` | fee-on-transfer swaps, EIP-7702 |
| [market-kit-android](https://github.com/abelokoj/market-kit-android) | `feature/ngn-rates` @ `966a222` | `33432f8` | NGN rate conversion |

Both kit forks branch from the **commit the wallet pins**, not `master`. Upstream `market-kit` master has since replaced RxJava with coroutines, and `ethereum-kit` master diverges similarly — building against either would break the wallet.

---

# 0.50.2 — 2026-09-06 to 2026-09-08

`versionCode 178` · based on upstream `0.50.1`

## Swap providers

**Restored Uniswap V2, PancakeSwap V2 and QuickSwap** — 2026-08-25 · `c69f59d0a`

Upstream commented these out of the registry in `45d60a43b` and never deleted the classes; `acfd98cb3` restored only the V3 variants. Re-added to `EvmChainPlugin.swapProviders()`. Tax and meme tokens overwhelmingly have liquidity in V2 pairs rather than V3 pools.

**Fixed fee-on-transfer (tax) token swaps** — 2026-08-25 · kit `11b92fc`

Selling a taxed token failed with `PancakeSwap: K` / `UniswapV2: K`. The pair contract receives less than the nominal `amountIn`, so the constant-product check fails. Root cause was in `ethereum-kit`, not the wallet: `uniswapkit` had never implemented the encoding side of the router's `...SupportingFeeOnTransferTokens` functions.

Added three method encoders — `0x5c11d795`, `0xb6f9de95`, `0x791ac947` — each subclassing its plain counterpart so `SwapTransactionDecorator`'s `is` checks still decorate these transactions. Also fixed three existing decoder factories that derived `methodId` from the *plain* variant's signature, so they could never match a real fee-on-transfer transaction and silently overwrote the plain factories in the registry map. Verified against canonical selectors and hand-computed calldata.

**Provider icons restored** — 2026-09-06 · `acb90644d`

Upstream's `82f4fbc0d` ("Remove provider names in Swap") deleted the `icon` field, every override and 18 drawables. Restored across 18 providers, including `UProvider`'s sub-providers. Drawables recovered from upstream tag `0.48.5` and commit `82f4fbc0d^`.

**Two-row route picker** — 2026-09-06 · `acb90644d`

Provider logo, name and amount on row one; clock, time, risk badge and fiat value on row two. Restored from `82f4fbc0d^`, the last commit before the redesign.

**Provider names shown by default** — 2026-08-25 · `5e2444c67`

`showSwapProviderName` defaulted to `false` and every display site was additionally gated behind `BuildConfig.DEBUG`. Both removed.

## Fees and privacy

**All integrator fees removed** — 2026-09-06 · `acb90644d`

`SWAP_FEE_BPS` was **100 bps (1%) in release builds**, 25 in debug. Now 0. Three providers consumed it:

- **THORChain** and **Maya** — `affiliate` and `affiliateBps` set to `null`. This mattered beyond the fee: these providers return a memo built from those values, and the memo is broadcast on-chain in `OP_RETURN`, transaction data or a shielded memo field. Every swap was permanently tagged.
- **1inch** — `referrer` and `fee` parameters dropped entirely rather than zeroed. Per 1inch's own troubleshooting docs, setting them makes fee-on-transfer swaps *always fail*, so this also fixed tax tokens on that provider.

Audited every other provider: Uniswap, PancakeSwap V2/V3, QuickSwap, AllBridge, the uSwap sub-providers, Stellar and Solana never took a cut.

## Currency

**NGN (Nigerian Naira)** — 2026-09-07 · `6e2442103` · kit `966a222`

The Horizontal Systems backend whitelists currencies server-side and does not serve NGN, so requesting it returned nothing. Prices are now fetched in USD and converted with a single FX rate from `open.er-api.com`.

Converted in `CoinPriceSchedulerProvider`, the only path into `CoinPriceManager.handleUpdated` — so the Room cache, every observable and all 27 price call sites get NGN with no other changes.

**Not CoinGecko directly**, despite coins already carrying a `coinGeckoId`. A `/simple/price` call sends the list of coins the user holds plus their IP to a third party, fingerprinting their portfolio and building a durable profile over repeated refreshes. Routing crypto prices through the HS backend is precisely what prevents that. The FX endpoint is asked one identical question by every user and reveals nothing about holdings.

Stale rates are served up to 24h rather than blanking; past that, prices are hidden rather than shown wrong.

**Nigeria flag drawable** — 2026-09-07 · `6e2442103`

Drawn from the official specification (`#008751`, equal thirds). The web repo's `ng.svg` is a stylised icon with angular notches, not the flag.

## Number formatting — 2026-09-08

**Subscript compression for small values.** `0.0000012345` renders as `0.0₅12345`. Applied in `NumberFormatter.formatRounded`, the single path every format method routes through.

Fixed 7-character budget after the decimal point, shared between the subscript and the digits — so a one-digit subscript gets 5 digits and a two-digit subscript gets 4, and compressed values line up in a column. Triggers above 2 leading zeros; `0.001234` is left alone.

**Trailing zeros kept only where real.** `0.0559803` → `0.055980` (the sixth digit is a genuine zero from truncation) but `0.5` → `0.5` (padding to `0.500000` would invent five zeros). Upstream stripped unconditionally, losing the first case.

**Uncompressed sub-1 values capped at 6 decimals.** Upstream's `zeros + maxFractionNonZeroDigits` gave 5 to 7 depending on the value.

**Values ≥ 1 show at least 2 decimals**, as currency conventionally does: `1.00`, `20.00`, `999.50`. Real precision beyond 2 is preserved.

**Abbreviated values fixed at 2 decimals** — `1.72 T`, `304.70 B`, `250.00 M`. Upstream used 2/1/0 decimals by magnitude and stripped trailing zeros, giving ragged widths.

**Scientific notation above quadrillion** — `1.23E18`. The named scale stops at Q, and nothing beyond has a suffix anyone would recognise. Reachable via token balances, not fiat values.

## Interface

**Six launcher icons** — 2026-09-06 · `acb90644d`

BTC, BCH, ETH, BNB, Polygon and THORChain, as adaptive icon vectors. `minSdk 28` means no PNG fallbacks are needed — four XML files each. ETH and THORChain use real gradients matching their source SVGs. THORChain's symbol is black, per the app's own `swap_provider_thorchain.xml` rather than the web repo's `networks/thor.svg`, which says white.

**THORChain set as the default icon** — 2026-09-07

Manifest aliases and `AppIconService` fallbacks both changed; a mismatch between them fails silently.

**Plflag and Sinwar removed from the picker** — 2026-09-06

Marked `isDeprecated` rather than deleted, so the manifest aliases still resolve for anyone with one active. `setAppIcon` migrates them to the default. Deleting the aliases outright would make the launcher icon vanish until reinstall.

**`IconSizes`: central icon size constants** — 2026-09-06, expanded 2026-09-08

Split by context — `Token`, `MarketRow`, `TransactionRow`, `TransactionRowContainer`, `TransactionRowPaired`, `TransactionInfo`, `CoinInfoRow`, `Provider`, `Compact`, `Badge`. Upstream hardcoded these at every call site.

**Token icons 32 → 48dp** across balance, swap, market lists, transaction rows, transaction info and confirmations. The transaction row's container grew 42 → 58dp, since a fixed container clips larger icons.

**Text 10% larger.** `ComposeAppTheme` pins `fontScale`, which is why the app ignores the system font size setting. Raised from `1f` to `1.1f`.

**`TransactionCell` fixed height → `heightIn`.** A fixed `72.dp` doesn't grow with the font scale, so text was clipped by the row divider.

**Secondary text contrast raised.** `andy` was `Steel` (#B3B3B3) on white and `Smoke` (#4B4B4B) on near-black, both near the legibility threshold. Now `GreyDark` (#63636A) and `Steel` respectively.

**Swap page: amount left, token selector right** — matching upstream `12f7f3843`, before the redesign. Included removing a `CenterEnd` Box that pinned the placeholder "0" to the right.

**Fee row defaults to fiat and persists the choice.** Upstream used `remember(valueFiat)` with a `false` initial value, so it started on the token and reset on every re-quote. Now backed by `ILocalStorage.feeDisplayInFiat`.

**Fixed: unverified tokens showed a blank icon.** The swap token selector and EIP-20 approve screen called `CoinImage`'s *coin* overload while holding a `Token`, discarding `token.iconPlaceholder` — the blockchain badge (BNB/BEP20, ETH/ERC20). Three call sites in `SelectSwapCoinDialogScreen`, two in `Eip20ApproveConfirmPage`.

## Build and distribution

**WalletConnect enabled in the F-Droid flavour** — 2026-08-25 · `5e2444c67`

Not gated by `FDROID_BUILD` as it appeared, but by module wiring: `:dapp-wallet-connect` was listed for `baseDebug`, `baseRelease` and `ci` only. Without it, `DAppServiceInitializer` never runs and the settings entry hides itself.

**Renamed to Open Swap, applicationId `money.openswap.wallet`** — 2026-08-25 · `9a4418218`, `09840a679`

The applicationId change matters for distribution: signing with a different key under upstream's package name means Android refuses to install alongside a real Unstoppable Wallet.

**Release signing config** — 2026-08-25 · `09840a679`

Reads `keystore.properties` locally, environment variables in CI. Both gitignored. Upstream's committed `test.keystore` has its password in `build.gradle.kts`, so anyone cloning the repo could sign an APK that Android installs as a silent update — unacceptable for public distribution.

**GitHub Actions release workflow** — 2026-08-28 · `9c8f3460a`, `259972863`, `18e79ca3b`

Builds, signs and attaches the APK on `v*` tags. Upstream's six inherited workflows were removed; they referenced secrets that don't exist here and failed on every push.

**Upstream sync `0.50.1`** — 2026-08-28 · `37575b974`

Five commits, all navigation and send-flow stability fixes.

---

# Outstanding

- **Release notes formatting.** `git tag -l --format='%(contents)'` isn't reaching the runner. `fetch-depth: 0` fetches history but may not fetch tag objects — try `git fetch --tags --force` before that step, or a committed `CHANGELOG.md` via `body_path`.
- **Tor and the FX request.** `RetrofitUtils.buildClient` constructs its own `OkHttpClient` rather than taking the app's shared one, so the NGN rate call may bypass `TorManager`. This matters: avoiding third-party exposure is the whole reason FX conversion was chosen over CoinGecko.
- **Two APK variants per release** — `openswap_wallet_fdroid_wc_v<N>.apk` and `openswap_wallet_fdroid_v<N>.apk`. Needs a design decision: a new product flavour, or a Gradle property with two CI runs. Both would share an applicationId, so users couldn't install both side by side.
- **Tax-aware swap quotes.** Quotes use plain constant-product math and ignore transfer taxes, so users must manually raise slippage above a token's sell tax or the swap reverts with `INSUFFICIENT_OUTPUT_AMOUNT`. Fix: simulate via `eth_call` to derive the real expected output. If a tax percentage is ever displayed, present it as a timestamped estimate — taxes vary by direction, size, wallet and time, and honeypots are built to pass simulation and fail on the real transaction.
- **Cross-chain swaps.** PancakeSwap's cross-chain is Across underneath, so integrate Across directly (public API, ERC-7683). Must be a new provider modelled on `BaseThorChainProvider`; `BaseUniswapProvider` is structurally single-chain. Hard part is the two-leg lifecycle — in-flight, refunded, partially completed — which `SendTransactionData.Evm` can't express, holding one call.
- **Batched approve + swap (EIP-7702).** Foundation committed and verified in the ethereum-kit fork: `Authorization`, `AuthorizationSigner`, `RawTransaction.authorizationList`, type-`0x04` signing and encoding. Remaining: choose a delegate contract and confirm deployment per chain (BSC matters most), confirm EIP-7702 activation per chain, widen `SendTransactionData.Evm` to carry multiple calls, include the authorization list in gas estimation, sign the authorization after the nonce is fixed (the `+1` self-delegation rule), and surface the delegation in the UI. A wrapper contract is **not** viable: it makes `msg.sender` a contract, and many tax tokens revert on contract callers.

# Gotchas

- **`compile*` produces no APK.** `assembleFdroidDebug` is required before installing; `adb install` after a `compile` task silently installs the previous build. Cost us several rounds of "the change didn't work".
- **CRLF churn between WSL and PowerShell.** Editing one working tree from both shows thousands of phantom modifications. Fix: `git config core.autocrlf true` in the repo.
- **WSL git has its own identity** — `user.name` and `user.email` must be set separately from Windows git.
- **`base64` in CI needs whitespace stripped.** Secrets pick up stray whitespace and `base64 -d` mistranslates it *silently*, producing a corrupt keystore and a "Tag number over 30 is not supported" failure 13 minutes into the build. Use `tr -d '[:space:]'`, then verify with `keytool -list` so it fails fast.
- **`apksigner` glob** — `$ANDROID_HOME/build-tools/*/apksigner` expands to several paths on CI runners. Use `ls -d ... | sort -V | tail -1`.
- **Validate workflow YAML before pushing.** A 12-minute build is a slow way to find an indentation error. Notepad breaks YAML indentation on paste.
- **JitPack builds on first request.** Gradle often times out waiting. Check `https://jitpack.io/#<user>/<repo>/<hash>` and retry once it's green.
- **`swap.unstoppable/public/` is not authoritative for assets.** Its `networks/thor.svg` has the symbol white where the brand is black, and `flags/ng.svg` is a stylised icon rather than the flag. The app's own drawables are the better reference.
- **The Fee row is a toggle**, not a stacked display — tap the value to switch between coin and fiat.
