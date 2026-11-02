# Kotlin-only PancakeSwap quote engine

Goal: Replace the newly introduced JavaScript/WebView AMM engine with Kotlin while retaining dapp-aligned routes, existing safety/privacy/signing, the sharper Original and both F-Droid 0.53.2 (189) APKs. No push, installation or funded swap.

Implementation: Reuse the native planner, pool catalog, JSON validators and RPC transport. Port exact V2/V3/Stable/Infinity arithmetic and adaptive graph selection to BigInteger fractions; implement the public read-only quoter encodings in Kotlin. Preserve the public numerical/reference fixtures. Remove the JavaScript assets/runtime, Node release/build requirements and their unused dependencies, retaining the necessary source/license provenance.

Files: native math/pool model and tests; exact fraction/route model; native graph search and tests; native quoter and captured ABI tests; PancakeAmmRouting adapter; Gradle/release cleanup; project policy and changelog.

- [x] Pin shared pool/route interfaces and frozen reference fixture tests before implementing math.
- [x] Independently implement native pool math and native read-only quoter encoding, with dedicated ownership and no parallel Gradle runs.
- [x] Port graph/gas/stream/merge selection and preserve every dapp-core reference output and route.
- [x] Integrate primary single-hop, adaptive and full fallback selection, on-chain verification, budgets and cancellation.
- [x] Remove app JavaScript/WebView assets and Node build requirements, archive the old reference tooling outside app source, retain required licenses.
- [x] Run native regressions and captured five-network quote replays; build both F-Droid variants sequentially with warm caches.
- [x] Verify signatures, packages/version, exact media and absence of JavaScript engine in each APK; deliver distinct files/checksums to Downloads. Update changelog and record any untested device/funded paths.

Constraints: no silent return to the old hub approximation, no loss of pool types or route splitting, no new language/runtime, no added developer fee, no private-key/address exposure, global Tor still honored and fail closed, source spending exact and destinations/refunds unchanged.

Native verification: all 64 focused Android/JUnit tests pass with zero failures, errors or skips. The stale Relay-rating test was corrected to the previously requested Good rating; app ratings are unchanged. The frozen five-network quotes and the public-SDK full-fallback split vector match exactly. Both native F-Droid installers and checksum sidecars are verified in computer Downloads; release vital lint, retained signatures/packages/version, native quote classes, removed JavaScript engine and all 18 media assets pass. No installation, funded transaction or GitHub publication was performed.
