# Camera Analyzer

A free, offline Android app that inspects installed APK metadata, DEX strings, and native library names for static indicators of camera implementations. It requires no account, license, ads, analytics, backend, or network access. Package name: `tech.cameraanalyzer`.

## What results mean

The app reports possible Camera1, Camera2, CameraX, WebRTC, and native camera indicators found in APKs. A match is **not proof that the code is reachable or used at runtime**, and no match is not proof the app has no camera implementation. Obfuscation, dynamic loading, native code, and inaccessible split APKs can affect results. Camera2 references alone do not establish Privacycam compatibility. The app performs no hooks or changes to other apps or their data.

Deep Analysis requests `su` package inventory only after the user taps the button. It is read-only and does not grant access to private app data or guarantee extra APK inspection.

## Device support and privacy

Minimum Android 9 (API 28); target API 36. Results are analyzed locally. `QUERY_ALL_PACKAGES` is used for the sideloaded diagnostic use case and is subject to Google Play policy restrictions. The app does not send scan data anywhere.

## Owner's phone checklist

1. Install the Debug APK from the GitHub Actions artifact and launch Camera Analyzer.
2. Tap Analyze and confirm installed apps appear; use the filter and expand results for evidence.
3. Compare classifications with apps you recognize, keeping the static-analysis limitations above in mind.
4. If the device has root, optionally tap Deep Analysis and approve/deny the `su` prompt; the app should still work without approval.
5. Save JSON and CSV through Android's document picker and open the saved files.
6. Note launch failures, crashes, scan duration, and any unexpected classifications.

Physical-device testing has not been performed. CI checks are listed on the workflow run and include Kotlin compilation, unit tests, APK assembly, lint, and APK package metadata verification. The Release APK is unsigned and is not install-ready; use Debug for manual testing.

## Reproducible build

GitHub Actions runs `:app:testDebugUnitTest`, `:app:assembleDebug`, `:app:assembleRelease`, and `:app:lintDebug`, then uploads `CameraAnalyzer-debug.apk` and `CameraAnalyzer-release-unsigned.apk`.
