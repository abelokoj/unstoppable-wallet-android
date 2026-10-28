# Release Notes Style

Use LeanType and WMKeyboard as the formatting references for every Open Swap release.

References:

- [LeanType releases](https://github.com/LeanBitLab/LeanType/releases).
- [WMKeyboard 0.6.4 release notes](https://github.com/wasi-master/wmkeyboard/releases/tag/v0.6.4).

Combine LeanType's clear change categories with WMKeyboard's short opening summary and feature-area grouping when a release is large. Describe what users can now do, where they will find it, and the behavior a fix corrects. Use real issue links where useful; do not invent them. Choose headings that suit the actual release instead of reproducing every reference section.

Start with a version heading and a short one-sentence summary when it helps readers understand the release. Organize user-visible changes under these headings, including only headings that have content:

- `## 🔄 Updated` for upstream updates, behavior changes and reorganized flows.
- `## ✨ Added` for new features.
- `## 🐛 Fixed` for corrected user-facing problems.
- `## ⚠️ Notes` for material limits, compatibility requirements, fees or financial-risk disclosures that readers need before using a feature.

Write concise bullets with a clear feature name followed by what changed for the user. Lead with benefits and observable behavior. Keep implementation details, test counts, device details, internal tools, commit history and developer-only explanations out of public notes.

For swaps, bridges, approvals and other financial features, state material fees, availability limits, trust assumptions and untested flows plainly. Do not present a route rating as a security guarantee. Include APK variants or sizes only when the release contains a meaningful choice between artifacts.

Preserve previous release sections in `RELEASE_NOTES.md`. Keep public notes free of model attribution, private data and em dashes.

When creating an annotated release tag from Markdown, use `git tag -a --cleanup=verbatim -F <notes-file> <tag>`. Default cleanup strips lines beginning with #, including release headings. After a release workflow publishes or reruns, compare the live body with the prepared version section and restore that body if needed without moving an existing tag.
