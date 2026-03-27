# Android Alarm Helper

A tiny Android app that creates native phone alarms, designed as a bridge target for OpenClaw.

## Current status

✅ Working debug build available (`app-debug.apk`)  
✅ Manual UI flow works (pick time, label, create alarm)  
✅ External trigger support (custom action + deep link)  
✅ Repeat-days support via `days` (Calendar format 1..7)

## App capabilities (current)

- Create alarm with:
  - `hour`
  - `minute`
  - `label`
  - `days` (optional, comma-separated; Android Calendar constants)
  - `skipUi` (optional)
  - `vibrate` (optional)

### Day mapping

Android `Calendar` constants:
- `1` = Sunday
- `2` = Monday
- `3` = Tuesday
- `4` = Wednesday
- `5` = Thursday
- `6` = Friday
- `7` = Saturday

## External trigger examples

Deep link:

```text
koialarm://set?hour=7&minute=30&label=Wake%20up&days=2,3,4,5,6&skipUi=true&vibrate=true
```

Custom action:

- Action: `ai.koi.alarmhelper.action.SET_ALARM`
- Extras:
  - `hour` (Int)
  - `minute` (Int)
  - `label` (String)
  - `days` (String: `2,3,4,5,6`)
  - `skipUi` (Boolean)
  - `vibrate` (Boolean)
  - `autoLaunch` (Boolean, default `true` for external triggers)

## Local build

```bash
cd android-alarm-helper
scripts/build-debug.sh
```

Output:

```text
android-alarm-helper/app/build/outputs/apk/debug/app-debug.apk
```

## Local release artifact helper

```bash
cd android-alarm-helper
scripts/release-local.sh
```

Creates timestamped artifacts under:

```text
android-alarm-helper/releases/
```

## GitHub release automation

Workflow file:

```text
.github/workflows/android-alarm-helper-release.yml
```

Trigger a downloadable GitHub Release APK by tagging and pushing:

```bash
git tag alarm-v0.1.0
git push origin alarm-v0.1.0
```

This will build debug APK and attach it to the GitHub release.
