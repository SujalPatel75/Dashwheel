# Fake head unit firmware (emulator)

Dashwheel's adapters for the other firmware families (FYT, MTC, ...) cannot be tried on the one real unit we have (a QF-firmware K706). The `:fakeunit` module is a small debug app for the emulator that stands in for them:

- it is installed as **`com.syu.ms`**, the FYT firmware's main server app, and exports the toolkit service Dashwheel binds (`com.syu.ms/app.ToolkitService`, action `com.syu.ms.toolkit`), with modules that speak the real binder descriptors and transaction codes, including the empty replies some firmwares give;
- it plays a scripted car (`fakeunit/src/main/assets/script.json`): the CAN decoder id, a speed and revs sweep, a per-car code (101), a phone that rings after 20 s;
- it relays broadcasts with byte arrays (MTC's `com.microntek.sync`), which `adb shell am broadcast` cannot send on its own.

Debug only; it is never a release asset and never installed on a real unit.

## Scripts

Run from the repository root with the HeadUnit_K706 AVD (API 29, 1280x720) up and a debug Dashwheel installed:

| Script | What it does |
|---|---|
| `fake-fyt.ps1` | installs the fake firmware, sets the FYT properties the detector reads, restarts Dashwheel as an FYT unit |
| `fake-qf.ps1` | sets the QF ignition property (no fake app: QF needs its binders), restarts Dashwheel as a QF unit |
| `fake-mtc.ps1` | names the MTC family through the debug override and shows the broadcasts to send |
| `reset.ps1` | removes the fake firmware and every property set above, restarts Dashwheel as plain Android |

Dashwheel only honours `persist.dashwheel.family` (the family override) and `persist.dashwheel.fytkeys` (take the FYT wheel keys) in **debug** builds.

## Pushing values by hand

```bash
adb shell am broadcast -a com.openauto.fakeunit.PUSH --ei module 7 --ei code 1031 --eia ints 57
adb shell am broadcast -a com.openauto.fakeunit.PUSH --ei module 0 --ei code 133 --eia ints 576
adb shell am broadcast -a com.openauto.fakeunit.PUSH --ei module 2 --ei code 9 --eia ints 4
adb shell am broadcast -a com.openauto.fakeunit.PUSH --es action com.microntek.sync --es extra syncdata --es hex "00 39 0A 1C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"
```

What to look for: `adb logcat -s UnitPlatform FytToolkit FytCanbus FytKeys FytPhone FakeFirmware FakeToolkit FakePush`.
