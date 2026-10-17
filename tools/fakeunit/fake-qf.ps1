# Makes the emulator look like a QF unit (the K706's firmware) to the detector: the ignition
# property set, the family override cleared, Dashwheel restarted. Its binders are not faked,
# so only the detection and the ignition broadcasts can be tried. See README.md.
$ErrorActionPreference = "Stop"
adb root | Out-Null
adb wait-for-device
adb shell "setprop sys.qf.is.acc.on true; setprop persist.dashwheel.family ''"
adb shell am force-stop com.openauto.dash
adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME com.openauto.dash" | Out-Null
Write-Host "QF unit: ignition off with  adb shell am broadcast -a com.qf.action.ACC_OFF  and on with  -a com.qf.action.ACC_ON"
