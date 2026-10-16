# Names the MTC (Microntek / HCT) family through the debug override and installs the fake firmware
# for its broadcast relay; Dashwheel is restarted. See README.md.
$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\..\.."
Push-Location $root
try {
    adb root | Out-Null
    adb wait-for-device
    & .\gradlew.bat :fakeunit:installDebug -q
    adb shell "setprop persist.dashwheel.family MTC"
    adb shell am force-stop com.openauto.dash
    adb shell "am start -a android.intent.action.MAIN -c android.intent.category.HOME com.openauto.dash" | Out-Null
    Write-Host "MTC unit. Try:"
    Write-Host "  adb shell am broadcast -a com.microntek.irkeyDown --ei keyCode 273"
    Write-Host "  adb shell am broadcast -a com.microntek.bootcheck --es class poweroff"
    Write-Host "  adb shell am broadcast -a com.openauto.fakeunit.PUSH --es action com.microntek.sync --es extra syncdata --es hex '00 39 0A 1C 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00'"
} finally {
    Pop-Location
}
