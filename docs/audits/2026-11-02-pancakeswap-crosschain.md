# PancakeSwap cross-chain quote audit

Audit date: November 2, 2026, America/New_York. Findings describe the current Open Swap source at `c6a3f0218be85e584e7ff5d6c42124bbdb399137`, with no cross-chain runtime changes made during this audit.

## Answer

Open Swap uses genuine PancakeSwap cross-chain infrastructure: the PancakeSwap gateway, PancakeSwap XChainSender/AcrossAdapter contracts for EVM swaps, and its Relay-backed interface for Solana routes. It does **not** reproduce the current dapp's complete quote construction and route selection. There are confirmed differences that can produce a lower displayed quote and miss better routes. Matching the bridge provider is insufficient to establish price parity.

The strongest finding for the supplied BSC-to-Ethereum example is different handling of expected output and slippage-protected source output. The current dapp preserves an expected quote separately from execution commands. Open Swap builds its downstream quote from the protected source minimum. The two displayed amounts therefore do not necessarily represent the same measure.

No added Open Swap developer/referral basis-point fee was found in the reviewed path. The AMM request sets its swap fee to zero, the response parser requires zero application-fee fields, and the command validator rejects unsupported payment/fee operations. Pool fees, bridge/relayer fees and gas remain. This is not an independent audit of external bridge contracts or proof that provider services have no revenue arrangements.

## Supplied comparison

- Input: 0.493441 native BNB on BNB Smart Chain.
- Destination: native ETH on Ethereum.
- Open Swap displayed: 0.151993268 ETH.
- PancakeSwap displayed: 0.152730 ETH.
- Difference using the rounded displays: 0.000736732 ETH, approximately 0.4824% of the website figure.

The images do not establish exact capture times, quote/request IDs, selected bridge assets, AMM paths, slippage values, gas-adjustment settings or guaranteed minimums. Consequently, the entire screenshot gap cannot be attributed to one cause. Its size is compatible with a roughly half-percent source haircut, but that alone does not prove which Open Swap route was selected. The website's displayed expected amount is not a guarantee of final settlement.

## Findings, ordered by relevance

### A1. High price-parity issue: protected source output is used as the displayed downstream quote

**Confirmed.** `PancakeCrossChainProvider.kt:213` creates the source AMM leg with a slippage-adjusted minimum; line 219 uses that minimum as the bridge input. `EvmPlan.output` at line 170 then exposes the destination quote/bridge output computed from that reduced input. `fetchQuote` at line 74 presents it as the route output. The source reduction is `floor(output * 10000 / (10000 + legSlippageBps))` in `PancakeRoutePlanner.kt:32`.

The current dapp's cross-chain strategy retains separate expected and protected command chains. Its source-swap-then-bridge strategy requests metadata for the expected source output and again for the protected output. Its displayed final trade output comes from the expected chain; protected commands are retained separately. The swap-bridge-swap strategy also keeps these separate.

This discrepancy is not a developer fee. It is also not safe to fix by merely increasing the number shown in Open Swap: the local transaction builder binds the actual bridge input to the protected amount. Expected display, minimum received, actual bridge funding and any source-chain surplus/refund must be reconciled together.

**Read-only reproduction.** A smaller BSC WETH-to-Ethereum-native-ETH probe returned two supported bridge responses with the same `quoteTimestamp`, isolating this calculation from a changed quote timestamp:

- Expected raw input: 1,000,000,000,000,000; quoted output: 982,082,431,567,369.
- Input after the existing one-percent formula: 990,099,009,900,990; quoted output: 972,190,813,287,007.

Both assets have 18 decimals. This smaller bridge-only example is not a replay of the user's complete BNB swap. It demonstrates the downward effect of the input reduction, not an executed payment or the exact screenshot cause.

**Required correction:** model expected and protected plans explicitly, preserve the existing signing and input-budget checks, and test the amounts actually encoded on both sides before claiming dapp-equivalent output.

### A2. Medium price-parity issue: the AMM quote engine differs from the dapp

**Confirmed architecture difference; exact effect on the supplied trade is unproven.** Open Swap calls `hub-gateway.pancakeswap.com/v2/quote` with at most three splits and a fixed protocol list (`PancakeCrossChainApi.kt:31`, `PancakeCrossChainProvider.kt:247`). The live cross-chain dapp explicitly disables its general aggregator and proprietary-AMM route strategies inside cross-chain AMM legs, then uses its configured single-hop/routing-SDK/full smart-router strategies. Its split search and pool discovery differ.

There is therefore no basis to describe the app as using the exact same full optimizer simply because the bridge gateway matches. The same source token, amount and bridge can still produce different AMM paths.

**Important negative result:** simultaneous three-split and ten-split PancakeSwap-only API probes returned exactly the same output, 153135942144651902 raw BSC WETH, at block 131194852. Increasing the split cap alone did not improve this sample. This comparison is an AMM leg, not a complete cross-chain quote.

**Required correction:** document and align the supported dapp routing settings/solver behavior, then benchmark complete plans at matched inputs, quote times and minimum-output assumptions. Do not assume a larger split cap is a complete fix.

### A3. Medium price-selection bug: later bridge candidates are never compared after one batch succeeds

**Confirmed, but not the explanation for this BSC-to-Ethereum snapshot.** `PancakeCrossChainProvider.kt:200` checks candidates in batches of three and line 205 immediately returns the best in the first successful batch. `PancakeRoutePlanner.kt:20` sorts by swap count, not total output or net cost. A later candidate that pays more can be ignored.

The downloaded catalogue contains more than three distinct candidates for 13 of the 20 directed pairs among the five supported EVM networks. BSC-to-Ethereum has exactly three, so all three fit in its first batch. A deterministic control-flow example with outputs 100, 99, 98, 110 returns 100 under the existing policy even though 110 is available later. This example is synthetic, not a claim that a particular live omitted route paid that amount.

**Required correction:** keep bounded concurrency but compare all permitted candidates within a clearly defined quote budget, with cancellation and timeouts handled explicitly. Compare a consistent output measure across routes, including the effect of differing slippage-leg counts.

### A4. Medium quote-consistency issue: preview uses a fixed one-percent slippage and confirmation can use another value

**Confirmed.** `fetchQuote` calls the planner with `IMultiSwapProvider.DEFAULT_SLIPPAGE` (`PancakeCrossChainProvider.kt:67`), defined as one percent (`IMultiSwapProvider.kt:90`). Confirmation rebuilds the plan using its supplied slippage at provider line 83, called from `SwapConfirmViewModel.kt:350`. The preview interface has no slippage argument.

Because protected source output is currently part of the displayed quote, changing slippage can change the quoted amount or even the route when the user proceeds. The dapp currently advertises Auto 0.50% on its initial page, but the screenshots do not establish the user's actual setting. A matched comparison must use known settings, not infer them from defaults.

**Required correction:** remove this slippage dependency from the expected-output comparison, retain the chosen protection separately, and ensure preview and confirmation explain any real repricing. Avoid changing the app-wide default as a substitute for fixing this provider.

### A5. Local Infinity StableSwap fallback does not compete with valid hub quotes

**Confirmed.** `PancakeCrossChainProvider.kt:257` uses the local Infinity StableSwap search only when the ordinary hub quote is absent. A valid but worse classic quote prevents the stable candidate from competing. The local stable finder does compare its own registered candidates (`PancakeInfinityStableQuote.kt:64`), but that comparison is unreachable whenever the hub succeeds.

**Correction recorded during implementation on November 2, 2026:** the default context contains Infinity StableSwap support, but the active swap provider overrides it to disabled. The earlier statement that this protocol was active in the current cross-chain dapp was inaccurate. This remains a local fallback difference, not evidence of a missing active dapp route. Open Swap's hub request omits this protocol. Simply adding `infinityStable` to the current public hub request is not a verified fix: the live endpoint returned HTTP 400, code 40101, for that protocol-list probe.

**Implementation ruling:** match the active dapp configuration rather than enabling an inactive protocol as a price-parity fix. Preserve canonical factory verification and the hook restrictions; do not accept arbitrary hooks to chase a price.

## Coverage and diagnostics limitations

- Hook-free Infinity CL/Bin, selected registered BSC stable hooks and supported classic pools are validated locally. Unknown hooks and custom hook data fail closed. This can exclude routes the dapp supports; it is an intentional security boundary that needs independently reviewed expansion.
- Candidate failures are mostly discarded, and the whole provider has a 30-second budget. A price may come from the surviving subset or the route may disappear. Do not describe such a result as the best possible route across every supported plan.
- Quotes/final data do not preserve enough user-visible bridge-path and component-cost provenance to explain this discrepancy from the screenshots alone. Future diagnostics should use redacted structured reasons and public route identifiers, never wallet inventories, credentials or sensitive RPC URLs.
- The route's Excellent label is a manually configured rating previously requested by the user, not a result of this audit or proof of price parity.

## Security and privacy checks

Reviewed the quote, route planning, AMM parsing, EVM construction/command-validation boundary, NON-EVM quote/deposit model and transport. The following controls are present and should remain when correcting the planner:

- Canonical XChainSender/AcrossAdapter addresses match PancakeSwap's published deployment list for the supported EVM networks; the Solana leg uses the PancakeSwap NON-EVM interface rather than silently sending every EVM route through Relay.
- AMM responses require zero application-fee fields, exact requested input/assets/chain, internally consistent route amounts and PancakeSwap pool provenance.
- EVM construction fixes input budgets, canonical adapters, recipient/refund ownership, minimum delivery and native value. AMM routers require an on-chain whitelist check. Unsupported payment, approval and hook commands are rejected, and outer calldata is reconstructed locally.
- Ordinary wallet approvals and the existing local signing path are retained. No borrowed Across production API key or developer payout is used by this route.
- Network clients honor the app's Tor preference and fail closed if enabled Tor is unavailable. Redirects and request-body logging are disabled. Initial quotes use fixed placeholders where addresses are required; final preparation discloses sender/recipient to the hosted service. Without Tor, the service sees the connection IP.
- Relay-backed destination commitments and hosted status/refund reports still require provider trust. The source intent identifier alone does not independently prove destination execution. This is an existing documented limitation, not a newly demonstrated exploit.

No new wallet-fund diversion exploit was demonstrated in this review. That statement is limited to the reviewed source and read-only evidence, and is not a guarantee about the external contracts, relayers, hosted services or every token behavior.

## Validation and reproducibility

- All 23 existing focused PancakeSwap tests passed: seven bridge, six calldata, four planner, three AMM parser, two NON-EVM and one Infinity StableSwap test. Zero failures, errors or skips. These tests do not establish live price parity or settlement safety.
- The 29 relevant source/test/fixture files in the cached test checkout match the main checkout byte for byte. Their checksums are preserved.
- Saved current public dapp HTML, 30 downloaded public scripts with checksums, the live 145-row route catalogue, unsigned quote responses, test counts and deterministic audit assertions. No public scripts were executed.
- The source and contract repositories referenced by historical PancakeSwap material returned 404 during the audit. Dapp behavior was inspected in the currently served official JavaScript bundles, not claimed to come from a current public source checkout.
- The full-size direct WETH bridge probe was rejected by current liquidity limits. This prevents a faithful live replay of the earlier successful screenshot quote. Smaller supported responses establish the arithmetic finding only.
- No user wallet address, key, inventory, signed order or transaction was used. No funded swap, destination receipt, refund, device playback or Tor traffic capture was tested. No APK was built, installed or published for this audit.

Run the saved `reproduce_quote_audit.py` beside its JSON evidence to check the recorded arithmetic, source anchors, catalogue counts, counterexample and test totals. Live snapshots are observations at different times unless their common block/timestamp is explicitly noted.

## Recommended next work

1. Correct expected versus protected planning and retain amount/recipient/security checks through transaction construction.
2. Align or explicitly document the AMM solver and supported pool settings; make stable candidates compete.
3. Compare all permitted bridge candidates within a bounded budget using a consistent quote measure.
4. Add regressions for all four EVM patterns, zero/one/two swap legs, user slippage changes, later-batch winners, classic-versus-stable competition and source-surplus handling.
5. Run synchronized read-only BNB-to-ETH comparisons and both Solana directions; show expected output, minimum received, bridge path and costs separately. Funded settlement/refund checks require a separate, explicitly authorized test.

This audit changes documentation only. It does not alter the existing signing flow, routes, ratings, fees, version/build number, animation work or public release.

## Primary references

- [PancakeSwap cross-chain mechanics and fees](https://docs.pancakeswap.finance/trade/crosschain-swaps)
- [Published XChainSender and AcrossAdapter deployments](https://developer.pancakeswap.finance/contracts/crosschain/addresses)
- [Current official swap interface](https://pancakeswap.finance/swap)
- [Observed dapp cross-chain strategy bundle](https://pancakeswap.finance/_next/static/immutable/chunks/1h4i5nf9fqbcw.js), principally character offsets 100939 through 115950 in the decoded UTF-8 snapshot. Module names in the minified bundle are not a stable public API.
- [Observed dapp gateway client bundle](https://pancakeswap.finance/_next/static/immutable/chunks/0lkvn2h3clcc4.js), principally the route/metadata/calldata clients around offsets 221000 through 228500. Exact downloaded files are pinned by the evidence manifest.

Recorded at 2026-11-02T01:50:31-05:00. Local, uncommitted audit.

## Follow-up: complete planner API/SDK verification, 2026-11-02T02:03:59-05:00

Checked the official SDK catalogue, Smart Router documentation, Unified Swap API overview/endpoints, public package metadata/type exports and the current dapp. The page still identifies build `_j9eHfua-CCKO5KzyxAiz`, matching the audited immutable frontend snapshot.

- A documented production Unified Swap API exists at `https://swap.pancakeswap.com`. A public `GET /v1/quote` same-chain BNB-to-CAKE request succeeded without credentials using `sources=agg` and zero application fee. The documented request has one `chainId`, and provides aggregator/PancakeSwap X execution. It does not document a source/destination-chain bridge planner. Its availability does not resolve full cross-chain parity.
- Inspected the published declaration files for Smart Router 7.8.0, Solana Core 0.2.1, swap-sdk-evm 1.3.0, swap-sdk-solana 1.1.10 and the legacy sdk 5.9.3. Their checked exports do not expose the dapp's complete source-swap/bridge/destination-swap planner. Registry searches and checks for plausible dedicated cross-chain SDK names did not locate one. This is a bounded search result, not proof that no private or differently named package exists.
- The existing gateway metadata endpoint returned `supported=false` with `Route is not enabled.` for the complete native BNB-on-BSC to native-ETH-on-Ethereum request. The already-inspected dapp constructs the AMM and bridge commands around that endpoint; the metadata call is not a substitute for its client planner.
- Standard public gateway OpenAPI/Swagger/docs paths returned 404. That alone does not establish whether partner documentation exists. The older routing host referenced in the official AI integration guide could not be reached for either a same-chain control or cross-chain request; no capability conclusion is drawn from those connection errors.
- Conclusion: a supported public one-call complete cross-chain planner was not found. The verified native-app path still requires matching the dapp's client orchestration and AMM routing, preserving transaction guards, and validating expected and protected amounts against actual execution. A private partner API and production support terms require confirmation from PancakeSwap; no contact, key request or external message was sent.

References: [Unified API overview](https://developer.pancakeswap.finance/contracts/unified-swap-api/overview), [documented endpoints](https://developer.pancakeswap.finance/contracts/unified-swap-api/endpoints), [Smart Router](https://developer.pancakeswap.finance/sdks/smart-router), [official SDK catalogue](https://developer.pancakeswap.finance/sdks/overview). Raw public snapshots and probe records are saved in the local `pancake-crosschain-api-verification-2026-11-02` evidence folder beside the earlier audit evidence. No user address, signature or transaction was used; no app runtime change, build or publication was performed.


## Local implementation follow-up, November 2, 2026

The audit above records the earlier source baseline. The current uncommitted provider now uses a dedicated four-pattern planner and a pinned local AMM bundle instead of the hub approximation. See dev_tools/pancake-routing/README.md for the reproducible build, exact tested scope and limitations.

Implementation checks corrected two initial assumptions. Active Infinity StableSwap is disabled by the dapp's provider override. Also, the gateway encodes trade.outputAmount as the swap minimum directly; it does not reduce that amount again using the separate slippage field. The original protected command amount is retained. Expected quotes are calculated independently.

Verified dispatcher source bridges the actual available source-swap balance and scales the bridge output proportionally. A live unsigned three-command response still uses fixed inner destination swap amounts even when its outer prefunding is CONTRACT_BALANCE. Open Swap preserves exact destination prefunding and the adapter's excess-token refund behavior; it does not forward surplus into a router without a proven sweep. This is an intentional retained security boundary.

Unsigned AMM reads and packaged-asset replays cover all five supported EVM networks; seven frozen graph-routing vectors match the saved dapp core. Both Solana directions retain existing gateway/signing checks and passing fixtures. Wallet addresses remain placeholders during preview, so Solana rent/account costs can change at confirmation. No funded settlement or native-device WebView/Tor-lifecycle test has been performed in this batch. Different-time quotes, unreviewed hooks, an evolving dapp and the observed pool endpoint still limit absolute parity claims.


## Kotlin rewrite follow-up, November 2, 2026

The newly introduced JavaScript engine described in the local follow-up above has been replaced by Kotlin at the user's request. Public SDK arithmetic and selection semantics are ported natively; no AMM WebView or Node build requirement remains. All five frozen AMM quote replays match exact outputs and routes, with native math, graph, ABI and cancellation regressions. The four-pattern planner and native signing/security checks are retained. See `docs/pancake-native-routing.md` for the current architecture, GPL-3.0 provenance and limitations. New Kotlin-only F-Droid installers are still being prepared; earlier local JavaScript installers are historical artifacts. No source or APK has been published for this rewrite.

Installer completion recorded 2026-11-02T09:38:30-05:00: both Kotlin-only F-Droid 0.53.2 (189) APKs and checksum sidecars are verified in computer Downloads. All 64 focused Android/JUnit tests, optimized release compilation and vital lint pass. Signatures, packages/version, native classes, removed JavaScript and exact media are verified. No installation, funded transaction or GitHub publication occurred; device/Tor/settlement limits above remain.
