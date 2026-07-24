package com.remote.controller

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnSettings: Button
    private lateinit var llPermissionLayout: LinearLayout
    private lateinit var llRemoteControl: LinearLayout
    private lateinit var tvConnectedDevice: TextView

    private var nsdHelper: NsdHelper? = null
    private var discoveryDialog: AlertDialog? = null
    private var remoteClient: RemoteClient? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvStatus = findViewById(R.id.tvAccessibilityStatus)
        btnSettings = findViewById(R.id.btnOpenSettings)
        llPermissionLayout = findViewById(R.id.llPermissionLayout)
        llRemoteControl = findViewById(R.id.llRemoteControl)
        tvConnectedDevice = findViewById(R.id.tvConnectedDevice)

        btnSettings.setOnClickListener {
            showPermissionInstructions()
        }

        setupRemoteButtons()

        nsdHelper = NsdHelper(this)
        startDiscoveryWithTimeout()
    }

    private fun setupRemoteButtons() {
        findViewById<Button>(R.id.btnBack).setOnClickListener { remoteClient?.sendCommand("BACK") }
        findViewById<Button>(R.id.btnHome).setOnClickListener { remoteClient?.sendCommand("HOME") }
        findViewById<Button>(R.id.btnRecents).setOnClickListener { remoteClient?.sendCommand("RECENTS") }
    }

    private fun startDiscoveryWithTimeout() {
        var found = false
        nsdHelper?.discoverServices(object : NsdHelper.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!found) {
                    found = true
                    handler.post { showDiscoveryDialog(serviceInfo) }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
        })

        // If no device found in 2 seconds, stop discovery to save battery/resources
        handler.postDelayed({
            if (!found) {
                nsdHelper?.stopDiscovery()
            }
        }, 2000)
    }

    private fun showDiscoveryDialog(serviceInfo: NsdServiceInfo) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_discovery, null)
        val llDeviceItem = view.findViewById<LinearLayout>(R.id.llDeviceItem)
        val tvName = view.findViewById<TextView>(R.id.tvDeviceName)
        val tvIp = view.findViewById<TextView>(R.id.tvDeviceIp)

        tvName.text = serviceInfo.serviceName
        tvIp.text = serviceInfo.host.hostAddress
        llDeviceItem.visibility = View.VISIBLE

        discoveryDialog = AlertDialog.Builder(this)
            .setView(view)
            .setNegativeButton(R.string.dialog_cancel) { _, _ -> nsdHelper?.stopDiscovery() }
            .create()

        llDeviceItem.setOnClickListener {
            connectToDevice(serviceInfo)
            discoveryDialog?.dismiss()
        }

        discoveryDialog?.show()
    }

    private fun connectToDevice(serviceInfo: NsdServiceInfo) {
        remoteClient = RemoteClient(serviceInfo.host.hostAddress ?: "", serviceInfo.port)
        remoteClient?.connect { success ->
            handler.post {
                if (success) {
                    llPermissionLayout.visibility = View.GONE
                    llRemoteControl.visibility = View.VISIBLE
                    tvConnectedDevice.text = "Connected to: ${serviceInfo.serviceName}"
                }
            }
        }
    }

    private fun showPermissionInstructions() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_permission_title)
            .setMessage(R.string.dialog_permission_message)
            .setPositiveButton(R.string.dialog_open_settings) { _, _ ->
                openAccessibilitySettings()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun openAccessibilitySettings() {
        val serviceComponent = ComponentName(packageName, RemoteAccessibilityService::class.java.name).flattenToString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                putExtra("android.intent.extra.COMPONENT_NAME", serviceComponent)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                fallbackToGeneralSettings()
            }
        } else {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                val fragmentKey = ":settings:fragment_args_key"
                val fragmentBundleKey = ":settings:show_fragment_args"

                val args = Bundle().apply {
                    putString(fragmentKey, serviceComponent)
                }

                putExtra(fragmentKey, serviceComponent)
                putExtra(fragmentBundleKey, args)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                fallbackToGeneralSettings()
            }
        }
    }

    private fun fallbackToGeneralSettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        nsdHelper?.stopDiscovery()
        remoteClient?.disconnect()
    }

    private fun updateStatus() {
        if (isAccessibilityServiceEnabled(this, RemoteAccessibilityService::class.java)) {
            tvStatus.text = getString(R.string.status_enabled)
            tvStatus.setTextColor(Color.GREEN)
            btnSettings.isEnabled = false
        } else {
            tvStatus.text = getString(R.string.status_disabled)
            tvStatus.setTextColor(Color.RED)
            btnSettings.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
