package com.example.smartstick

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import com.google.firebase.database.ktx.database
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import java.io.IOException
import java.util.UUID
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    // Firebase
    private lateinit var functions: FirebaseFunctions
    private val database = Firebase.database
    private val coordRef = database.getReference("coord")

    // Bluetooth
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var connectThread: ConnectThread? = null
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private val deviceAddresses = HashSet<String>()

    // Audio
    private var toneGenerator: ToneGenerator? = null

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLatitude: Double = 0.0
    private var currentLongitude: Double = 0.0

    // UI
    private lateinit var deviceAdapter: ArrayAdapter<String>

    // Constants
    companion object {
        private const val TAG = "SmartStick"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val REQUEST_ENABLE_BT = 1
        private const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"
        private const val TONE_DURATION_MS = 2000
        private const val LOCATION_UPDATE_INTERVAL = 10000L
        private const val LOCATION_FASTEST_INTERVAL = 5000L

        private const val COMMAND_PLAY_TONE = "0"
        private const val COMMAND_SEND_LOCATION = "2"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeComponents()
        checkAndRequestPermissions()
        setupBluetooth()
        setupLocationTracking()
    }

    private fun initializeComponents() {
        // Firebase
        functions = Firebase.functions

        // Audio
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME)

        // UI - ListView for discovered devices
        deviceAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        findViewById<ListView>(R.id.ListView).apply {
            adapter = deviceAdapter
            setOnItemClickListener { _, _, position, _ ->
                connectToDevice(discoveredDevices[position])
            }
        }

        // Location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_SCAN)
            }
            if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                permissionsToRequest.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (!hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (!hasPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            this, permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setupBluetooth() {
        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.adapter

        if (!::bluetoothAdapter.isInitialized) {
            Log.e(TAG, "Bluetooth not supported on this device")
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        // Check if we have the necessary permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission")
                return
            }
        }

        if (!bluetoothAdapter.isEnabled) {
            requestEnableBluetooth()
            return
        }

        startBluetoothDiscovery()
    }

    @SuppressLint("MissingPermission")
    private fun requestEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery() {
        // Check permissions before starting discovery
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)) {
                Log.e(TAG, "Missing BLUETOOTH_SCAN permission")
                Toast.makeText(this, "Bluetooth scan permission required", Toast.LENGTH_SHORT)
                    .show()
                return
            }
        }

        // Register receiver for device discovery
        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(bluetoothReceiver, filter)

        // Start discovery
        val discoveryStarted = bluetoothAdapter.startDiscovery()
        if (discoveryStarted) {
            Log.d(TAG, "Bluetooth discovery started successfully")
            Toast.makeText(this, "Scanning for devices...", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "Failed to start Bluetooth discovery")
            Toast.makeText(this, "Failed to start scanning", Toast.LENGTH_SHORT).show()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device =
                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (deviceAddresses.add(it.address)) {
                            discoveredDevices.add(it)
                            val deviceName = it.name ?: "Unnamed Device"
                            Log.d(TAG, "Found device: $deviceName (${it.address})")
                            deviceAdapter.add(deviceName)
                            deviceAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        connectThread?.cancel()
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationTracking() {
        val locationRequest = LocationRequest.create().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = LOCATION_UPDATE_INTERVAL
            fastestInterval = LOCATION_FASTEST_INTERVAL
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLatitude = location.latitude
                    currentLongitude = location.longitude
                    Log.d(TAG, "Location updated: $currentLatitude, $currentLongitude")
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest, locationCallback, Looper.getMainLooper()
        )
    }

    private fun sendLocationToFirebase() {
        val locationData = hashMapOf(
            "longitude" to currentLongitude, "latitude" to currentLatitude
        )

        val randomId = Random.nextLong(Long.MAX_VALUE)
        val coordData = hashMapOf<String, Any?>(
            "clicked" to randomId, "lat" to currentLatitude, "long" to currentLongitude
        )

        coordRef.setValue(coordData).addOnSuccessListener {
                Log.d(TAG, "Location saved to Firebase: $coordData")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Failed to save location", e)
            }

        sendNotification(locationData)
    }

    private fun sendNotification(location: HashMap<String, Double>): Task<String> {
        val data = hashMapOf(
            "text" to location, "push" to true
        )

        return functions.getHttpsCallable("sendNotification").call(data).continueWith { task ->
                val result = task.result?.data as? String ?: ""
                Log.d(TAG, "Notification sent: $result")
                result
            }
    }

    private fun playTone() {
        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, TONE_DURATION_MS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Log.d(TAG, "All permissions granted")
                setupBluetooth()
                setupLocationTracking()
            } else {
                Log.w(TAG, "Some permissions were denied")
                Toast.makeText(this, "Permissions required for app to function", Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Log.d(TAG, "Bluetooth enabled")
                startBluetoothDiscovery()
            } else {
                Log.w(TAG, "Bluetooth enable request denied")
                Toast.makeText(this, "Bluetooth is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator?.release()
        connectThread?.cancel()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Receiver not registered", e)
        }
    }

    // Bluetooth Connection Thread
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID))
        }
        private var connectedThread: ConnectedThread? = null

        @SuppressLint("MissingPermission")
        override fun run() {
            bluetoothAdapter.cancelDiscovery()

            socket?.let { sock ->
                try {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connecting...", Toast.LENGTH_SHORT)
                            .show()
                    }

                    sock.connect()

                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connected!", Toast.LENGTH_LONG).show()
                    }

                    connectedThread = ConnectedThread(sock)
                    connectedThread?.start()

                } catch (e: IOException) {
                    Log.e(TAG, "Connection failed", e)
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Connection failed", Toast.LENGTH_SHORT)
                            .show()
                    }
                    try {
                        sock.close()
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close socket", closeException)
                    }
                }
            }
        }

        fun cancel() {
            connectedThread?.cancel()
            try {
                socket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close socket", e)
            }
        }
    }

    // Bluetooth Communication Thread
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream = socket.inputStream
        private val buffer = ByteArray(1024)

        @Volatile
        private var isRunning = true

        override fun run() {
            while (isRunning) {
                try {
                    val numBytes = inputStream.read(buffer)
                    if (numBytes > 0) {
                        val command = buffer.decodeToString(0, 1)
                        handleCommand(command)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Input stream disconnected", e)
                    break
                }
            }
        }

        private fun handleCommand(command: String) {
            Log.d(TAG, "Received command: $command")

            runOnUiThread {
                when (command) {
                    COMMAND_PLAY_TONE -> {
                        playTone()
                    }

                    COMMAND_SEND_LOCATION -> {
                        sendLocationToFirebase()
                    }

                    else -> {
                        Log.w(TAG, "Unknown command: $command")
                    }
                }
            }
        }

        fun cancel() {
            isRunning = false
            try {
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close socket", e)
            }
        }
    }
}