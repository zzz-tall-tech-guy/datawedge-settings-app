package com.example.dwsettings

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/**
 * Minimal "scanner settings" screen. Each toggle sends a DataWedge SET_CONFIG intent that
 * updates ONE parameter on ONE existing DataWedge profile.
 *
 * EDIT THE TWO THINGS BELOW:
 *  1. PROFILE_NAME  - the DataWedge profile your users' app (browser etc.) is associated with.
 *  2. SETTINGS      - the list of toggles to show. One line per setting.
 *
 * The parameter names/values below are PLACEHOLDERS taken from Zebra's SET_CONFIG docs.
 * Verify each one against the DataWedge version on your scanners before relying on it:
 * https://techdocs.zebra.com/datawedge/latest/guide/api/setconfig/
 */
class MainActivity : Activity() {

    private data class Setting(
        val label: String,
        val plugin: String,      // DataWedge plug-in: BARCODE, KEYSTROKE, INTENT...
        val param: String,       // parameter name inside that plug-in
        val onValue: String,
        val offValue: String,
        val defaultOn: Boolean
    )

    companion object {
        const val PROFILE_NAME = "WebScanning"   // <-- change to your real profile name

        const val DW_ACTION = "com.symbol.datawedge.api.ACTION"
        const val DW_RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION"
        const val DW_PACKAGE = "com.symbol.datawedge"

        private val SETTINGS = listOf(
            Setting("Vibrate on scan", "BARCODE", "decode_haptic_feedback", "true", "false", true),
            Setting("Code 128", "BARCODE", "decoder_code128", "true", "false", true),
            Setting("Code 39", "BARCODE", "decoder_code39", "true", "false", true),
            Setting("QR code", "BARCODE", "decoder_qrcode", "true", "false", true),
            Setting("Data Matrix", "BARCODE", "decoder_datamatrix", "true", "false", true),
            Setting("UPC-A", "BARCODE", "decoder_upca", "true", "false", true),
            Setting("EAN-13", "BARCODE", "decoder_ean13", "true", "false", true),
            // 13 = send Enter (carriage return) after the scanned text, 0 = send nothing
            Setting("Press Enter after scan", "KEYSTROKE", "keystroke_action_char", "13", "0", false)
        )
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
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

        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        root.addView(TextView(this).apply {
            text = "Scanner settings"
            textSize = 22f
        })

        SETTINGS.forEach { s ->
            root.addView(Switch(this).apply {
                text = s.label
                textSize = 18f
                setPadding(0, pad, 0, pad)
                isChecked = prefs.getBoolean(s.param, s.defaultOn)
                setOnCheckedChangeListener { _, on ->
                    prefs.edit().putBoolean(s.param, on).apply()
                    send(s, on)
                }
            })
        }

        root.addView(Button(this).apply {
            text = "Re-apply all settings"
            setOnClickListener {
                SETTINGS.forEach { s -> send(s, prefs.getBoolean(s.param, s.defaultOn)) }
            }
        })

        status = TextView(this).apply {
            gravity = Gravity.START
            setPadding(0, pad, 0, 0)
        }
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

    /** Update a single parameter on the existing profile; other settings are left untouched. */
    private fun send(s: Setting, on: Boolean) {
        val params = Bundle().apply { putString(s.param, if (on) s.onValue else s.offValue) }
        val plugin = Bundle().apply {
            putString("PLUGIN_NAME", s.plugin)
            putString("RESET_CONFIG", "false")
            putBundle("PARAM_LIST", params)
        }
        val main = Bundle().apply {
            putString("PROFILE_NAME", PROFILE_NAME)
            // CREATE_IF_NOT_EXIST: if the profile exists, only the listed parameters are changed
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
