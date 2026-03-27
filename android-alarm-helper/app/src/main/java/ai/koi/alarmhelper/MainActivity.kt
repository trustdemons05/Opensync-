package ai.koi.alarmhelper

import android.Manifest
import android.app.AlarmManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import ai.koi.alarmhelper.AlarmBridgeServer.BridgeRequest
import ai.koi.alarmhelper.AlarmBridgeServer.BridgeResponse
import ai.koi.alarmhelper.databinding.ActivityMainBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import fi.iki.elonen.NanoHTTPD
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedHour: Int = 7
    private var selectedMinute: Int = 0
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

    private var bridgeServer: AlarmBridgeServer? = null
    private val recentBridgeEvents = ArrayDeque<String>()
    private val debugLogs = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderTime()
        setupUi()
        updateBridgeStatus("Bridge stopped")
        logDebug("App started")
        ensureCapabilities()
        handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        renderSavedAlarms()
    }

    override fun onDestroy() {
        logDebug("App destroyed")
        stopBridgeServer()
        super.onDestroy()
    }

    private fun setupUi() = with(binding) {
        pickTimeButton.setOnClickListener {
            android.app.TimePickerDialog(
                this@MainActivity,
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    renderTime()
                    statusText.text = "Picked ${formattedTime()}"
                    logDebug("Time picked: ${formattedTime()}")
                },
                selectedHour,
                selectedMinute,
                false
            ).show()
        }

        weekdaysButton.setOnClickListener {
            daysEditText.setText("mon,tue,wed,thu,fri")
            statusText.text = "Preset applied: weekdays"
            logDebug("Preset weekdays applied")
        }

        dailyButton.setOnClickListener {
            daysEditText.setText("sun,mon,tue,wed,thu,fri,sat")
            statusText.text = "Preset applied: daily"
            logDebug("Preset daily applied")
        }

        clearDaysButton.setOnClickListener {
            daysEditText.setText("")
            statusText.text = "Repeat days cleared"
            logDebug("Repeat days cleared")
        }

        val soundLabels = SOUND_OPTIONS.map { it.label }
        val soundAdapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, soundLabels)
        soundDropdown.setAdapter(soundAdapter)
        soundDropdown.setText(SOUND_OPTIONS.first().label, false)

        refreshAlarmsButton.setOnClickListener {
            renderSavedAlarms()
            logDebug("Saved alarms list refreshed")
        }

        setAlarmButton.setOnClickListener {
            logDebug("Manual create alarm tapped")
            val result = createAlarm(
                label = labelEditText.text?.toString().orEmpty(),
                skipUi = false,
                vibrate = vibrateCheckBox.isChecked,
                daysSpec = daysEditText.text?.toString().orEmpty(),
                soundType = selectedSoundType(),
                source = "manual"
            )
            if (!result.ok) {
                Toast.makeText(this@MainActivity, result.message, Toast.LENGTH_LONG).show()
            }
        }

        bridgeToggleButton.setOnClickListener {
            if (bridgeServer == null) startBridgeServer() else stopBridgeServer()
        }

        debugMenuButton.setOnClickListener {
            showDebugMenu()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_create -> showTab(Tab.CREATE)
                R.id.nav_alarms -> showTab(Tab.ALARMS)
                R.id.nav_bridge -> showTab(Tab.BRIDGE)
                else -> false
            }
        }
        bottomNav.selectedItemId = R.id.nav_create

        renderSavedAlarms()
    }

    private fun ensureCapabilities() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQ_POST_NOTIFICATIONS
                )
                logDebug("Requested POST_NOTIFICATIONS permission")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                binding.statusText.text = "Exact alarms may be restricted. Open debug menu if alarms don't ring."
                logDebug("Exact alarm permission not granted (canScheduleExactAlarms=false)")
            }
        }
    }

    private fun startBridgeServer() {
        if (bridgeServer != null) {
            updateBridgeStatus("Bridge already running at ${bridgeUrl()}")
            return
        }

        try {
            val server = AlarmBridgeServer(
                port = BRIDGE_PORT,
                tokenProvider = { binding.bridgeTokenEditText.text?.toString().orEmpty() },
                onAlarmRequest = { req -> onBridgeRequest(req) }
            )
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            bridgeServer = server
            binding.bridgeToggleButton.text = "Stop bridge server"
            updateBridgeStatus("Bridge running at ${bridgeUrl()}")
            logDebug("Bridge started at ${bridgeUrl()}")
        } catch (t: Throwable) {
            bridgeServer = null
            binding.bridgeToggleButton.text = "Start bridge server"
            updateBridgeStatus("Failed to start bridge: ${t.message}")
            logDebug("Bridge start failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    private fun stopBridgeServer() {
        bridgeServer?.stop()
        bridgeServer = null
        binding.bridgeToggleButton.text = "Start bridge server"
        updateBridgeStatus("Bridge stopped")
        logDebug("Bridge stopped")
    }

    private fun onBridgeRequest(req: BridgeRequest): BridgeResponse {
        val warnings = mutableListOf<String>()

        val (hour, hourWarning) = parseHour(req.hour, selectedHour)
        val (minute, minuteWarning) = parseMinute(req.minute, selectedMinute)
        hourWarning?.let(warnings::add)
        minuteWarning?.let(warnings::add)

        val label = req.label.orEmpty()
        val daysSpec = req.days.orEmpty()
        val vibrate = parseBoolean(req.vibrate, default = true)
        val soundType = normalizeSoundType(req.soundType ?: selectedSoundType())
        val autoLaunch = parseBoolean(req.autoLaunch, default = true)

        val requestedTime = LocalTime.of(hour, minute).format(timeFormatter)
        val bridgeMessage = "Bridge request from ${req.source} for $requestedTime"
        logDebug("$bridgeMessage (autoLaunch=$autoLaunch, days='${daysSpec}')")

        val result = if (autoLaunch) {
            scheduleNativeAlarm(
                hour = hour,
                minute = minute,
                label = label,
                daysSpec = daysSpec,
                vibrate = vibrate,
                soundType = soundType,
                source = "bridge"
            )
        } else {
            AlarmCreateResult(
                ok = true,
                message = "autoLaunch disabled; values populated in UI"
            )
        }

        runOnUiThread {
            selectedHour = hour
            selectedMinute = minute
            binding.labelEditText.setText(label)
            binding.daysEditText.setText(daysSpec)
            binding.vibrateCheckBox.isChecked = vibrate
            binding.soundDropdown.setText(displayLabelForSoundType(soundType), false)
            renderTime()

            val warningSuffix = if (warnings.isEmpty()) "" else " (${warnings.joinToString("; ")})"
            val resultSuffix = if (result.ok) "" else " [${result.message}]"
            binding.statusText.text = "$bridgeMessage$warningSuffix$resultSuffix"
        }

        val callbackUrl = req.callbackUrl?.takeIf { it.isNotBlank() }
        if (callbackUrl != null) {
            sendCallbackAsync(
                callbackUrl = callbackUrl,
                ok = result.ok,
                message = result.message,
                extra = mapOf(
                    "hour" to hour,
                    "minute" to minute,
                    "autoLaunch" to autoLaunch,
                    "soundType" to soundType,
                    "warnings" to warnings.joinToString("; "),
                    "triggerAt" to (result.triggerAt ?: "")
                )
            )
            logDebug("Callback queued to $callbackUrl")
        }

        val summary = "$requestedTime label='${label.ifBlank { "Koi Alarm" }}'"
        recordBridgeEvent(summary)

        return BridgeResponse(
            ok = result.ok,
            message = result.message,
            extra = mapOf(
                "time" to requestedTime,
                "autoLaunch" to autoLaunch,
                "warnings" to warnings.joinToString("; "),
                "bridgeUrl" to bridgeUrl(),
                "triggerAt" to (result.triggerAt ?: "")
            ),
            status = if (result.ok) 200 else 400
        )
    }

    private fun sendCallbackAsync(
        callbackUrl: String,
        ok: Boolean,
        message: String,
        extra: Map<String, Any?> = emptyMap()
    ) {
        Thread {
            try {
                val conn = (URL(callbackUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 7000
                    readTimeout = 7000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val payload = buildString {
                    append("{\"ok\":")
                    append(if (ok) "true" else "false")
                    append(",\"message\":\"")
                    append(message.replace("\"", "\\\""))
                    append("\"")
                    extra.forEach { (k, v) ->
                        append(",\"")
                        append(k.replace("\"", "\\\""))
                        append("\":\"")
                        append(v?.toString()?.replace("\"", "\\\"") ?: "")
                        append("\"")
                    }
                    append("}")
                }

                conn.outputStream.use { it.write(payload.toByteArray()) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (t: Throwable) {
                logDebug("Callback failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }.start()
    }

    private fun handleExternalIntent(intent: Intent?) {
        intent ?: return

        val action = intent.action.orEmpty()
        val data = intent.data
        val hasDeepLink = data?.scheme == "koialarm" && data.host == "set"
        val hasCustomAction = action == ACTION_SET_ALARM

        if (!hasDeepLink && !hasCustomAction) {
            return
        }

        val warnings = mutableListOf<String>()

        val hourRaw = readRaw(intent, data, EXTRA_HOUR, "hour")
        val minuteRaw = readRaw(intent, data, EXTRA_MINUTE, "minute")
        val (hour, hourWarning) = parseHour(hourRaw, selectedHour)
        val (minute, minuteWarning) = parseMinute(minuteRaw, selectedMinute)
        hourWarning?.let(warnings::add)
        minuteWarning?.let(warnings::add)

        val label = readRaw(intent, data, EXTRA_LABEL, "label")
            ?: binding.labelEditText.text?.toString().orEmpty()
        val daysSpec = readRaw(intent, data, EXTRA_DAYS, "days") ?: ""

        val skipUi = parseBoolean(
            readRaw(intent, data, EXTRA_SKIP_UI, "skipUi"),
            default = false
        )
        val vibrate = parseBoolean(
            readRaw(intent, data, EXTRA_VIBRATE, "vibrate"),
            default = true
        )
        val soundType = normalizeSoundType(
            readRaw(intent, data, EXTRA_SOUND_TYPE, "soundType") ?: selectedSoundType()
        )
        val autoLaunch = parseBoolean(
            readRaw(intent, data, EXTRA_AUTO_LAUNCH, "autoLaunch"),
            default = true
        )

        selectedHour = hour
        selectedMinute = minute
        binding.labelEditText.setText(label)
        binding.daysEditText.setText(daysSpec)
        binding.vibrateCheckBox.isChecked = vibrate
        binding.soundDropdown.setText(displayLabelForSoundType(soundType), false)
        renderTime()

        val warningSuffix = if (warnings.isEmpty()) "" else " (${warnings.joinToString("; ")})"
        binding.statusText.text = "Received external request for ${formattedTime()}$warningSuffix"
        logDebug("External intent received for ${formattedTime()}$warningSuffix")

        if (autoLaunch) {
            val result = createAlarm(
                label = label,
                skipUi = skipUi,
                vibrate = vibrate,
                daysSpec = daysSpec,
                soundType = soundType,
                source = "intent",
                finishAfter = true
            )
            if (!result.ok) {
                Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderTime() {
        binding.timeEditText.setText(formattedTime())
    }

    private fun formattedTime(): String = LocalTime.of(selectedHour, selectedMinute).format(timeFormatter)

    private fun createAlarm(
        label: String,
        skipUi: Boolean,
        vibrate: Boolean,
        daysSpec: String,
        soundType: String,
        source: String,
        finishAfter: Boolean = false
    ): AlarmCreateResult {
        if (skipUi) {
            logDebug("skipUi requested but ignored in native mode")
        }

        val result = scheduleNativeAlarm(
            hour = selectedHour,
            minute = selectedMinute,
            label = label,
            daysSpec = daysSpec,
            vibrate = vibrate,
            soundType = soundType,
            source = source
        )

        if (result.ok) {
            binding.statusText.text = result.message
            Toast.makeText(this, "Native alarm scheduled", Toast.LENGTH_SHORT).show()
            if (finishAfter) finish()
        } else {
            binding.statusText.text = result.message
        }
        return result
    }

    private fun scheduleNativeAlarm(
        hour: Int,
        minute: Int,
        label: String,
        daysSpec: String,
        vibrate: Boolean,
        soundType: String,
        source: String
    ): AlarmCreateResult {
        return try {
            val parsedDays = parseDaysSpec(daysSpec)
            val safeLabel = label.trim().ifBlank { "Koi Alarm" }.take(MAX_LABEL_LENGTH)
            val alarmId = UUID.randomUUID().toString()

            val alarm = NativeAlarm(
                id = alarmId,
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                label = safeLabel,
                repeatDays = parsedDays.days,
                vibrate = vibrate,
                soundType = normalizeSoundType(soundType),
                enabled = true,
                source = source
            )

            NativeAlarmStore.upsert(this, alarm)
            val triggerAt = NativeAlarmScheduler.schedule(this, alarm)
            val human = NativeAlarmScheduler.formatTriggerForUi(triggerAt)

            val suffixes = mutableListOf<String>()
            if (parsedDays.days.isNotEmpty()) suffixes += "repeats: ${parsedDays.days.joinToString(",")}"
            if (parsedDays.unknownTokens.isNotEmpty()) suffixes += "ignored: ${parsedDays.unknownTokens.joinToString(",")}"
            if (label.trim().length > MAX_LABEL_LENGTH) suffixes += "label truncated to $MAX_LABEL_LENGTH chars"
            val suffix = if (suffixes.isEmpty()) "" else " (${suffixes.joinToString("; ")})"

            val message = "Scheduled native alarm for $human$suffix"
            logDebug("$message [id=$alarmId source=$source sound=${alarm.soundType}]")
            runOnUiThread { renderSavedAlarms() }

            AlarmCreateResult(
                ok = true,
                message = message,
                triggerAt = triggerAt,
                alarmId = alarmId
            )
        } catch (t: Throwable) {
            val msg = "Schedule failed: ${t.javaClass.simpleName}: ${t.message}"
            logDebug(msg)
            AlarmCreateResult(ok = false, message = msg)
        }
    }

    private fun parseDaysSpec(daysSpec: String): ParsedDays {
        if (daysSpec.isBlank()) return ParsedDays(emptyList(), emptyList())

        val days = linkedSetOf<Int>()
        val unknown = mutableListOf<String>()

        val tokens = daysSpec
            .split(',', ';', ' ', '|')
            .map { it.trim() }
            .filter { it.isNotBlank() }

        tokens.forEach { token ->
            val normalized = token.lowercase(Locale.getDefault())
            val numeric = normalized.toIntOrNull()

            when {
                numeric != null && numeric in 1..7 -> days += numeric
                DAY_ALIAS_TO_INDEX.containsKey(normalized) -> days += DAY_ALIAS_TO_INDEX.getValue(normalized)
                normalized.contains('-') -> {
                    val rangeParts = normalized.split('-').map { it.trim() }
                    if (rangeParts.size == 2) {
                        val start = parseDayToken(rangeParts[0])
                        val end = parseDayToken(rangeParts[1])
                        if (start != null && end != null) {
                            if (start <= end) {
                                (start..end).forEach { days += it }
                            } else {
                                (start..7).forEach { days += it }
                                (1..end).forEach { days += it }
                            }
                        } else {
                            unknown += token
                        }
                    } else {
                        unknown += token
                    }
                }
                else -> unknown += token
            }
        }

        return ParsedDays(days.toList().sorted(), unknown)
    }

    private fun parseDayToken(token: String): Int? {
        val normalized = token.lowercase(Locale.getDefault())
        return normalized.toIntOrNull()?.takeIf { it in 1..7 }
            ?: DAY_ALIAS_TO_INDEX[normalized]
    }

    private fun readRaw(intent: Intent, data: android.net.Uri?, extraKey: String, queryKey: String): String? {
        val fromExtras = intent.extras?.get(extraKey)?.toString()
        val fromQuery = data?.getQueryParameter(queryKey)
        return fromExtras ?: fromQuery
    }

    private fun parseHour(raw: String?, fallback: Int): Pair<Int, String?> {
        if (raw.isNullOrBlank()) return fallback to null
        val parsed = raw.toIntOrNull() ?: return fallback to "invalid hour '$raw', using $fallback"
        val clamped = parsed.coerceIn(0, 23)
        return clamped to if (clamped != parsed) "hour clamped to $clamped" else null
    }

    private fun parseMinute(raw: String?, fallback: Int): Pair<Int, String?> {
        if (raw.isNullOrBlank()) return fallback to null
        val parsed = raw.toIntOrNull() ?: return fallback to "invalid minute '$raw', using $fallback"
        val clamped = parsed.coerceIn(0, 59)
        return clamped to if (clamped != parsed) "minute clamped to $clamped" else null
    }

    private fun parseBoolean(raw: String?, default: Boolean): Boolean {
        if (raw.isNullOrBlank()) return default
        return when (raw.trim().lowercase(Locale.getDefault())) {
            "1", "true", "yes", "y", "on" -> true
            "0", "false", "no", "n", "off" -> false
            else -> default
        }
    }

    private fun selectedSoundType(): String {
        val selectedLabel = binding.soundDropdown.text?.toString().orEmpty().trim()
        return SOUND_OPTIONS.firstOrNull { it.label.equals(selectedLabel, ignoreCase = true) }?.value ?: "alarm"
    }

    private fun normalizeSoundType(raw: String?): String {
        val normalized = raw.orEmpty().trim().lowercase(Locale.getDefault())
        return when (normalized) {
            "alarm", "ringtone", "notification" -> normalized
            else -> "alarm"
        }
    }

    private fun displayLabelForSoundType(soundType: String): String {
        val value = normalizeSoundType(soundType)
        return SOUND_OPTIONS.firstOrNull { it.value == value }?.label ?: SOUND_OPTIONS.first().label
    }

    private fun renderSavedAlarms() {
        val alarms = NativeAlarmStore.list(this).sortedWith(
            compareBy<NativeAlarm> { !it.enabled }
                .thenBy { it.hour }
                .thenBy { it.minute }
                .thenBy { it.createdAt }
        )

        binding.alarmsContainer.removeAllViews()

        if (alarms.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No alarms yet. Create one above."
                textSize = 13f
            }
            binding.alarmsContainer.addView(empty)
            return
        }

        alarms.forEach { alarm ->
            binding.alarmsContainer.addView(buildAlarmCard(alarm))
        }
    }

    private fun buildAlarmCard(alarm: NativeAlarm): MaterialCardView {
        val card = MaterialCardView(this).apply {
            radius = 20f
            strokeWidth = 2
            setCardBackgroundColor(Color.parseColor("#251F33"))
            strokeColor = Color.parseColor("#4A3F63")
            cardElevation = 0f
            useCompatPadding = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = 14
            layoutParams = lp
        }

        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
        }

        val time = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute)
        val repeat = if (alarm.repeatDays.isEmpty()) "One-time" else "Repeat: ${alarm.repeatDays.joinToString(",")}"
        val next = NativeAlarmScheduler.formatTriggerForUi(
            NativeAlarmScheduler.computeNextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDays)
        )

        val title = TextView(this).apply {
            text = "$time — ${alarm.label}"
            textSize = 17f
            setTextColor(Color.parseColor("#F4EEFF"))
        }

        val meta = TextView(this).apply {
            text = "$repeat | Sound: ${displayLabelForSoundType(alarm.soundType)} | ${if (alarm.enabled) "Enabled" else "Disabled"}\nNext: $next"
            textSize = 12f
            setTextColor(Color.parseColor("#CAB8E6"))
            setPadding(0, 6, 0, 0)
        }

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, 12, 0, 0)
        }

        val toggle = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = if (alarm.enabled) "Disable" else "Enable"
            setOnClickListener { toggleAlarm(alarm) }
        }

        val delete = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Delete"
            setOnClickListener { deleteAlarm(alarm) }
            setPadding(20, paddingTop, 20, paddingBottom)
        }

        actions.addView(toggle)
        actions.addView(delete)

        wrap.addView(title)
        wrap.addView(meta)
        wrap.addView(actions)
        card.addView(wrap)
        return card
    }

    private fun toggleAlarm(alarm: NativeAlarm) {
        val updated = alarm.copy(enabled = !alarm.enabled)
        if (updated.enabled) {
            val trigger = NativeAlarmScheduler.schedule(this, updated)
            logDebug("Alarm re-enabled ${alarm.id}, next=${NativeAlarmScheduler.formatTriggerForUi(trigger)}")
        } else {
            NativeAlarmScheduler.cancel(this, updated.id)
            logDebug("Alarm disabled ${alarm.id}")
        }
        NativeAlarmStore.upsert(this, updated)
        renderSavedAlarms()
    }

    private fun deleteAlarm(alarm: NativeAlarm) {
        NativeAlarmScheduler.cancel(this, alarm.id)
        NativeAlarmStore.remove(this, alarm.id)
        logDebug("Alarm deleted ${alarm.id}")
        renderSavedAlarms()
    }

    private fun showTab(tab: Tab): Boolean {
        binding.createTab.visibility = if (tab == Tab.CREATE) android.view.View.VISIBLE else android.view.View.GONE
        binding.alarmsTab.visibility = if (tab == Tab.ALARMS) android.view.View.VISIBLE else android.view.View.GONE
        binding.bridgeTab.visibility = if (tab == Tab.BRIDGE) android.view.View.VISIBLE else android.view.View.GONE
        if (tab == Tab.ALARMS) renderSavedAlarms()
        return true
    }

    private fun showDebugMenu() {
        val options = arrayOf("View logs", "Copy logs", "Clear logs", "Open exact alarm settings")
        AlertDialog.Builder(this)
            .setTitle("Debug menu")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showLogsDialog()
                    1 -> copyLogsToClipboard()
                    2 -> clearLogs()
                    3 -> openExactAlarmSettings()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                logDebug("Opened exact alarm settings")
            } catch (t: Throwable) {
                logDebug("Failed to open exact alarm settings: ${t.message}")
            }
        } else {
            Toast.makeText(this, "Not required on this Android version", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogsDialog() {
        val logs = getLogsText()
        val view = TextView(this).apply {
            text = logs
            setPadding(32, 24, 32, 24)
            textSize = 12f
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
        }

        AlertDialog.Builder(this)
            .setTitle("Debug logs")
            .setView(view)
            .setPositiveButton("Close", null)
            .setNeutralButton("Copy") { _, _ -> copyLogsToClipboard() }
            .show()
    }

    private fun copyLogsToClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = getLogsText()
        clipboard.setPrimaryClip(ClipData.newPlainText("KoiAlarmLogs", text))
        Toast.makeText(this, "Logs copied", Toast.LENGTH_SHORT).show()
        logDebug("Logs copied to clipboard")
    }

    private fun clearLogs() {
        synchronized(debugLogs) { debugLogs.clear() }
        logDebug("Logs cleared")
        Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
    }

    private fun getLogsText(): String {
        val snapshot = synchronized(debugLogs) { debugLogs.toList() }
        return if (snapshot.isEmpty()) "No logs yet." else snapshot.joinToString("\n")
    }

    private fun logDebug(message: String) {
        val stamp = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()).format(LocalTime.now())
        val line = "[$stamp] $message"
        synchronized(debugLogs) {
            debugLogs.addFirst(line)
            while (debugLogs.size > MAX_DEBUG_LOG_LINES) debugLogs.removeLast()
        }
    }

    private fun updateBridgeStatus(text: String) {
        val historySuffix = if (recentBridgeEvents.isEmpty()) "" else "\nLast: ${recentBridgeEvents.first()}"
        binding.bridgeStatusText.text = "$text$historySuffix"
    }

    private fun recordBridgeEvent(summary: String) {
        val stamp = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault()).format(LocalTime.now())
        recentBridgeEvents.addFirst("[$stamp] $summary")
        while (recentBridgeEvents.size > 10) recentBridgeEvents.removeLast()
        runOnUiThread { updateBridgeStatus("Bridge running at ${bridgeUrl()}") }
    }

    private fun bridgeUrl(): String {
        val ip = localIpv4Address() ?: "<phone-ip>"
        return "http://$ip:$BRIDGE_PORT/set"
    }

    private fun localIpv4Address(): String? {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            interfaces
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { addr -> !addr.isLoopbackAddress && addr is Inet4Address }
                ?.hostAddress
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        const val ACTION_SET_ALARM = "ai.koi.alarmhelper.action.SET_ALARM"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DAYS = "days"
        const val EXTRA_SKIP_UI = "skipUi"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_SOUND_TYPE = "soundType"
        const val EXTRA_AUTO_LAUNCH = "autoLaunch"

        const val MAX_LABEL_LENGTH = 80
        const val MAX_DEBUG_LOG_LINES = 300
        const val BRIDGE_PORT = 8765

        const val REQ_POST_NOTIFICATIONS = 1001

        private val DAY_ALIAS_TO_INDEX = mapOf(
            "sun" to 1, "sunday" to 1,
            "mon" to 2, "monday" to 2,
            "tue" to 3, "tues" to 3, "tuesday" to 3,
            "wed" to 4, "wednesday" to 4,
            "thu" to 5, "thur" to 5, "thurs" to 5, "thursday" to 5,
            "fri" to 6, "friday" to 6,
            "sat" to 7, "saturday" to 7
        )

        private val SOUND_OPTIONS = listOf(
            SoundOption("System alarm", "alarm"),
            SoundOption("System ringtone", "ringtone"),
            SoundOption("System notification", "notification")
        )
    }

    enum class Tab {
        CREATE,
        ALARMS,
        BRIDGE
    }

    data class SoundOption(
        val label: String,
        val value: String
    )

    data class ParsedDays(
        val days: List<Int>,
        val unknownTokens: List<String>
    )

    data class AlarmCreateResult(
        val ok: Boolean,
        val message: String,
        val triggerAt: Long? = null,
        val alarmId: String? = null
    )
}
