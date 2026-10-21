# dude.call

A single-purpose, entirely native Android app. Black immersive screen, one giant glossy red 3D arcade button: touch it, release it, call the number you locked in during initial setup. No ads, accounts, analytics, tracking, internet permission, phonebook access, or in-app number editing after setup.

## Features

- First run: `WHO SHOULD I CALL?`, a custom sunken black 3×4 mechanical keypad (`1–9`, `+`, `0`, `X`), international/local formatting, 15-digit limit, hold-to-delete, and a round red **LOCK IN** button.
- Review the number in a softly glowing red frame (tap the frame to edit). **LET'S GO!** saves it to local SharedPreferences once; uninstall/reinstall is required to choose another number. Android backups and device-transfer recovery of the number are disabled.
- Immersive black home screen with a large blank glossy arcade-red (`#F20D17`) dome, glossy black base, crisp curved upper-left highlight, 30° viewpoint, deep mechanical travel, bounce, and held neon-red edge glow.
- Tap-and-release call with press cancellation when sliding off the red dome, first-pointer-only multitouch, temporary rotation lock while held, and a 0.3-second mechanical release animation.
- Requests `CALL_PHONE` and `READ_PHONE_STATE` on the first call attempt. Call permission alone is enough for dialing. When available, phone state is used to avoid initiating a call on an already active call.
- An accessible permission-recovery panel opens Android app settings, plus READY TO CALL feedback when the user returns with permission.
- Responsive portrait/landscape screens, fullscreen preference, display-cutout safety, reduced-motion accommodation, TalkBack virtual controls and announcements, and black protected Recent Apps/screenshot previews during number setup.
- Custom embedded synthesized WAV effects, played as media without requesting audio focus, automatically muted while headphones or Bluetooth audio are connected; existing effects fade out over 0.1s on connection.
- GitHub Actions debug APK build. No custom signing keys or secrets necessary.

## Build

Requirements: Java 17, Android SDK platform 35 / build tools 35.0.0, Gradle 8.9.

```sh
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed). To build on GitHub, push this directory to the `main` branch and open **Actions → Build Android APK**. Each run exposes a **dude-call-debug-apk** artifact. GitHub Actions may need to be enabled in the repository's settings.

## Upload to GitHub from Android / Termux

Download the ZIP and the separate `dude-call-push-termux.sh` helper into your phone's Downloads, then in Termux:

```sh
termux-setup-storage
bash ~/storage/downloads/dude-call-push-termux.sh
```

The helper installs Git/GitHub CLI, opens a GitHub browser login if needed, checks that you are logged in as **cr1za**, copies the project into `cr1za/dude.call`, and pushes it to `main` to trigger the APK workflow.

## Permissions and privacy

The **only app permissions** declared are `CALL_PHONE` and `READ_PHONE_STATE` (to detect active calls when available). No phone number is sent to any app-owned server. Android's built-in phone app handles outgoing calls and network errors. The first-launch setup uses Android's `FLAG_SECURE` to hide captured images and Recents thumbnails, released after setup completes.

## Platform details

- Package/application ID: `com.cr1za.dudecall`
- Min SDK 26 (Android 8.0), target SDK 35 (Android 15), compile SDK 35.
- Java 17 and the standard Android SDK only; no extra runtime libraries.
- `CALL_PHONE` must be explicitly granted. Device manufacturers may apply additional permissions, call confirmation UI, audio routing, or multitasking policies that the app cannot override.

## Source structure

- `MainActivity.java` — preferences, permission flow, phone-call launch and rotation/window policy.
- `ArcadeView.java` — Canvas art, setup keypad, touch tracking, animation and accessibility.
- `ArcadeAudio.java` — media sound effects, headphone detection, and fade/mute handling.
- `res/raw/*.wav` — original synthesized mechanical and arcade audio effects.
- `.github/workflows/build-apk.yml` — continuous Android debug APK builds.

This project has been source/XML checked, but compiling an APK locally requires the Android SDK and Gradle; the GitHub workflow is the intended first full compilation check.
