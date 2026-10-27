# OBIC — Android device acceptance checklist

## Branding and update
- [ ] Launcher app title says **OBIC** (not the previous name).
- [ ] Android App Info displays **One Button Instant Caller (OBIC)**.
- [ ] Widget picker displays **OBIC Call Button**.
- [ ] Floating foreground notification and channel are branded **OBIC**.
- [ ] In-place APK update preserves the existing phone number.
- [ ] Existing home widgets continue working after the update.

## Call flow
- [ ] First launch: arcade keypad, LOCK IN, editable confirmation, LET'S GO!.
- [ ] Giant red fullscreen arcade button calls only after finger release.
- [ ] Tapping transparent home widget calls without a confirmation.
- [ ] Shared cooldown prevents duplicate outgoing calls across surfaces.
- [ ] Permission denial and Android Settings recovery still work.

## Floating button
- [ ] Bottom FLOATING ON/OFF control is visible on main screen.
- [ ] First ON displays OPEN SETTINGS/CANCEL overlay permission explanation.
- [ ] Returning with permission granted enables the floating button.
- [ ] No nagging prompt on ordinary app launches.
- [ ] Red floating button is 64dp wide with transparent corners.
- [ ] One short tap calls; hold then drag does not call.
- [ ] Releasing after dragging remembers exact position.
- [ ] Drag onto temporary REMOVE target to turn OFF.
- [ ] Notification STOP leaves floating mode OFF after reopening app.
- [ ] In-app OFF removes overlay; UNDO works within three seconds.
- [ ] Rotation and background/foreground transitions keep it usable.
- [ ] TalkBack accessibility click can activate the floating button.

## Audio, display and safety
- [ ] Arcade sound respects media volume and headphone-muting rules.
- [ ] Reduced motion retains essential press feedback.
- [ ] Number setup screens block screenshots, main screen allows them.
- [ ] Calls and floating button do not bypass Android security screens.

Source-level renaming has been validated through Android build automation.
Device-dependent behavior must still be tested on a real Android device.
