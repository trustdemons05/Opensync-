package ai.koi.alarmhelper

import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import ai.koi.alarmhelper.databinding.ActivityMainBinding
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedHour: Int = 7
    private var selectedMinute: Int = 0
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        renderTime()
        setupUi()
        handleExternalIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    private fun setupUi() = with(binding) {
        pickTimeButton.setOnClickListener {
            TimePickerDialog(
                this@MainActivity,
                { _, hourOfDay, minute ->
                    selectedHour = hourOfDay
                    selectedMinute = minute
                    renderTime()
                    statusText.text = "Picked ${formattedTime()}"
                },
                selectedHour,
                selectedMinute,
                false
            ).show()
        }

        weekdaysButton.setOnClickListener {
            daysEditText.setText("mon,tue,wed,thu,fri")
            statusText.text = "Preset applied: weekdays"
        }

        dailyButton.setOnClickListener {
            daysEditText.setText("sun,mon,tue,wed,thu,fri,sat")
            statusText.text = "Preset applied: daily"
        }

        clearDaysButton.setOnClickListener {
            daysEditText.setText("")
            statusText.text = "Repeat days cleared"
        }

        setAlarmButton.setOnClickListener {
            createAlarm(
                label = labelEditText.text?.toString().orEmpty(),
                skipUi = skipUiCheckBox.isChecked,
                vibrate = vibrateCheckBox.isChecked,
                daysSpec = daysEditText.text?.toString().orEmpty()
            )
        }
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
        val autoLaunch = parseBoolean(
            readRaw(intent, data, EXTRA_AUTO_LAUNCH, "autoLaunch"),
            default = true
        )

        selectedHour = hour
        selectedMinute = minute
        binding.labelEditText.setText(label)
        binding.daysEditText.setText(daysSpec)
        binding.skipUiCheckBox.isChecked = skipUi
        binding.vibrateCheckBox.isChecked = vibrate
        renderTime()

        val warningSuffix = if (warnings.isEmpty()) "" else " (${warnings.joinToString("; ")})"
        binding.statusText.text = "Received external request for ${formattedTime()}$warningSuffix"

        if (autoLaunch) {
            createAlarm(
                label = label,
                skipUi = skipUi,
                vibrate = vibrate,
                daysSpec = daysSpec,
                finishAfter = true
            )
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
        finishAfter: Boolean = false
    ) {
        val parsedDays = parseDaysSpec(daysSpec)
        val safeLabel = label.trim().ifBlank { "Koi Alarm" }.take(MAX_LABEL_LENGTH)

        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, selectedHour)
            putExtra(AlarmClock.EXTRA_MINUTES, selectedMinute)
            putExtra(AlarmClock.EXTRA_MESSAGE, safeLabel)
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
            if (parsedDays.days.isNotEmpty()) {
                putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(parsedDays.days))
            }
        }

        try {
            startActivity(alarmIntent)
            val suffixes = mutableListOf<String>()
            if (parsedDays.days.isNotEmpty()) suffixes += "repeats: ${parsedDays.days.joinToString(",")}"
            if (parsedDays.unknownTokens.isNotEmpty()) suffixes += "ignored: ${parsedDays.unknownTokens.joinToString(",")}" 
            if (label.trim().length > MAX_LABEL_LENGTH) suffixes += "label truncated to $MAX_LABEL_LENGTH chars"

            val statusSuffix = if (suffixes.isEmpty()) "" else " (${suffixes.joinToString("; ")})"
            binding.statusText.text = "Sent alarm request for ${formattedTime()}$statusSuffix"
            Toast.makeText(this, "Opening clock app for ${formattedTime()}", Toast.LENGTH_SHORT).show()
            if (finishAfter) finish()
        } catch (_: ActivityNotFoundException) {
            binding.statusText.text = "No compatible clock app found on this device."
            Toast.makeText(this, "No compatible clock app found", Toast.LENGTH_LONG).show()
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
                                // wrap (e.g., fri-mon)
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

    companion object {
        const val ACTION_SET_ALARM = "ai.koi.alarmhelper.action.SET_ALARM"
        const val EXTRA_HOUR = "hour"
        const val EXTRA_MINUTE = "minute"
        const val EXTRA_LABEL = "label"
        const val EXTRA_DAYS = "days"
        const val EXTRA_SKIP_UI = "skipUi"
        const val EXTRA_VIBRATE = "vibrate"
        const val EXTRA_AUTO_LAUNCH = "autoLaunch"

        const val MAX_LABEL_LENGTH = 80

        private val DAY_ALIAS_TO_INDEX = mapOf(
            "sun" to 1, "sunday" to 1,
            "mon" to 2, "monday" to 2,
            "tue" to 3, "tues" to 3, "tuesday" to 3,
            "wed" to 4, "wednesday" to 4,
            "thu" to 5, "thur" to 5, "thurs" to 5, "thursday" to 5,
            "fri" to 6, "friday" to 6,
            "sat" to 7, "saturday" to 7
        )
    }

    data class ParsedDays(
        val days: List<Int>,
        val unknownTokens: List<String>
    )
}
