# Local Android build workflow

Build the F-Droid distribution only. Use `:app:assembleFdroidDebug` for development and `:app:assembleFdroidRelease` for the signed regular installer. GitHub releases must upload `app/build/outputs/apk/fdroid/release/app-fdroid-release.apk`. Verify the output metadata identifies the F-Droid variant and generated BuildConfig has `FDROID_BUILD = true`. Other app variants are disabled to prevent accidental Base or CI installers.

The local Gradle user configuration already enables the build cache and configuration cache. Preserve the checkout's build outputs as well: switching historical versions in the active checkout or creating a new checkout can still require Kotlin/KSP compilation and dex assembly. Keep historical installer repairs separate from the active development checkout.

For repeated local development, reuse the Gradle background process and a consistent memory profile. The command below uses two workers and a shared 5 GB Gradle/Kotlin heap, matching the current F-Droid build profile. Avoid the repository's combined 8 GB Gradle and 8 GB Kotlin defaults on a 16 GB host.

```powershell
.\gradlew.bat :walletkit-chain-evm:testDebugUnitTest :walletkit:testDebugUnitTest :app:assembleFdroidDebug --daemon --build-cache --parallel --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx5120m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Keep the same checkout, Java toolchain, variant and JVM arguments between iterations. Preserve `.gradle/` and `build/` outputs. Run the tests affected by a change; add a signed release build when preparing a release or reproducing an optimisation-only problem. CI can keep using its single-use process because its runners are disposable.

The cache reuses unchanged task outputs, rather than making edited code free to compile. Cache hits appear as `FROM-CACHE`; unchanged local outputs appear as `UP-TO-DATE`. An unchanged repeat build is a cache check, not a prediction of build time after source edits.
When removing large packaged assets, an incremental APK can retain unused ZIP space even though the removed entries no longer appear in the archive. Repackage from a missing final APK output, keeping the compilation and dependency caches. Compare the APK size with its ZIP entry payload total before release; do not clear the whole build tree just to compact the installer. Preserve earlier testing/release APKs outside the build-output folder before removing an output.
