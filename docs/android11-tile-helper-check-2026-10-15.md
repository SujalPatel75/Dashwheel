# Tile display helper on Android 11-15 — device check

The root-started helper that gives tile apps the system user's displays
(`SystemDisplayHelper`/`SystemDisplays`) only knew Android 10's activity-manager
call, so on Android 11 and later it could not hand its binder to Dashwheel and
tiles fell back to Dashwheel's own displays. There the firmware throws an app's
own screens (search, permission dialogs, voice prompts) full screen onto the
main screen. This change makes the helper work on Android 11 through 15.

## What changed

- The helper picks the activity manager's broadcast call by name
  (`broadcastIntentWithFeature` from Android 11, else `broadcastIntent`) and
  builds the arguments by parameter type, so it fits every release's signature
  (13 arguments on 10, 14 on 11, 15 on 12, 16 from 13).
- A display made as the system user is marked trusted from Android 11 on, which
  Android 12 and later require before an app may open its own screens on it.

## Proven on the emulator (Android 15, API 35, Medium_Tablet image)

- The helper runs as uid 1000 and `createVirtualDisplay` returns a display that
  reads back as trusted.
- The real broadcast path logs `broadcastIntentWithFeature with 16 arguments`,
  returns without error, and the helper stays up as uid 1000.
- Dashwheel's receiver is registered for the broadcast; Google Maps launched
  onto the trusted display resumes there.

The emulator cannot go further: its `su` does not elevate for the app process,
so Dashwheel's own in-app start of the helper cannot run, and a headless virtual
display cannot be driven to make Maps open its own sub-screens.

## What the tester with a rooted Android 11+ unit should check (Joying A13 / QCM6125)

1. Install this build. Open a tile with Google Maps inside (Windows tab ->
   Google Maps inside), with root granted.
2. In the log (`adb logcat -s SystemDisplays SystemDisplayHelper TileDisplay`),
   expect `the tile displays are the system's from now on` and, from the helper,
   `broadcastIntentWithFeature with N arguments` then `up as uid 1000`. If it
   instead says `no root` or the helper `died`, capture those lines.
3. In the Maps tile: run a search, open a place, trigger a permission dialog.
   Each should stay inside the tile, not jump full screen onto the main screen.
4. Power the unit off and on (ACC). After boot, confirm the helper comes back
   through `su` without a root-manager prompt blocking it, and the tile apps
   still keep their screens.
5. Confirm a second screen (Pi relay) and a window tile with zoom still behave.

Report the logcat lines from step 2 and anything from step 3 that still tears
out.
