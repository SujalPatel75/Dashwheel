# Turns the emulator into an FYT head unit: installs the fake firmware (as com.syu.ms),
# sets the properties the detector reads, and restarts Dashwheel. See README.md.
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
Push-Location $root
try {
    adb root | Out-Null
    adb wait-for-device
    & .\gradlew.bat :fakeunit:installDebug -q
    # ro.* can only be set once per boot (and only as root); the package alone names FYT anyway.
    adb shell "setprop ro.fyt.platform uis7862 2>/dev/null; setprop ro.build.fytmanufacturer 31 2>/dev/null; setprop sys.carlink.type 0; setprop persist.dashwheel.family ''"
    adb shell am force-stop com.openauto.dash
    adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME com.openauto.dash" | Out-Null
    Write-Host "FYT unit: watch with  adb logcat -s UnitPlatform FytToolkit FytCanbus FytPhone FakeFirmware"
    Write-Host "Wheel keys through the fake:  adb shell setprop persist.dashwheel.fytkeys 1  then restart Dashwheel"
} finally {
    Pop-Location
}
