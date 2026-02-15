package com.daricheh.app.ui

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.daricheh.app.MeshApplication
import com.daricheh.app.R
import com.daricheh.app.model.Peer
import com.daricheh.app.service.MeshService

class SettingsActivity : AppCompatActivity() {

    private var meshService: MeshService? = null
    private var isBound = false
    private val app = MeshApplication.instance

    // View references - nullable برای جلوگیری از crash
    private var toolbar: Toolbar? = null
    private var tvProfileName: TextView? = null
    private var tvProfilePhone: TextView? = null
    private var tvDeviceId: TextView? = null
    private var tvVersion: TextView? = null
    private var switchBluetooth: Switch? = null
    private var switchWifiDirect: Switch? = null
    private var switchAutoDiscover: Switch? = null
    private var btnScanNow: Button? = null
    private var btnViewLogs: Button? = null

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            try {
                meshService = (service as MeshService.MeshBinder).getService()
                isBound = true
                updateDevices()
                meshService?.onPeerListChanged = { peers -> runOnUiThread { showDevices(peers) } }
            } catch (e: Exception) { app.log("Settings: error: ${e.message}") }
        }
        override fun onServiceDisconnected(name: ComponentName?) { meshService = null; isBound = false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_settings)
            initViews()
            setupUI()
            bindService(Intent(this, MeshService::class.java), serviceConn, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            app.log("Settings: onCreate error: ${e.message}")
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvProfileName = findViewById(R.id.tvProfileName)
        tvProfilePhone = findViewById(R.id.tvProfilePhone)
        tvDeviceId = findViewById(R.id.tvDeviceId)
        tvVersion = findViewById(R.id.tvVersion)
        switchBluetooth = findViewById(R.id.switchBluetooth)
        switchWifiDirect = findViewById(R.id.switchWifiDirect)
        switchAutoDiscover = findViewById(R.id.switchAutoDiscover)
        btnScanNow = findViewById(R.id.btnScanNow)
        btnViewLogs = findViewById(R.id.btnViewLogs)
    }

    private fun setupUI() {
        toolbar?.title = "تنظیمات"
        toolbar?.setNavigationOnClickListener { finish() }

        tvProfileName?.text = app.username
        tvProfilePhone?.text = app.phoneNumber
        tvDeviceId?.text = "شناسه: ${app.deviceId}"

        switchBluetooth?.isChecked = app.bluetoothEnabled
        switchWifiDirect?.isChecked = app.wifiDirectEnabled
        switchAutoDiscover?.isChecked = app.autoDiscoverEnabled

        switchBluetooth?.setOnCheckedChangeListener { _, c -> app.bluetoothEnabled = c }
        switchWifiDirect?.setOnCheckedChangeListener { _, c -> app.wifiDirectEnabled = c }
        switchAutoDiscover?.setOnCheckedChangeListener { _, c -> app.autoDiscoverEnabled = c }

        btnScanNow?.setOnClickListener {
            if (isBound) {
                meshService?.discoverPeers()
                Toast.makeText(this, "در حال اسکن...", Toast.LENGTH_SHORT).show()
            }
        }

        btnViewLogs?.setOnClickListener { showLogs() }

        try {
            val ver = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            tvVersion?.text = "نسخه $ver"
        } catch (_: Exception) {
            tvVersion?.text = "نسخه 1.0"
        }
    }

    private fun showLogs() {
        val text = app.getLogsText()
        if (text.isBlank()) {
            Toast.makeText(this, "لاگی نیست", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("لاگ‌ها")
            .setMessage(text)
            .setPositiveButton("کپی") { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Logs", text))
                Toast.makeText(this, "کپی شد ✓", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("پاک") { _, _ -> app.clearLogs() }
            .setNegativeButton("بستن", null)
            .show()
    }

    private fun updateDevices() {
        // Simplified - no need for complex view handling
        try {
            val peers = meshService?.getDiscoveredPeers() ?: emptyList()
            // Show count in toast for now
            if (peers.isNotEmpty()) {
                Toast.makeText(this, "${peers.size} دستگاه یافت شد", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {}
    }

    private fun showDevices(peers: List<Peer>) {
        // Simplified
        if (peers.isNotEmpty()) {
            Toast.makeText(this, "${peers.size} دستگاه متصل", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { if (isBound) { unbindService(serviceConn); isBound = false } } catch (_: Exception) {}
    }
}