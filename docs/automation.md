# Automation (Tasker, MacroDroid, Automate)

Other apps on the head unit can tell Dashwheel what to do, and hear what the car does. Turn it on first: **Settings, System, Show all, Automation apps**. While it is off, Dashwheel ignores every command and sends no event.

Commands are broadcast intents. No package or class is needed: Dashwheel listens for them while it runs, which is whenever the unit is on. Every extra can be sent as text.

## Commands

Action: `com.openauto.dash.action.<COMMAND>`

| Command | Extras | What it does |
|---|---|---|
| `RUN_ACTION` | `action`: a steering-wheel action | Runs it, as a wheel button would. Names below. |
| `SHOW_DASHBOARD` | `name`: the dashboard's name | Shows that dashboard. |
| `NAVIGATE` | `destination`: `home`, `work`, a saved place, or an address | Starts guidance there. |
| `SAY` | `text` (up to 500 characters) | Says it in the launcher's voice. |
| `SET_LOOK` | `appearance`: `AUTO`, `DARK` or `LIGHT`; and / or `skin`: a skin's name, such as `PANORAMA`, `COCKPIT`, `RACING` | Changes the look. |
| `SET_VOLUME` | `level` | Sets the music volume. |
| `OPEN_APP` | `name`: the app's name, as on its icon | Opens it. |
| `START_PARKING_TIMER` | `minutes`; optional `add` = `true` to add to a running timer | Starts the parking timer. |
| `STOP_PARKING_TIMER` | | Stops it. |
| `IMPORT_DASHBOARD` | `code`: a shared dashboard (`DW1:…`) | Shows it in the Share sheet, to look at before it replaces anything. |

Steering-wheel actions for `RUN_ACTION` include `MEDIA_PLAY_PAUSE`, `MEDIA_NEXT`, `MEDIA_PREVIOUS`, `VOLUME_UP`, `VOLUME_DOWN`, `VOLUME_MUTE`, `SAY_STATUS`, `QUIET`, `DISMISS_ALERT`, `NAVIGATE_HOME`, `NAVIGATE_WORK`, `NAVIGATE_FUEL` and `GO_HOME`: the full list is the wheel button menu, in `SteeringWheelActions.kt`.

## Events

Action: `com.openauto.dash.event.<EVENT>`, sent once each time it happens.

| Event | Extras |
|---|---|
| `IGNITION_ON` | |
| `IGNITION_OFF` | |
| `REVERSE_ON` | |
| `REVERSE_OFF` | |
| `OBD_CONNECTED` | |
| `OBD_DISCONNECTED` | |
| `FAULT_CODES` | `codes`: the fault codes, comma-separated |

Events never carry the car's position.

## Tasker examples

**Dark look at a set time:** Profile, Time, 20:00. Task, *Send Intent*: Action `com.openauto.dash.action.SET_LOOK`, Extra `appearance:DARK`, Target *Broadcast Receiver*.

**Say something at ignition:** Profile, Event, *Intent Received*, Action `com.openauto.dash.event.IGNITION_ON`. Task, *Send Intent*: Action `com.openauto.dash.action.SAY`, Extra `text:Good morning, check the tyre pressure today`, Target *Broadcast Receiver*.

**Music dashboard when the phone connects:** Profile, State, *BT Connected* (your phone). Task, *Send Intent*: Action `com.openauto.dash.action.SHOW_DASHBOARD`, Extra `name:Music`, Target *Broadcast Receiver*.

From adb, for testing:

```
adb shell am broadcast -a com.openauto.dash.action.SAY --es text "Hello"
```
