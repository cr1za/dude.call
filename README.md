# One Button Instant Caller (OBIC)

**OBIC** is a minimal Android instant-caller with three ways to place a call:
the huge full-screen red arcade button, a transparent home-screen widget, and a
small red button that can float above other applications.

**Current version:** 1.4.0 (branding update over 1.3).

## Using OBIC

1. Install and open OBIC. On the first run, enter a phone number using the
   arcade keypad. Press **LOCK IN**, check the number, then press **LET'S GO!**.
2. In the full-screen app, touch and release the big red button to call.
3. Tap the compact **FLOATING ON/OFF** control at the bottom to enable or
   disable the optional red floating button.
4. The first time you enable floating mode, approve **Display over other apps**
   in Android Settings. Android may also ask about notification permission.
5. The floating button is 64dp wide. Tap the red dome to call; hold then drag
   to move it. Drop onto the temporary **REMOVE** target to turn it OFF.
   You can also turn it off inside OBIC or use STOP in its notification.
   In-app OFF offers a 3-second **UNDO**. It remains OFF until re-enabled.
6. To add the transparent home-screen widget, long-press your Android home
   screen, open **Widgets**, then choose **OBIC Call Button**. One tap calls
   without another confirmation.

The saved number is not backed up. Uninstalling the app clears it. Installing
an update of the **same Android application ID and signing certificate**
preserves the number. Avoid uninstalling just to change the launcher icon.

## Build and install

The repo includes a GitHub Actions workflow named **Build OBIC Android APK**.
Its successful build artifact is **OBIC-debug-APK**. Download the artifact ZIP
and extract `app-debug.apk` to install or update OBIC.

Local build with Java 17, Android SDK 35 and Gradle 8.9:

```sh
gradle --no-daemon :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The repository ships a consistent **debug-only** signing key for update testing.
Do not use that public key to sign a production release.

## Android identity and backwards compatibility

This project was formerly named **dude.call**. The displayed Android application
name, launcher name, widget picker, floating notification, Gradle project and
CI artifact were rebranded as **One Button Instant Caller (OBIC)**.

The technical Android application ID `com.cr1za.dudecall`, existing Java
package/classes, stored-preference keys, notification channel ID and widget
provider component are retained **intentionally**. Changing them would risk
turning OBIC into an unrelated app, losing the number on update, resetting
permissions or orphaning existing widgets. They are compatibility identifiers,
not visible product branding.

GitHub repository renaming is a separate administrative setting; the currently
connected repository is `cr1za/dude.call`. If it is later renamed to
`cr1za/OBIC` through **GitHub → Settings → General → Repository name**,
existing GitHub repository links should redirect. No Android reinstall is
required.

## Privacy and limitations

- No internet permission, advertising or analytics.
- The saved number is local and intentionally locked until uninstall.
- Call permission is requested by Android. The app does not bypass phone
  carrier or Android calling restrictions.
- Optional floating mode requires system overlay permission and a foreground
  service. Android may suppress overlays on sensitive screens.
- The optional home-screen widget cannot float over other apps.
- Setup screens are protected against screenshots; the main button is not.
- Tested by GitHub Actions build; real-device interaction testing is still
  necessary. See [OBIC-QA.md](OBIC-QA.md).

## Documentation

- [Changelog](CHANGELOG.md)
- [Device acceptance checklist](OBIC-QA.md)
