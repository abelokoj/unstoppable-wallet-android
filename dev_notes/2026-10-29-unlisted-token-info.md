# Unlisted token information implementation

Approved scope: prevent Coin Info from crashing for tokens missing from MarketKit. Show the token name/symbol and existing icon or placeholder, network, standard, full copyable contract/mint/asset reference, decimals, explorer link and total supply when available. Keep one market-information-unavailable message and the existing wallet typography, colours, spacing and navigation.

Approach: retain the existing market page for catalogue matches; route saved or supplied token metadata to a basic page; keep the existing not-found page when neither source identifies the coin. Match exact coin IDs and network/token identity, never symbols. Extra market providers and catalogue submissions are outside this change.

Implementation:
- [x] Add regression coverage for catalogue, custom-token and missing-token routing, token references and supply response validation.
- [x] Reuse existing detail cells, coin icons, clipboard HUD and explorer helper. Pass known token metadata from the three balance entry points; resolve other entry points centrally.
- [x] Add optional read-only EVM/SPL supply through selected chain providers, with Tor, source-change checks, strict numeric validation and cancellation. Failure leaves basic details usable.
- [x] Translate new copy into every shipped locale, run affected tests and build/install the development APK. Inspect the native result once where device access permits, update CHANGELOG.md and leave further user testing to the user. No signing, new indexer or production publication.

Validation: 383 unit tests passed, all supported locale strings validated, development APK built and installed. User confirmed the former crashing token page opens correctly. The fallback was not visible during the limited final layout check; theme/scaling/RTL and optional live supply remain unverified on the phone. No production publication or broader catalogue coverage change.
