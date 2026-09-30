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

# Unreleased - 2026-09-30T08:22:08-04:00

- Renamed the Default app-font option to `Default (Manrope)` so users can identify the bundled typeface. The selected-value label and font picker share this resource; font rendering and saved preferences are unchanged.
- Recording time for this local, uncommitted change. Validation: confirmed the shared resource mapping and checked patch whitespace. No build or device installation performed for this text-only edit.
- Update recorded at `2026-09-30T08:29:30-04:00`: the Base development APK built successfully, retaining existing Gradle deprecation warnings. This change is being folded into the previous release commit with its original message; earlier changelog entries and public release notes are preserved. No device installation performed in this batch.

---

# Release notes prepared - 2026-09-30T08:04:52-04:00

- Added the approved public notes for version `0.51.2` in `RELEASE_NOTES.md`, covering user-facing improvements and the remaining Across limitation.
- Removed em dashes from the public notes and kept development validation details in this changelog.
- Recording time for local release preparation; commit and publication are pending. Validation: staged patch whitespace check passed; the application build and device checks are recorded below.

---

# Unreleased — 2026-09-30T07:50:50-04:00

Switch Wallet scrolling fix prepared for the next release.

- Replaced the Switch Wallet sheet's regular scrolling column with the same lazy-list and fixed bottom-search overlay structure used by Manage Wallets. The search control no longer participates in scroll layout or shifts while the wallet list is moving.
- Added a list-boundary nested-scroll guard so high-velocity repeated flings are consumed by the wallet list instead of being handed to the draggable modal sheet and making the sheet jerk.
- Kept the original Switch Wallet selection behavior and dismisses search focus when list scrolling begins.
- Validation: the Base development APK compiled, packaged and installed successfully on the connected Samsung SM-S928U. On-device review confirmed stable scrolling, including repeated high-velocity upward swipes.

---

# Unreleased — 2026-09-30T07:09:00-04:00

Release metadata and font-size controls prepared for the next release.

- Changed the Appearance font-size stepper from 5% jumps to 1% increments while preserving the existing 85%–140% range and 110% reset value.
- Made the percentage field directly editable with a numeric keyboard, live updates for valid values, and safe clamping when editing ends.
- Added a fixed bottom search field to the long-press **Switch Wallet** sheet while preserving that sheet's original wallet-switching behavior.
- Increased the app version from `0.51.0 (180)` to `0.51.2 (182)` because `0.51.1` is already present in the GitHub repository.
- Passed the focused font-size persistence, migration and bounds tests, assembled the Base development APK, and installed `money.openswap.wallet.dev` successfully on the connected Samsung SM-S928U. The build retained existing Android/Gradle deprecation and Room index warnings; no release build or store artifact was produced.

---

# Unreleased — 2026-09-29T23:33:34-04:00

Changes prepared for the next release.

## Wallet switching and appearance

- Made the search field in the long-press **Switch Wallet** sheet a fixed bottom control, matching Coin/Wallet Manager behavior; only the wallet list now scrolls behind it.
- Replaced the three-option font-size picker with a compact minus/editable-percentage/plus stepper using 1% increments from 85% to 140%, plus a reset button that restores the previous 110% default. Users may type a percentage directly; incomplete input is retained while editing and out-of-range values are clamped when editing finishes. Existing Small/Default/Large preferences migrate without changing their effective scale.
- Enlarged the Relay provider artwork within the same 32dp provider-icon container so its visible mark is consistent with other route icons.

## Relay risk correction

- Corrected Relay's displayed risk level from **Excellent** to **Fair**. The original value was assigned manually by analogy with other non-custodial bridge providers, not by a formal scoring engine. Relay's current published API behavior and terms permit sanctions/risk screening, blocked routes or wallets, delayed/frozen funds and non-guaranteed recovery, which conflicts with this app's **Excellent** description of no route checks/freezes and automatic refunds.

## Validation

- Passed the wallet and Base development Kotlin compilation, focused font-size persistence/migration/bounds tests, and `:app:assembleBaseDebug`.
- Installed the updated `money.openswap.wallet.dev` APK successfully on the connected Samsung SM-S928U (`R5CX105K2CV`); no release APK was installed or replaced.

---

# Unreleased — 2026-09-29T23:08:17-04:00

Local validation performed for the next release.

- Built the `baseDebug` development variant and verified its application ID as `money.openswap.wallet.dev`.
- Installed that development APK successfully on the newly connected Samsung SM-S928U (`R5CX105K2CV`). No release APK was installed or replaced.

---

# Unreleased — 2026-09-29T23:06:15-04:00

Documentation prepared for the next release.

- Added a root `TODO.md` implementation backlog for Uniswap v4 and PancakeSwap Infinity, including routing architecture, hook and dynamic-fee restrictions, exact approvals and Permit2 safeguards, calldata validation, simulation, Tor fail-closed privacy, UI expectations and release criteria.
- Recorded that existing v2/v3 integrations must remain available and that no Uniswap v5 or PancakeSwap v5 provider should be created without an official production protocol.
- Documentation only; no swap-provider behavior changed in this batch.

---

# Unreleased — 2026-09-29T22:45:40-04:00

Changes prepared for the next release.

## Relay bridge and security

- Replaced Relay's backend-only registration with a direct Relay Quote API v2 provider for supported EVM-to-EVM routes. The provider builds approval and deposit transactions through the wallet's existing EVM approval/signing flow and tracks bridge completion through Relay's status API.
- Added defensive validation for every remote Relay transaction: expected origin chain and sender, valid EVM target and calldata, exact native deposit value or zero ERC-20 call value, decoded standard ERC-20 approval token/spender, and a hard rejection of any non-zero application fee. The client deliberately omits `appFees`, referrer and integrator-fee fields and never sends keys or signing material.
- Live unsigned production quotes succeeded for Base ETH to Optimism ETH and Arbitrum USDC to Base USDC. Both returned ready deposit transactions, the ERC-20 route returned the expected approval step, and Relay reported a zero application fee. No transaction was signed or submitted.
- Across remains registered through the existing backend adapter but is not marked complete: Across's current production Swap API requires a Bearer API key and two-byte integrator ID, neither of which is configured in this repository. Embedding an API secret in the Android client would be unsafe; a direct Across route needs a protected backend credential or another approved architecture.
- Relay now explicitly honors the wallet's existing Tor option. When Tor is enabled and connected, Relay quote and status traffic uses the local Tor HTTP proxy, so Relay sees a Tor exit address instead of the device's direct IP; while Tor is connecting or unavailable, Relay fails closed without a direct-network fallback. Even over Tor, Relay necessarily receives the public wallet address, selected chains/tokens and amount. HTTP logging remains BASIC (method/URL/status only), so request bodies containing wallet and quote data are not logged by the app.

## Wallet and appearance corrections

- Restored the bottom Wallet-tab long press to the original **Switch Wallet** bottom sheet and added the requested search bar inside that sheet. It no longer opens **Manage Wallets**.
- Renamed **APP FONT** to **App Font** and added a reversible persisted **Font Size** selector with Small, Default and Large choices; Default preserves the prior 110% app scale.
- Changed the swap token picker's action to **+ Add token manually**, placed it at the bottom of search results and reused the same full-width primary button component and typography as the swap page's **Enter Amount** button.

## Validation and limitations

- Passed `:walletkit:compileDebugKotlin`, the focused font preference tests and `:app:compileBaseDebugKotlin`; the Base debug build was installed successfully on the connected Samsung SM-A716U1 running Android 13 after the Relay/Tor changes.
- Across cannot return direct routes until production credentials are supplied through a secure mechanism. Relay's API is a third-party trust boundary; the wallet validates the transaction envelope and approval target, but users should still review the confirmation screen and destination before signing.

---

# Unreleased — 2026-09-29T21:59:14-04:00

Changes prepared for the next release.

## Token and wallet selection

- Added contract-address discovery to Coin Manager's bottom search. When the normal catalog has no match, the app searches supported custom-token networks, shows the discovered token and lets the user enable it in place; the existing manual Add Token flow remains available.
- Made the long-press wallet shortcut on the selected bottom Wallet tab open the same searchable wallet switcher used by the top-right wallet button.
- Tightened the swap picker's **Add token manually** control to 12 dp horizontal padding and increased its label to 18 sp while retaining a 48 dp minimum touch target.

## Bridge route test and correction

- Live-tested canonical EVM bridge pairs on the connected device: Ethereum USDC to Base USDC and Ethereum ETH to Base ETH. The configured backend returned Near, LI.FI, Circle CCTP and THORChain routes, but did not return Across or Relay.
- This corrects the earlier entry that described the Across and Relay client registration as a completed integration. Their provider metadata, artwork and uSwap adapters are present locally, but usable integration remains incomplete until the configured backend advertises `ACROSS` and `RELAY`, or dedicated direct provider clients are implemented. No bridge transaction was submitted.

## Validation and limitations

- Passed `:walletkit:compileDebugKotlin` and the focused swap-provider, provider-icon and percentage-selector unit tests.
- Automatic contract discovery requires network access to the relevant chain services. Across and Relay remain unavailable in live route selection with the current backend configuration.

---

# Unreleased — 2026-09-29T21:09:31-04:00

Changes prepared for the next release.

## Token discovery and custom-token catalog

- Added cross-network contract discovery to the swap token picker. When a non-native token is not already enabled, the picker checks the existing custom-token services for supported EVM networks, Solana and the other custom-token-capable chains, presents each matching network, and lets the user toggle the token on without leaving the swap flow.
- Kept the existing manual Add Token flow available as the fallback when automatic discovery returns no match.
- Added an app-wide, offline custom-token catalog. A manually added custom token's query, name, symbol, decimals and image reference are persisted independently of the active wallet, reconstructed locally, and merged into Coin Manager after an account switch. Enabling remains account-specific.
- Refresh the swap selector from wallet updates so tokens enabled automatically or through the manual screen become available immediately on return.

## Appearance

- Added **Default**, **System** and **Pretendard** font choices under Appearance. Default keeps the existing Manrope typography; System uses Android's default family; Pretendard uses the supplied Pretendard Std regular, medium, semibold and bold assets.
- Centralized the selection at the root Compose theme and persisted only a stable font key. The feature can be reversed without touching individual text components by removing the font enum/resources and the single theme parameter. Included the Pretendard license alongside the bundled assets.

## Validation and limitations

- Passed the focused font, contact, swap-percentage and provider-icon unit tests, `:app:compileBaseDebugKotlin`, and Android resource processing.
- Built and installed `app-base-debug.apk` successfully on the connected Samsung SM-A716U1 running Android 13.
- Initial contract discovery requires network access to the relevant chain services; tokens already saved in the custom-token catalog remain available offline. Clearing the app's data also clears this local catalog and the font preference.

---

# Unreleased — 2026-09-29T19:56:50-04:00

Changes prepared for the next release.

## Swap and bridge providers

- Audited Jupiter's quote and commit requests and confirmed they carry no app BPS, platform-fee, referral or fee-account fields. Removed the unused `SWAP_FEE_BPS` build/configuration hook so an application-level BPS fee cannot be reintroduced through that dead path.
- Added Jupiter's official provider artwork so its logo is shown in the route picker.
- Registered Across and Relay as cross-chain providers through the wallet's existing uSwap execution and UUID tracking contract, including provider metadata, supported-source filtering, same-chain Relay handling and official provider artwork.
- Kept the fixed `25% / 50% / 75% / 100%` amount selectors visible for every input token, including native assets that also pay network gas. Selecting the full native balance can still fail at confirmation when the network fee is not covered; the selector now remains available as requested.

## Appearance and contacts

- Removed the fixed splash drawable so Android derives the launch splash icon from the currently enabled launcher alias, matching the icon selected under Appearance.
- Made a saved EVM contact address available across every supported EVM chain. Exact per-chain entries still win when present; filtering, autocomplete, contact selection, transaction lookup, duplicate checks and replacement warnings all use the compatible EVM address otherwise.

## Validation and limitations

- Passed the focused contact, Jupiter-icon and percentage-option unit tests, `:app:compileBaseDebugKotlin`, `:app:processBaseDebugResources`, and `git diff --check`.
- Across and Relay client integration is complete, but live route availability was not exercised with real funds. It depends on the configured uSwap backend advertising the `ACROSS` and `RELAY` adapters for the selected asset pair.

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
- **Across and Relay live-route verification.** The client providers are now registered through the existing uSwap execution/tracking contract. Confirm the production backend advertises both provider IDs and exercise successful, failed and refunded routes before release.
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
