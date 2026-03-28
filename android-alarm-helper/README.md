# Android Alarm Helper

Android alarm app with native scheduling, ringing UI, bridge control, and in-app debug tools.

## Current status

✅ Native alarms (no stock clock app dependency)  
✅ Ringing foreground service + full-screen Dismiss/Snooze UI  
✅ Bridge endpoint (`/set`) with optional token + callback  
✅ **Saved alarms UI** (view / enable-disable / delete / refresh)  
✅ **Sound selection** (System alarm / ringtone / notification)  
✅ UI declutter with bottom tabs (Alarms / Logs / Bridge)  
✅ Better alarm cards (dark themed, improved spacing, cleaner actions)  
✅ Dedicated Logs tab (view/copy/clear logs + exact-alarm settings shortcut)  
✅ App-only relay mode via FCM data messages (token-based)  
✅ Boot/package-replace reschedule support

## Manual UI flow

Bottom tabs:
- **Alarms**: create + manage alarms, plus top summary strip
- **Logs**: full debug logs with copy/clear actions
- **Bridge**: token + local endpoint controls

Create flow (inside **Alarms** tab):
- Pick time
- Optional label
- Optional repeat days (`mon-fri`, `2,3,4,5,6`, etc.)
- Choose sound
- Toggle vibrate
- Tap **Create native alarm**

Then use the **Scheduled alarms** list in the same tab for enable/disable/delete.

## Bridge endpoint

Start bridge in app, then call:

```text
http://<phone-ip>:8765/set?hour=7&minute=30&label=Wake%20up&days=mon-fri&soundType=alarm&vibrate=true&token=YOUR_TOKEN
```

Optional params:
- `soundType` = `alarm` | `ringtone` | `notification`
- `skipUi` (accepted for compatibility; ignored in native mode)
- `autoLaunch` (`true`/`false`)
- `callback` (best-effort callback POST URL)
- `source` (string label)

## App-only relay mode (FCM)

Use this when you want no LAN URL/tunnel dependency.

In **Bridge** tab:
1. Fill Firebase values:
   - `projectId`
   - `applicationId`
   - `apiKey`
   - `senderId` (project number)
2. Tap **Save relay config**
3. Tap **Fetch device token**
4. Copy token and use it as target for FCM data messages

The app accepts FCM **data payload** fields matching bridge params:
- `hour`, `minute` (required)
- `label`, `days`, `vibrate`, `soundType`, `autoLaunch`, `callback`, `source`
- `token` (optional shared token; if set in app bridge token field, it is enforced)

Relay processing runs in `RelayMessagingService` and schedules native alarms directly.

## Day parsing

Accepted `days` formats:
- numeric: `2,3,4,5,6`
- names: `mon,tue,wed`
- ranges: `mon-fri`, `fri-mon` (wrap supported)

Android mapping:
- `1` = Sunday
- `2` = Monday
- `3` = Tuesday
- `4` = Wednesday
- `5` = Thursday
- `6` = Friday
- `7` = Saturday

## Permissions notes

- Android 12+: exact alarm access may require approval
- Android 13+: notifications permission may be required

Use the Logs tab (exact alarm settings shortcut) if alarms don’t fire reliably.

## Local build

```bash
cd android-alarm-helper
scripts/build-debug.sh
```

APK output:

```text
android-alarm-helper/app/build/outputs/apk/debug/app-debug.apk
```

## Local release artifact helper

```bash
cd android-alarm-helper
scripts/release-local.sh
```

Artifacts in:

```text
android-alarm-helper/releases/
```

## GitHub release automation

Workflow:

```text
.github/workflows/android-alarm-helper-release.yml
```

Create release by tag:

```bash
git tag alarm-v0.7.0
git push origin alarm-v0.7.0
```

or helper:

```bash
cd android-alarm-helper
scripts/cut-release-tag.sh
```

CI attaches APK + `.sha256` to release.
