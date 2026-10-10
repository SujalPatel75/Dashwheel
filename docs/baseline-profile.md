# Baseline profile

The launcher ships a baseline profile so a cold start and the first swipes run
compiled code instead of the interpreter. Until now it was the hand-written
wildcard in `app/src/main/baseline-prof.txt` (review 2026-10-10, finding #8).
The `:baselineprofile` module records a real one.

Nothing here runs in a normal build or in CI. `./gradlew :app:assembleGithubRelease`
builds exactly as before; the `androidx.baselineprofile` plugin on `:app` only
adds the `benchmarkRelease` and `nonMinifiedRelease` build types the recorder
installs, and the `generate…BaselineProfile` tasks.

## What it records

`baselineprofile/src/main/java/com/openauto/dash/baselineprofile/StartupProfile.kt`:

- `StartupProfileGenerator`: a cold start of the dashboard, then two swipes left
  and two right along the middle row. Also written as the startup profile.
- `StartupBenchmark`: the same journey, timed cold with no compilation and with
  the profile, 5 runs each, to see what the profile buys.

## Device

Recording needs Android 13+, or Android 9+ with root. Use the `HeadUnit_K706`
emulator (API 29, 1280x720, `adb root` works on it), not the head unit itself.

- Only one device connected (`adb devices`), awake and unlocked.
- MapLibre tiles crash on the emulator's GL (see the emulator notes): seed a
  layout without map tiles first, or the journey records a crash.
- The first start shows the setup. Finish or skip it once in the
  `benchmarkRelease` build so the journey swipes the dashboard, not the setup.

## Record

```sh
export JAVA_HOME="/c/Users/PC-salon/.jdks/jbr-21.0.11"
./gradlew :app:generateGithubReleaseBaselineProfile
```

This installs `githubNonMinifiedRelease` and the recorder, runs
`StartupProfileGenerator`, and writes the result under
`app/src/githubRelease/generated/baselineProfiles/` (the task prints the path). Commit those files. The release build merges them with
`app/src/main/baseline-prof.txt`. Once the recorded profile looks right, the
hand-written wildcard there can be cut down or removed. Check the APK's
`assets/dexopt/baseline.prof` size before and after.

For the Play edition: `./gradlew :app:generatePlayReleaseBaselineProfile`.

## Measure

```sh
./gradlew :baselineprofile:connectedGithubBenchmarkReleaseAndroidTest
```

Results are in `baselineprofile/build/outputs/connected_android_test_additional_output/`.
Compare `timeToInitialDisplayMs` between `startupWithoutProfile` and `startupWithProfile`.

## Notes

- The head unit runs Android 10, which does not read profiles from the APK by
  itself. `androidx.profileinstaller` installs it (already a dependency of `:app`).
- Versions: `androidx.baselineprofile` and `benchmark-macro-junit4` 1.4.1,
  checked to configure and build with AGP 8.13.2. Do not move them without
  building `:baselineprofile` again.
