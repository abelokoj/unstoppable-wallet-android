# Kotlin PancakeSwap cross-chain routing

The wallet quote engine is Kotlin only. It has no JavaScript quote bundle, WebView quote bridge, Node build step or new runtime dependency. Existing Retrofit/OkHttp, coroutines, Gson, web3j and BigInteger are reused.

## Reference and supported routes

The reference is PancakeSwap dapp build `_j9eHfua-CCKO5KzyxAiz`, inspected November 2, 2026, and the pinned public packages listed in `walletkit/src/main/assets/licenses/pancake_routing_licenses.txt`. No supported public API exposing the complete cross-chain planner was found. The provider combines the observed full-pool catalog, native AMM calculations and read-only contract quoters with the existing gateway and signing flow.

The four directed EVM strategies are bridge only, source swap then bridge, bridge then destination swap and swaps on both sides. Both-swap strategies compare eligible bridge currencies before bridge fees; the other strategies follow their corresponding bridge currency. Expected outputs remain separate from protected command amounts. Gateway trade output is the encoded minimum; it does not apply slippage a second time.

AMM selection compares primary single-hop and adaptive graph/split routes after contract verification. If both fail, the public SDK's full split-search strategy is used, with off-chain V2/Stable arithmetic and on-chain V3/Infinity candidate quotes. Gas scoring and integer/fractional rounding follow their respective reference strategies. Selected-route verification requires every split to succeed, not a partial surviving quote. Transaction gas is still estimated by the wallet.

Ethereum, BNB Chain, Base, Arbitrum and zkSync are supported. BNB uses V2, V3, classic StableSwap and reviewed zero-hook Infinity CL/Bin pools. Other existing EVM networks use V2/V3. The reference dapp disables Infinity StableSwap. Unreviewed hooks remain rejected and Linea remains excluded by the existing-network scope. Existing Solana routing and transaction validation are retained.

## Safety and privacy

All RPC reads use the selected chain-verified provider through existing transport and the global Tor setting. Enabled but disconnected Tor fails closed. Changing the network provider or privacy mode during quoting invalidates the result. Each quote has a 120-second deadline, a 256-read ceiling, bounded candidate/response sizes and cancellable graph/stream loops; only two quotes run concurrently.

No signing, broadcast, sender field, arbitrary URL or account override is exposed by the quote engine. Preview sender/recipient addresses remain public placeholders. Real addresses enter only the existing confirmation flow; Solana rent/account-creation costs can change then. With Tor disabled, the network provider sees the connection IP under the existing policy.

Native checks retain exact input budgets, approved routers, canonical pool keys, recipients, fee rejection and refunds. No application developer/referral fee is added. Source surplus is bridged by the verified dispatcher. Exact destination prefunding preserves adapter refunds rather than forwarding excess bridge assets into a router without a proven sweep.

## Tests and build

Run `./gradlew :walletkit:testDebugUnitTest --tests '*Pancake*Test'` before the normal F-Droid builds. The release workflow runs those Kotlin tests before loading signing material. No Node installation is needed.

Native fixtures cover directional math for every active pool type, post-swap state, decimal/fraction rounding, tick boundaries and overflow; seven exact adaptive graph vectors; public-SDK calldata and recorded ABI responses; all five captured complete AMM quote replays; SDK full fallback split selection; invalid replies, hooks and cancellation. Existing planner, command, Solana and security tests remain in place.

Numerical/ABI fixtures are frozen unsigned records or explicitly labeled synthetic public-SDK vectors. They do not contain user wallet inventories, keys or private addresses. The removed JavaScript implementation and development-only reference generators are saved outside the source repository in the local `pancake-kotlin-rewrite/javascript-reference-archive` evidence directory. They are not shipped or required for native builds.

## Limits and provenance

This matches the inspected strategies and tested routes, not every future dapp release or quotes taken at different times. The observed pool endpoint is not a verified stable public integration contract. Unavailable or changed pool/RPC/gateway services can make the route unavailable. Offline replay and unsigned calldata checks do not establish funded settlement, refund execution, real-device lifecycle or live Tor-cancellation behavior.

Pool arithmetic, adapter semantics, quoter encoding and SDK selection were translated into Kotlin with modifications. The upstream distributed LICENSE files say GPL version 3, while some package metadata says MIT. The port conservatively retains the distributed GPL-3.0 text and copyright/provenance in the app assets. Preserve applicable source and license obligations when distributing the combined application.
