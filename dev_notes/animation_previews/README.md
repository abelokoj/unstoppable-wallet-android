# Saved animation previews

The original Tom and Jerry ground-search source remains preserved unchanged in [tom-jerry-ground-search-original.html](tom-jerry-ground-search-original.html). Open it locally for the self-contained preview. All artwork is embedded and needs no external download.

The [saved GIF export](tom-jerry-ground-search-original.gif) remains available for later reuse: 600 by 400 pixels, a 2.4-second infinite loop, a 256-colour palette and binary transparency. The source HTML and GIF remain separate from the compact app resources.

## Current app assets

Revoke Access cycles through Scanner, Together, Tom, Jerry and Original. The four cartoon styles use local light/dark H.264 Constrained Baseline Level 3.0 MP4s at 30 fps, with static lossless WebP first-frame posters. The refined trio retains 1.7x speed and eight-second loops; Original retains 2.4 seconds. Video, stills and the existing scanner media total 2,705,561 packaged bytes.

The crypto scanner JSON files and existing Lottie playback settings remain unchanged. Cartoon playback pauses/releases offscreen, uses stills with system animations disabled, and falls back to the scanner on decoder errors. The large animated cartoon WebPs are no longer packaged in the app. No remote animation fetch is used.

## Preserved sharper MP4 alternative

An exact eight-video backup is saved under computer Downloads / OpenSwap Animation Backups / Sharper MP4 Version - Full Resolution 60fps - 2.5MB Total. Open its index.html to play it. SHA256SUMS.txt, manifest.json, README.md and provenance/encoder records support future retrieval and verification.

That alternative retains full 800 by 500 refined-cartoon resolution, 600 by 400 Original resolution, 60 fps and every source frame. Videos plus the unchanged scanner media total 2,501,235 bytes. The archive was copied without re-encoding and remains an alternative; the app ships the selected conservative 30-fps Baseline version. Universal device playback is not guaranteed, and native phone playback remains untested for this refresh.
