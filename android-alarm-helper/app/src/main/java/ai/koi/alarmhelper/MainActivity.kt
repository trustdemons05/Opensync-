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

        val hour = intent.getIntExtra(EXTRA_HOUR, data?.getQueryParameter("hour")?.toIntOrNull() ?: selectedHour)
        val minute = intent.getIntExtra(EXTRA_MINUTE, data?.getQueryParameter("minute")?.toIntOrNull() ?: selectedMinute)
        val label = intent.getStringExtra(EXTRA_LABEL)
            ?: data?.getQueryParameter("label")
            ?: binding.labelEditText.text?.toString().orEmpty()
        val daysSpec = intent.getStringExtra(EXTRA_DAYS)
            ?: data?.getQueryParameter("days")
            ?: ""
        val skipUi = intent.getBooleanExtra(
            EXTRA_SKIP_UI,
            data?.getQueryParameter("skipUi")?.equals("true", ignoreCase = true) == true
        )
        val vibrate = intent.getBooleanExtra(
            EXTRA_VIBRATE,
            data?.getQueryParameter("vibrate")?.equals("false", ignoreCase = true) != true
        )
        val autoLaunch = intent.getBooleanExtra(
            EXTRA_AUTO_LAUNCH,
            data?.getQueryParameter("autoLaunch")?.equals("true", ignoreCase = true) != false
        )

        selectedHour = hour.coerceIn(0, 23)
        selectedMinute = minute.coerceIn(0, 59)
        binding.labelEditText.setText(label)
        binding.daysEditText.setText(daysSpec)
        binding.skipUiCheckBox.isChecked = skipUi
        binding.vibrateCheckBox.isChecked = vibrate
        renderTime()
        binding.statusText.text = "Received external request for ${formattedTime()}"

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
        val repeatDays = parseDaysSpec(daysSpec)

        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, selectedHour)
            putExtra(AlarmClock.EXTRA_MINUTES, selectedMinute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Koi Alarm" })
            putExtra(AlarmClock.EXTRA_SKIP_UI, skipUi)
            putExtra(AlarmClock.EXTRA_VIBRATE, vibrate)
            if (repeatDays.isNotEmpty()) {
                putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(repeatDays))
            }
        }

        try {
            startActivity(alarmIntent)
            val repeatSuffix = if (repeatDays.isEmpty()) "" else " (repeats: ${repeatDays.joinToString(",")})"
            binding.statusText.text = "Sent alarm request for ${formattedTime()}$repeatSuffix"
            Toast.makeText(this, "Opening clock app for ${formattedTime()}", Toast.LENGTH_SHORT).show()
            if (finishAfter) finish()
        } catch (_: ActivityNotFoundException) {
            binding.statusText.text = "No compatible clock app found on this device."
            Toast.makeText(this, "No compatible clock app found", Toast.LENGTH_LONG).show()
        }
    }

    private fun parseDaysSpec(daysSpec: String): List<Int> {
        if (daysSpec.isBlank()) return emptyList()

        // Android Calendar constants: 1=Sun, 2=Mon, ... 7=Sat
        return daysSpec
            .split(",", " ", ";")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in 1..7 }
            .distinct()
            .sorted()
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
    }
}
