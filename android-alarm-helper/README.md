# Android Alarm Helper

A tiny Android app that now supports **its own native alarm engine** (no dependency on the phone's stock clock app for scheduling).

## Current status

✅ Working debug build available (`app-debug.apk`)  
✅ Native alarms scheduled via `AlarmManager` (in-app)  
✅ Ringing foreground service + alarm ring screen (Dismiss / Snooze 10m)  
✅ Boot/package-replace reschedule support  
✅ Embedded HTTP bridge server (`/set`) with optional token auth  
✅ Optional callback webhook after bridge request is queued  
✅ Top-left debug menu (view/copy/clear logs + exact alarm settings)  
✅ GitHub release pipeline installs SDK + publishes APK checksum

## Native alarm behavior

When an alarm fires:
- app starts a ringing foreground service
- shows full-screen alarm UI (`Dismiss`, `Snooze 10m`)
- repeating alarms auto-reschedule
- one-shot alarms are removed after firing

## Bridge endpoint

Start bridge in app, then call:

```text
http://<phone-ip>:8765/set?hour=7&minute=30&label=Wake%20up&days=mon-fri&vibrate=true&token=YOUR_TOKEN
```

Optional params:
- `skipUi` (accepted for compatibility; ignored in native mode)
- `autoLaunch` (`true`/`false`)
- `callback` (URL for best-effort callback POST)
- `source` (string label)

## Day parsing

Accepted values for `days`:
- numeric: `2,3,4,5,6`
- aliases: `mon,tue,wed`
- ranges: `mon-fri`, `fri-mon` (wrap supported)

Android `Calendar` mapping:
- `1` = Sunday
- `2` = Monday
- `3` = Tuesday
- `4` = Wednesday
- `5` = Thursday
- `6` = Friday
- `7` = Saturday

## Permissions notes

On Android 12+:
- Exact alarms may require user approval (`SCHEDULE_EXACT_ALARM`)

On Android 13+:
- Notifications permission may be required for ringing notifications

Use the top-left debug menu:
- **Open exact alarm settings**
- **View logs** if alarms do not fire as expected

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
git tag alarm-v0.5.0
git push origin alarm-v0.5.0
```

or use helper:

```bash
cd android-alarm-helper
scripts/cut-release-tag.sh
```

This builds debug APK in CI and attaches both APK + `.sha256` to the GitHub release.
