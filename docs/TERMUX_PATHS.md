# Hard-coded `com.termux` paths and how this project handles them

Original prefix: `/data/data/com.termux/files/usr`  ->  ours: `/data/data/com.mimax.files/files/usr`
Length differs (10 vs 15 chars) so patching binaries in place (`sed` on ELF) is NOT possible; everything must be rebuilt.

| Where the path is baked in | Consequence | Handling |
|---|---|---|
| ELF RUNPATH/RPATH and interpreter strings | binaries fail to find libs | rebuild from source with new prefix (bootstrap.yml) |
| Shebangs (`#!/data/data/com.termux/...`) | scripts don't run | termux-packages rewrites `@TERMUX_PREFIX@` at build |
| dpkg/apt config (`Dir::`, `Dpkg::`, sources.list, lists/cache dirs) | apt/pkg read the wrong dirs | rebuilt `apt`/`dpkg`; `TERMUX_REPO_URL` points to our repo |
| terminfo / ncurses default path | `TERM` errors | rebuilt ncurses |
| OpenSSL `OPENSSLDIR`, CA bundle path | HTTPS fails (pkg, git, curl, npm) | rebuilt openssl/ca-certificates |
| Python `sys.prefix`, `sysconfig` data | pip/ext builds wrong | rebuilt python |
| Git exec-path, templates | git subcommands fail | rebuilt git |
| Node.js, npm global prefix | global installs wrong | rebuilt nodejs |
| `termux-exec` (LD_PRELOAD) | exec/shebang handling | preloaded by `TermuxEnv.environment()` when present |
| Env vars (`PREFIX`, `HOME`, `TMPDIR`, `TERMUX_*`) | tools consult them | set in `TermuxEnv.environment()` |
| Android build | `targetSdk` must be 28 to exec from app data on Android 10+ | `app/build.gradle.kts` |
| apt signatures | official keyring does not trust our repo | repo marked `[trusted=yes]` by `BootstrapInstaller` (HTTPS only; TODO: sign) |

This table comes from my knowledge of termux-packages and was NOT verified against the current upstream tree
(no network access in the authoring environment). Check `scripts/properties.sh`, `scripts/Dockerfile`, `build-package.sh`
and `scripts/generate-bootstraps.sh` when running the workflow; option/variable names may have changed.
