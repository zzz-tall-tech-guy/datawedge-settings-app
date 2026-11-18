package com.example.dwsettings

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/**
 * Simple scanner settings screen. It exposes five DataWedge "Barcode input > Configure scanner
 * settings" options and nothing else. Each change sends a DataWedge SET_CONFIG intent that
 * updates only that parameter on one existing profile.
 *
 * CHANGE PROFILE_NAME below to the DataWedge profile your users' app/browser is associated with.
 *
 * Parameter names/values: https://techdocs.zebra.com/datawedge/latest/guide/api/setconfig/
 * Support for each parameter varies by scanner model; test on every model you deploy.
 */
class MainActivity : Activity() {

    /** value == null means "leave unchanged" (nothing is sent). */
    private class Choice(val label: String, val value: String?)

    companion object {
        const val PROFILE_NAME = "WebScanning"   // <-- change to your real profile name

        const val DW_ACTION = "com.symbol.datawedge.api.ACTION"
        const val DW_RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
        const val DW_PACKAGE = "com.symbol.datawedge"

        const val P_AIM_TIMER = "aim_timer"
        const val P_AIM_TYPE = "aim_type"
        const val P_PICKLIST = "picklist"
        const val P_AUDIO = "decode_audio_feedback_uri"
        const val P_HAPTIC = "decode_haptic_feedback"

        private val ALL_PARAMS = listOf(P_AIM_TIMER, P_AIM_TYPE, P_PICKLIST, P_AUDIO, P_HAPTIC)

        private val NO_CHANGE = Choice("(no change)", null)

        private val AIM_TYPES = listOf(
            NO_CHANGE,
            Choice("Trigger", "0"),
            Choice("Timed hold", "1"),
            Choice("Timed release", "2"),
            Choice("Press and release", "3"),
            Choice("Presentation", "4"),
            Choice("Continuous read", "5"),
            Choice("Press and sustain", "6"),
            Choice("Press and continue", "7"),
            Choice("Timed continuous", "8")
        )

        private val PICKLISTS = listOf(
            NO_CHANGE,
            Choice("Disabled", "0"),
            Choice("Hardware picklist", "1"),
            Choice("Software picklist", "2")
        )

        private val HAPTIC = listOf(
            NO_CHANGE,
            Choice("On", "true"),
            Choice("Off", "false")
        )
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private var pad = 0
    private var commandCounter = 0

    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val result = intent.getStringExtra("RESULT") ?: return
            val info = intent.getBundleExtra("RESULT_INFO")
            val detail = info?.keySet()?.joinToString(", ") { k -> "$k=${info.get(k)}" } ?: ""
            status.text = if (result == "SUCCESS") "Saved." else "DataWedge said $result $detail"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        pad = (16 * resources.displayMetrics.density).toInt()

        status = TextView(this).apply { setPadding(0, pad, 0, 0) }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Scanner settings"
            textSize = 22f
        })
        root.addView(TextView(this).apply {
            text = "Shows what you last set here. \"(no change)\" leaves the scanner as it is."
            setPadding(0, pad / 2, 0, 0)
        })

        addAimTimer(root)
        addChoice(root, "Aim type", P_AIM_TYPE, AIM_TYPES)
        addChoice(root, "Picklist mode", P_PICKLIST, PICKLISTS)
        addChoice(root, "Decode audio feedback", P_AUDIO, loadTones())
        addChoice(root, "Decode haptic feedback (vibrate)", P_HAPTIC, HAPTIC)

        root.addView(Button(this).apply {
            text = "Re-apply all settings"
            setOnClickListener { reapplyAll() }
        })
        root.addView(status)

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(DW_RESULT_ACTION).apply { addCategory(Intent.CATEGORY_DEFAULT) }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(resultReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(resultReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(resultReceiver)
    }

    private fun label(root: LinearLayout, title: String) {
        root.addView(TextView(this).apply {
            text = title
            textSize = 16f
            setPadding(0, pad, 0, 0)
        })
    }

    private fun addAimTimer(root: LinearLayout) {
        label(root, "Aim timer (0 - 60000)")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "no change"
            setText(prefs.getString(P_AIM_TIMER, ""))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val set = Button(this).apply {
            text = "Set"
            setOnClickListener {
                val n = input.text.toString().toIntOrNull()
                if (n == null || n !in 0..60000) {
                    status.text = "Enter a whole number from 0 to 60000."
                } else {
                    prefs.edit().putString(P_AIM_TIMER, n.toString()).apply()
                    sendParams(mapOf(P_AIM_TIMER to n.toString()))
                }
            }
        }
        row.addView(input)
        row.addView(set)
        root.addView(row)
    }

    private fun addChoice(root: LinearLayout, title: String, param: String, choices: List<Choice>) {
        label(root, title)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, choices.map { it.label }
        )
        val saved = prefs.getString(param, null)
        val start = choices.indexOfFirst { it.value == saved }.let { if (it < 0) 0 else it }
        spinner.setSelection(start, false)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val value = choices[pos].value
                if (value == null) {
                    prefs.edit().remove(param).apply()
                    return
                }
                if (value == prefs.getString(param, null)) return  // initial callback, nothing new
                prefs.edit().putString(param, value).apply()
                sendParams(mapOf(param to value))
            }

            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spinner)
    }

    /** "Default" (empty string resets the parameter) plus the device's notification sounds. */
    private fun loadTones(): List<Choice> {
        val list = mutableListOf(NO_CHANGE, Choice("Default", ""))
        try {
            val rm = RingtoneManager(this)
            rm.setType(RingtoneManager.TYPE_NOTIFICATION)
            val c = rm.cursor
            while (c.moveToNext()) {
                list.add(
                    Choice(
                        c.getString(RingtoneManager.TITLE_COLUMN_INDEX),
                        rm.getRingtoneUri(c.position).toString()
                    )
                )
            }
        } catch (e: Exception) {
            // No tone list available: only "(no change)" and "Default" are offered.
        }
        return list
    }

    private fun reapplyAll() {
        val values = ALL_PARAMS.mapNotNull { p -> prefs.getString(p, null)?.let { p to it } }.toMap()
        if (values.isEmpty()) {
            status.text = "Nothing to apply yet."
        } else {
            sendParams(values)
        }
    }

    /** Update the given BARCODE parameters on the existing profile; everything else is untouched. */
    private fun sendParams(values: Map<String, String>) {
        val params = Bundle().apply { values.forEach { (k, v) -> putString(k, v) } }
        val plugin = Bundle().apply {
            putString("PLUGIN_NAME", "BARCODE")
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", params)
        }
        val main = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            // CREATE_IF_NOT_EXIST: if the profile exists, only the listed parameters change
            putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
            putBundle("PLUGIN_CONFIG", plugin)
        }
        val intent = Intent().apply {
            action = DW_ACTION
            setPackage(DW_PACKAGE)
            putExtra("com.symbol.datawedge.api.SET_CONFIG", main)
            putExtra("SEND_RESULT", "true")
            putExtra("COMMAND_IDENTIFIER", "SET_${commandCounter++}")
        }
        sendBroadcast(intent)
        status.text = "Sending..."
    }
}
