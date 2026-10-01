# Life Assistant 1.7.1 — Android fullscreen and adaptive layout

This is an Android-focused update (version code 15). Existing Windows 1.7.0
installers remain usable; no new Windows installer is distributed with this patch.
The shared source/build version is kept consistent for future builds.

## Changes

- Hide the status bar (clock, battery, notification icons) while the app owns the
  window. Edge swiping can temporarily reveal system information. Bottom system
  navigation is not hidden.
- Replace the black status strip with continuous app-header background. Keep actual
  camera-cutout clearance, including in landscape, rather than subtracting a fixed
  amount from the safe inset.
- Reapply the policy after focus returns and in app-owned Compose dialogs and
  date/time pickers. External Google/authentication/permission screens remain
  controlled by Android or their owning app.
- Apply only the actual overlapping camera/navigation/keyboard insets to each
  foldable pane; do not duplicate top and bottom padding across both halves.
- When a keyboard covers the lower tabletop pane, move editing to the visible
  upper pane. Retain movable/saveable editor state across posture changes.
- Android 8–10 uses a visible-window-frame keyboard fallback: legacy fullscreen
  flags otherwise prevent resize, while status-visibility flags alone can expose
  the status bar when the keyboard opens. Android 11+ uses standard IME insets.
- Notes folder/name/deletion dialogs now share the scrollable, hinge-safe dialog
  implementation used by the other modules.

## Compatibility and data

The minimum version is still **Android 8.0 / API 26**; target SDK is 36.
Layout follows available window size and reported folding/cutout information,
not model names. This covers conventional phones, tablets, tall/narrow screens,
landscape, small windows and folding postures as an implementation target.

Samsung Galaxy Z Flip7 inner-screen proportions (21:9) and a small near-square
window were exercised through emulator resizing. No physical Flip7, Fold, or
other manufacturer's device was tested. Samsung decides which apps can launch
on its cover display; an adaptive layout does not itself enable that launcher.
Android TV, Wear OS and Android Auto are not separate supported app targets.
No claim is made that all devices, OEM keyboards or future Android versions have
been individually verified. Floating keyboards and OEM safe-area reporting can
differ from the emulator.

Install over the existing app; do not uninstall or clear storage. Back up first.
The package, signing certificate, launcher aliases, database schema, stored
settings/attachments, Vault keys, backup format and Drive namespace are unchanged.
Google Drive production OAuth configuration remains a separate prerequisite.

## Verification

- Android JVM: 346 tests, debug and release variants; shared cloud: 18 tests;
  Windows JVM: 32 tests. 396 distinct JVM tests, no failures.
- Release lint: no errors; 53 warnings and 3 hints remain.
- Android 8 (API 26) and Android 16 (API 36) emulator runs each passed 10 focused
  instrumentation tests: fullscreen activity/recreation, dialog and picker bars,
  vertical/horizontal fold draft retention, picker posture placement, auxiliary
  top bar, and saved-state restoration. The dialog test additionally opens the
  software keyboard and checks that the input stays above its reported boundary.
- Manual screenshot checks: tall portrait, landscape, small near-square window,
  Todo/Calendar/Notes, and Android 8 fullscreen with a visible software keyboard.
  These are emulator checks, not Samsung One UI or physical-device certification.
- The final signed release APK was installed over the existing app on both
  emulators and passed the four fullscreen/keyboard/picker tests on each.
- A read-only upgrade snapshot across all 15 business tables (one synthetic todo)
  matched before and after the update, SHA-256:
  `213496e281d4240770f374c43830a8977104e2bc01dea4b36abde39497b5b5b2`.
  No personal data was printed or bundled.

For device tests, enable the soft keyboard even when a hardware keyboard is
attached (`settings put secure show_ime_with_hard_keyboard 1` on a test emulator).
Build commands and source/signing instructions remain in the root README.

## References

- [Android immersive mode](https://developer.android.com/develop/ui/views/layout/immersive)
- [Android display cutouts](https://developer.android.com/develop/ui/views/layout/display-cutout)
- [Android official immersive sample](https://github.com/android/platform-samples/blob/main/samples/user-interface/window-insets/src/main/java/com/example/platform/ui/insets/ImmersiveMode.kt)
- [Samsung Galaxy Z Flip7](https://www.samsung.com/us/smartphones/galaxy-z-flip7/specs/)
