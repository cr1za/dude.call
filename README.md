# dude.call v1.3

Version 1.3.0 adds a compact FLOATING ON/OFF control under the fullscreen call button.

Enable floating mode explicitly using the dark-red overlay permission explanation. On Android 13+, the notification permission is optional. Floating ON creates a 64dp transparent arcade button over other apps. Tap the red surface once to call. Hold 0.45 seconds to drag; releasing saves its free position. Drag into REMOVE to turn floating mode off. Notification STOP also stops and remembers OFF. Switching OFF inside dude.call shows UNDO for three seconds. No automatic enable prompts on launch.

The home-screen widget remains transparent and immediately calls on a single tap. The saved phone number and first-launch workflow remain unchanged. Do not uninstall the app to update: that erases the saved number.

See CHANGELOG.md and QA-v1.3.md for details.

---

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
- GitHub Actions debug APK build. Includes a project debug keystore for consistent debug builds; never use that key for a production release.

## Build

Requirements: Java 17, Android SDK platform 35 / build tools 35.0.0, Gradle 8.9.

```sh
gradle --no-daemon :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (debug-signed). To build on GitHub, push this directory to the `main` branch and open **Actions → Build Android APK**. Each run exposes a **dude-call-debug-apk** artifact. GitHub Actions may need to be enabled in the repository's settings.

## Upload to GitHub from Android / Termux

Download the v1.2 source ZIP and `dude-call-v1.2-over-apps-upload.sh` into your phone's Downloads, then in Termux:

```sh
termux-setup-storage
bash ~/storage/downloads/dude-call-v1.2-over-apps-upload.sh
```

The helper installs Git/GitHub CLI, opens a GitHub browser login if needed, checks that you are logged in as **cr1za**, copies the project into `cr1za/dude.call`, and pushes it to `main` to trigger the APK workflow.

## Permissions and privacy

Phone permissions are `CALL_PHONE` and `READ_PHONE_STATE` (when available). Version 1.2 also declares `SYSTEM_ALERT_WINDOW` and Android foreground-service permissions, solely to show a user-enabled floating button above other apps. No phone number is sent to any app-owned server. Android's built-in phone app handles outgoing calls and network errors. The first-launch setup uses Android's `FLAG_SECURE` to hide captured images and Recents thumbnails, released after setup completes.

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

## Launcher icon (v1.0.1)

The home-screen icon is a glossy red arcade button on a fully transparent RGBA
canvas, including legacy PNG assets and Android 8+ adaptive icon foreground and
transparent background layers. The startup splash screen deliberately keeps its
red background. No monochrome themed icon is provided. Android launchers may
still impose their own masks, icon tiles, shadows or backgrounds; the app cannot
force a particular launcher to show transparency. If an old tile stays after
updating, remove and re-add the home-screen shortcut or refresh the launcher.

## Version 1.1.0 — transparent home-screen button widget

Android's `AppWidgetProvider` + `RemoteViews` APIs let the red arcade button live
**on the home screen**, directly over the user's wallpaper. Unlike an always-on-top
overlay, this **does not** need `SYSTEM_ALERT_WINDOW`, a background service, or
an extra permission. The custom-painted fullscreen app remains unchanged.

After installing/updating the APK, **long-press an empty area of the Android home
screen → Widgets → dude.call button → add**. Move or resize the red button as
wanted. There is no square background drawn by the widget itself; the image is
an RGBA PNG with alpha transparency. Some launchers may still impose widget
padding or unusual resizing rules.

- Tap the widget to call the same stored number using the normal Android call
  permission model. If there is no saved number, it opens the original first-run
  number-entry screen. If CALL_PHONE needs granting, Android requests permission;
  a denial opens the existing in-app OPEN SETTINGS warning.
- A direct widget tap is a click from Android's launcher and triggers a very
  brief **transparent** Activity to request permissions/check call state/launch
  the call. Unlike the fullscreen arcade button, widget views cannot reliably
  animate a finger-down press and bounce through `RemoteViews`. Android also
  owns the eventual in-call screen.
- The on-screen widget stays on the **home screen only**; it does not hover over
  unrelated apps. System-wide floating bubbles need the restricted special
  `SYSTEM_ALERT_WINDOW` permission and extra lifecycle handling.
- Widget taps have a rectangular touch area even where the PNG is transparent:
  Android widgets do not expose per-pixel alpha hit-testing.
- The existing app ID and preferences are unchanged, but **APK updates require
  the SAME signing key**. Earlier GitHub Actions debug builds may have used an
  ephemeral keystore, so Android might reject the first v1.1 update with a
  signature mismatch. To avoid this happening on future builds, v1.1 includes
  a fixed public **debug-only** keystore in `app/debug.keystore`. Never use this
  key for release / Play Store distribution. If Android rejects installation,
  do not uninstall casually: doing so would erase the saved number. Back up
  the number out of band and re-enter it if a reinstall becomes necessary.

Official Android documentation:
- https://developer.android.com/develop/ui/views/appwidgets/overview
- https://developer.android.com/develop/ui/views/appwidgets
- https://developer.android.com/guide/components/activities/secure-bal
- https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW

## Version 1.2: floating red button above other Android apps

New in v1.2: **dude.call floating overlay**. This is a genuine Android `WindowManager`
`TYPE_APPLICATION_OVERLAY`, not a home-screen-only widget. The glossy red button
has a transparent background and follows you over ordinary applications. The
original fullscreen button and the home-screen widget continue to work.

### Enable

1. Install/update the APK (same application ID and project debug signing key).
2. Open dude.call. Complete the phone-number setup first if necessary.
3. At the **FLOATING RED BUTTON** prompt, choose **ENABLE**.
4. In Android Settings, explicitly allow **Display over other apps** for dude.call.
5. Return to dude.call. If asked, grant the regular phone-call permission.
6. Press Home or open another app: the movable button remains at the screen edge.

**Tap and release** the red dome to place one call; **hold for at least 0.45s and
then drag** to move the button. The glossy black base is not a call target.
The button does not draw a wallpaper or black background behind itself. Other
apps remain touchable outside the small floating button rectangle.

To remove the overlay, expand notifications and tap **STOP** on the persistent
**dude.call floating button** notification. Relaunch dude.call to enable it
again. The app never starts the overlay at boot and never makes calls without
a deliberate button gesture. Permission may be revoked in Android Settings.

Implementation: `SYSTEM_ALERT_WINDOW`, `Settings.canDrawOverlays`, and
`Settings.ACTION_MANAGE_OVERLAY_PERMISSION`; a `specialUse` typed foreground
service for Android 14+; translucent `TYPE_APPLICATION_OVERLAY` window; and
`TelecomManager.placeCall` for calls initiated while another app is visible.
Android may suppress overlays over sensitive screens (permission dialogs,
lock screen, or secure apps). Foreground services show a persistent user-visible
notification. The system can still stop the service for resource or policy reasons.

No overlay is displayed before user permission. No Accessibility Service or
root privileges are used. Phone number storage is unchanged. Screenshots and
screen recordings remain blocked during first-run number setup.
