package com.example.wifimonitor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WifiViewModel : ViewModel() {
    private val _savedNetworks = MutableStateFlow<Set<String>>(setOf())
    val savedNetworks: StateFlow<Set<String>> = _savedNetworks

    fun addNetwork(ssid: String) {
        _savedNetworks.value = _savedNetworks.value + ssid
    }

    fun removeNetwork(ssid: String) {
        _savedNetworks.value = _savedNetworks.value - ssid
    }
}

class MainActivity : ComponentActivity() {
    private lateinit var wifiManager: WifiManager
    private lateinit var viewModel: WifiViewModel
    private val CHANNEL_ID = "WifiMonitorChannel"
    private val PERMISSION_REQUEST_CODE = 123

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                checkForSavedNetworks()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        viewModel = WifiViewModel()
        createNotificationChannel()
        
        registerReceiver(wifiReceiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        
        setContent {
            WifiMonitorApp(viewModel, ::startScan)
        }
        
        requestPermissions()
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_WIFI_STATE,
                Manifest.permission.CHANGE_WIFI_STATE,
                Manifest.permission.POST_NOTIFICATIONS
            )
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "WiFi Monitor"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startScan() {
        if (checkPermissions()) {
            wifiManager.startScan()
        } else {
            Toast.makeText(this, "Missing required permissions", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkForSavedNetworks() {
        if (!checkPermissions()) return

        val scanResults = wifiManager.scanResults
        val savedNetworks = viewModel.savedNetworks.value
        
        for (network in scanResults) {
            if (savedNetworks.contains(network.SSID)) {
                showNotification(network.SSID)
            }
        }
    }

    private fun showNotification(ssid: String) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_wifi_lock)
            .setContentTitle("WiFi Network Found")
            .setContentText("$ssid is now in range")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        with(NotificationManagerCompat.from(this)) {
            if (ActivityCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
                notify(ssid.hashCode(), builder.build())
            }
        }
    }

    private fun checkPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(wifiReceiver)
    }
}

@Composable
fun WifiMonitorApp(viewModel: WifiViewModel, onScanClick: () -> Unit) {
    var newNetwork by remember { mutableStateOf("") }
    val savedNetworks by viewModel.savedNetworks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = newNetwork,
            onValueChange = { newNetwork = it },
            label = { Text("Enter WiFi SSID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    if (newNetwork.isNotEmpty()) {
                        viewModel.addNetwork(newNetwork)
                        newNetwork = ""
                    }
                }
            ) {
                Text("Add Network")
            }

            Button(onClick = onScanClick) {
                Text("Scan Now")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Monitored Networks:", style = MaterialTheme.typography.titleMedium)
        
        savedNetworks.forEach { ssid ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(ssid)
                IconButton(onClick = { viewModel.removeNetwork(ssid) }) {
                    Text("×")
                }
            }
        }
    }
}
