# dude.call

One big red cartoon-style 3D call button on a completely black background.

- First launch: enter a phone number and tap **SAVE**.
- After that: only the red button is shown. Tap it to call the stored number immediately (after Android's one-time phone-call permission prompt).
- The number cannot be edited from the app. It stays through restarts and app updates.
- Uninstalling the app clears it. Backup and transfer of the number are disabled to prevent it from being restored after reinstall.
- No ads, Internet access, contacts access, analytics, third-party dependencies, or other app functions.

## Build an APK with GitHub Actions

1. Make a new GitHub repository named **dude.call**.
2. Upload all files **and folders** in this project to its root (including the hidden `.github` folder).
3. Open the repository's **Actions** tab and select **Build Android APK**. You can choose **Run workflow** or just let it run on the first push to `main`.
4. Open the completed workflow run, then download the **dude-call-debug-apk** artifact.
5. Unzip the artifact, install `app-debug.apk` on your phone. Android may require you to allow app installs from your browser or file manager.

This is an installable debug APK, for private testing; a release signing key isn't included.

## Build locally

Use Android Studio with JDK 17 and Gradle 8.9, or run `gradle :app:assembleDebug` with those tools installed. Android SDK 35 is required.

## Android package details

- App label: `dude.call`
- Android application ID: `com.cr1za.dudecall`
- Minimum Android version: Android 6.0 (API 23)
- Calls use Android `ACTION_CALL`, not merely the dialer.
- Requires a device that can place telephone calls and grants `CALL_PHONE` permission. Android itself may block emergency calls and may show system call handling UI; those are OS behavior, not extra app features.
