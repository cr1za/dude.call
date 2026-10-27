# OBIC changelog

## 1.4.0 — One Button Instant Caller (OBIC) rebrand

- Renamed user-visible app branding to **One Button Instant Caller (OBIC)**.
- Launcher shortcut shows **OBIC**, and Android app info uses the full title.
- Renamed home-screen widget picker label and widget description to **OBIC**.
- Rebranded overlay notification title, notification channel name and permission
  messaging.
- Renamed Gradle root project to **OBIC** and GitHub Actions workflow/artifact.
- Increased Android version code to 6 while preserving the same app ID,
  signing certificate, saved number, installed widgets and notification settings.
- Removed obsolete source archive and old Termux upload helper from the repo.

## 1.3.0 — Floating button controls and usability fixes

- Compact FLOATING ON/OFF control below the fullscreen arcade button.
- OFF removes the overlay; a three-second UNDO restores it.
- Notification STOP and drag-to-REMOVE persist the OFF state.
- Transparent 64dp floating button; one-tap call; long-press to drag.
- Free positioning without edge snapping; remember its position.
- Explicit overlay permission explanation with OPEN SETTINGS/CANCEL.
- Single call cooldown shared across fullscreen, widget and overlay.
- Improved accessibility, first-finger tracking and Android service lifecycle.

## 1.2.0 — Floating overlay

- Added floating red call button over apps and notification STOP.

## 1.1.0 — Home widget

- Added transparent home-screen widget and consistent debug signing.
