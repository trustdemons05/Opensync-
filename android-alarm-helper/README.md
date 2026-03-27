# Android Alarm Helper

A tiny Android app that creates native phone alarms, designed as a bridge target for OpenClaw.

## Current status

✅ Working debug build available (`app-debug.apk`)  
✅ Manual UI flow works (pick time, label, create alarm)  
✅ External trigger support (custom action + deep link)  
✅ Repeat-days support via `days` (Calendar format 1..7 + names like `mon`)  
✅ Embedded HTTP bridge server (`/set`) with optional token auth  
✅ Optional callback webhook after bridge request is queued  
✅ Compatibility fallback for OEM clock apps (minimal intent retry)  
✅ Top-left debug menu (view/copy/clear logs in-app)  
✅ OEM fallback: if direct set is blocked, open alarms screen automatically  
✅ GitHub release pipeline now installs SDK + publishes APK checksum

## App capabilities (current)

- Create alarm with:
  - `hour`
  - `minute`
  - `label`
  - `days` (optional)
  - `skipUi` (optional)
  - `vibrate` (optional)

- Input quality-of-life:
  - day presets (`Weekdays`, `Daily`, `Clear`)
  - flexible day parser: `2,3,4,5,6`, `mon,tue`, `mon-fri`
  - bad input clamping and warnings shown in status

- Bridge mode:
  - start/stop HTTP server from inside app
  - endpoint: `http://<phone-ip>:8765/set`
  - optional token check (`token` query param or `X-Alarm-Token` header)
  - optional callback: `callback=<url>`

## Day mapping

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
koialarm://set?hour=7&minute=30&label=Wake%20up&days=mon,tue,wed,thu,fri&skipUi=true&vibrate=true
```

Custom action:

- Action: `ai.koi.alarmhelper.action.SET_ALARM`
- Extras:
  - `hour` (Int/String)
  - `minute` (Int/String)
  - `label` (String)
  - `days` (String, e.g. `mon-fri` or `2,3,4,5,6`)
  - `skipUi` (Boolean/String)
  - `vibrate` (Boolean/String)
  - `autoLaunch` (Boolean/String; default `true`)

HTTP bridge (`GET` or `POST` query params):

```text
http://<phone-ip>:8765/set?hour=7&minute=30&label=Wake%20up&days=mon-fri&token=YOUR_TOKEN&callback=https://example.com/hook
```

Response is JSON (`ok`, `message`, metadata).

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
git tag alarm-v0.4.3
git push origin alarm-v0.4.3
```

or use helper:

```bash
cd android-alarm-helper
scripts/cut-release-tag.sh
```

This builds debug APK in CI and attaches both APK + `.sha256` to the GitHub release.
