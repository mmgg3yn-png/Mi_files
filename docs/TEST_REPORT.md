# Test report — Mi_files (com.mimax.files)

Date: 2026-11-16. Environment of this review: no network, no Gradle, no Android SDK, no Docker, no emulator/device.
Therefore NO build, NO terminal run and NO package install was executed. Nothing below is claimed beyond what its evidence says.

Legend: PASS = verified with evidence · FAIL = known defect / not implemented · NOT TESTED = needs CI or a real device

| # | Item | Status | Evidence / note |
|---|------|--------|-----------------|
| 0 | applicationId unchanged (com.mimax.files) | PASS | single declaration in app/build.gradle.kts; grep output |
| 0 | Workflow YAML syntax (build/bootstrap/test) | PASS | python yaml.safe_load OK (syntax only, not behavior) |
| 0 | Kotlin brace/paren balance, TerminalActivity declared in manifest | PASS | static script (NOT a compile) |
| 1 | Real Termux bootstrap ARM32 + ARM64 | NOT TESTED | bootstrap.yml never executed; written from memory of termux-packages |
| 2 | Bootstrap bundled into APK | FAIL→FIXED IN CODE, NOT TESTED | build.yml now fails if aarch64/arm zips are missing/corrupt/lack bin/bash and verifies them inside the built APK with `unzip -l` + aapt2. Never run. |
| 3 | TerminalSession + bash inside the app | NOT TESTED | needs APK + bootstrap on device/emulator |
| 4 | pkg update; install Python and Git | NOT TESTED | ShellSmokeTest exists (instrumented), never run |
| 5 | PREFIX/HOME/TMPDIR vs package build prefix | FAIL→FIXED IN CODE, NOT TESTED | Bug found: Context.filesDir gives /data/user/0/<pkg>/files, packages are built for /data/data/<pkg>/files/usr. TermuxEnv now uses the literal /data/data path (fallback to filesDir if not the same dir). ShellSmokeTest no longer accepts the /data/user/0 variant. |
| 6 | Bootstrap build workflow end-to-end | NOT TESTED | still unverified recipe; expect iterations (builder image, properties.sh variables, generate-bootstraps options, runner disk) |
| 7 | Signed apt repo + package integrity | FAIL | not implemented; BootstrapInstaller still writes [trusted=yes] |
| 8 | SAF in file manager | FAIL | not implemented; FileManagerActivity is 100% java.io.File based (needs a File/DocumentFile abstraction) |
| 9 | LICENSE (GPLv3) | PASS | LICENSE = full GPL-3.0 text (674 lines), README updated. Per-file headers / dependency NOTICE not added. |
| 10 | GitHub Actions run + Gradle/Kotlin errors fixed | NOT TESTED | no Gradle wrapper in repo; JitPack coordinates and TerminalViewClient/TerminalSessionClient signatures unverified |

## Changes made in this pass
- LICENSE added; README license line fixed.
- build.yml: bootstrap required for aarch64 and arm, zip integrity check, APK content check, package-name check, pipefail-safe Gradle log upload.
- TermuxEnv: literal /data/data/<pkg>/files base so PREFIX/HOME/TMPDIR match the build prefix.
- ShellSmokeTest: strict PREFIX, HOME and TMPDIR assertions.

## Next required runs (in order)
1. Run "Build Bootstrap" (bootstrap.yml) and send the logs of every failing step.
2. Run "Build APK"; send gradle-build.log (Gradle/Kotlin errors will be fixed from it).
3. Run "Emulator smoke test" (x86_64 only) and install the APK on real ARM64 and ARM32 devices; run Settings > self-test.
Only after 1–3 pass: signed apt repo, then SAF.
