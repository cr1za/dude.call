# dude.call v1.3 — Android device acceptance tests

## Floating controls
- [ ] App opens without unsolicited overlay prompts.
- [ ] Compact FLOATING OFF control appears beneath the fullscreen arcade button.
- [ ] ON without overlay permission shows dark-red OPEN SETTINGS/CANCEL dialog.
- [ ] CANCEL leaves overlay OFF without additional prompts.
- [ ] Grant overlay permission; returning to app activates the overlay.
- [ ] Floating image is 64dp wide and background is transparent.
- [ ] Tap red dome once to call; black base and transparent pixels do not call.
- [ ] Long-press ~0.45 seconds shows REMOVE. Drag to REMOVE and release: overlay stops.
- [ ] Drag and release elsewhere: exact position retained, including after rotation/re-enable.
- [ ] Notification STOP reliably stops the overlay; reopening app doesn't restart it.
- [ ] In-app OFF removes overlay and displays three-second UNDO.
- [ ] Press UNDO within three seconds: overlay reappears.
- [ ] Decline notification permission: in-app OFF still works.
- [ ] TalkBack activates floating button with accessibility click.
- [ ] Multitouch ignores extra fingers and never makes duplicate calls.

## Calling and setup
- [ ] Fullscreen call button still works on successful finger release only.
- [ ] Home-screen widget performs single-tap call without confirmation.
- [ ] Call attempts across all three surfaces respect the same five-second cooldown.
- [ ] Existing active call is not duplicated when Android can detect its state.
- [ ] Permissions recover after denial and return from Android Settings.
- [ ] First-launch keypad, editable confirmation, and LET'S GO! work as before.
- [ ] In-place APK update preserves phone number; reinstall clears it.
- [ ] Setup remains screenshot-protected; home screen is screenshot-enabled.
- [ ] Audio, headphone-muting, reduced-motion, and TalkBack rules remain intact.

Android may suppress overlays on sensitive screens, and manufacturers may impose
additional restrictions. Verify on a real Android 13/14/15+ phone.
