# Back to plain Android: the fake firmware uninstalled, every fake property cleared, Dashwheel restarted.
$ErrorActionPreference = "Continue"
adb root | Out-Null
adb wait-for-device
adb uninstall com.syu.ms | Out-Null
adb shell "setprop persist.dashwheel.family ''; setprop persist.dashwheel.fytkeys ''; setprop sys.qf.is.acc.on ''; setprop sys.carlink.type ''"
adb shell am force-stop com.openauto.dash
adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME com.openauto.dash" | Out-Null
Write-Host "Plain Android again (ro.fyt.* stay set until the emulator reboots)."
