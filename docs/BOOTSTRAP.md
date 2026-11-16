# Termux bootstrap for Mi_files

Chosen approach: **rebuild Termux for `com.mimax.files`** (package name unchanged).
See `docs/TERMUX_PATHS.md` for why, and `.github/workflows/bootstrap.yml` for the (unverified) pipeline.

Outputs: release `bootstrap` with `bootstrap-aarch64.zip`, `bootstrap-arm.zip`, `bootstrap-x86_64.zip`
(x86_64 is for emulator CI only), plus apt repo at https://mmgg3yn-png.github.io/Mi_files/apt (branch gh-pages; enable GitHub Pages).
`build.yml` downloads the zips into `app/src/main/assets/` before assembling the APK.
