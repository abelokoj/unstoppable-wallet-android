# Local Android build workflow

The local Gradle user configuration already enables the build cache and configuration cache. Preserve the checkout's build outputs as well: switching historical versions in the active checkout or creating a new checkout can still require Kotlin/KSP compilation and dex assembly. Keep historical installer repairs separate from the active development checkout.

For repeated local development, reuse the Gradle background process and a consistent memory profile. The commands below use one worker and a shared 4 GB Gradle/Kotlin heap, which is the profile validated on the current development machine. Avoid the repository's combined 8 GB Gradle and 8 GB Kotlin defaults on a 16 GB host.

```powershell
.\gradlew.bat :walletkit-chain-evm:testDebugUnitTest :walletkit:testDebugUnitTest :app:assembleBaseDebug --daemon --max-workers=1 '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

Keep the same checkout, Java toolchain, variant and JVM arguments between iterations. Preserve `.gradle/` and `build/` outputs. Run the tests affected by a change; add a signed release build when preparing a release or reproducing an optimisation-only problem. CI can keep using its single-use process because its runners are disposable.

The cache reuses unchanged task outputs, rather than making edited code free to compile. Cache hits appear as `FROM-CACHE`; unchanged local outputs appear as `UP-TO-DATE`. An unchanged repeat build is a cache check, not a prediction of build time after source edits.