# Status (updated)

**The project is NOT complete.** The authoring environment had no network, Gradle, Kotlin compiler, Android SDK, Docker or emulator,
so *nothing below has been compiled or executed*. Required acceptance tests are PENDING until you run them (steps at the bottom).

## Acceptance tests requested
| # | Test | Result |
|---|---|---|
| 1 | Gradle build succeeds (`Build APK` workflow) | NOT RUN |
| 2 | Real interactive shell starts in the Terminal screen | NOT RUN |
| 3 | `apt` / `pkg update` against our repo | NOT RUN (repo not built yet) |
| 4 | `pkg install python git` and run them | NOT RUN |
| 5 | Node.js / npm / OpenSSH / Zsh run | NOT RUN |
| 6 | Sessions survive going Home | NOT RUN (implemented via foreground service) |
| 7 | HOME and packages persist after app restart | NOT RUN (stored in filesDir; test `dataPersistsInHome` exists) |
| 8 | ARM32 / ARM64 | NOT RUN. Emulator CI covers x86_64 only; ARM needs a real device (Settings > self-test) |

## Implemented since last version (all unverified)
- Environment: x86_64 arch for CI, `TERMUX__*`/`TERMUX_APP__*` vars, conditional `LD_PRELOAD` termux-exec.
- `SelfTest` (real commands) + Settings button; instrumented tests `ShellSmokeTest` (bash, prefix, apt, pkg update, python+git, node+ssh+zsh, persistence).
- Workflows: `bootstrap.yml` (source rebuild for new prefix, apt repo on gh-pages, release `bootstrap`), `test.yml` (x86_64 emulator), `build.yml`.
- `docs/TERMUX_PATHS.md`: hard-coded path inventory.

## Known risks / unknowns
- `bootstrap.yml` is a best-effort recipe from memory of termux-packages; variable names, builder image tag, `termux-apt-repo` usage and
  `generate-bootstraps.sh` options may differ. It is a multi-hour build and may exceed free runner disk (python/nodejs). Expect iterations.
- JitPack coordinates `com.github.termux.termux-app:terminal-{emulator,view}:v0.118.0` and the `TerminalViewClient` /
  `TerminalSessionClient` signatures were written from memory; compile errors are likely until checked.
- Apt repo is unsigned (`[trusted=yes]`).
- Session persistence = process stays alive via foreground service; sessions do NOT survive Android killing the process.
- targetSdk 28 => not publishable on Google Play.

## Still TODO (features)
SAF browsing; ARSC decode; DEX disassembly; APK repack/sign; boot/super repack; logo.bin editing; ext4/erofs browsing.

## How to run the real tests
1. Push to GitHub; run **Build Bootstrap** (hours). Fix workflow errors using its logs.
2. Run **Build APK**; install the APK on an ARM phone; Terminal screen should install the bootstrap and show a prompt.
3. Settings > "Run Termux self-test" and `pkg install python git nodejs openssh zsh` on the phone.
4. Run **Emulator smoke test** for automated x86_64 results.
Send me the logs of any failing step and I will fix them.
