package com.example.smartstick

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID


class MainActivity : AppCompatActivity() {

    lateinit var bluetoothManager: BluetoothManager
    lateinit var bluetoothAdapter: BluetoothAdapter

    private lateinit var connectThread: ConnectThread


    private var toneGenerator: ToneGenerator? = null
    private lateinit var audioManager: AudioManager

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_ADMIN,
            android.Manifest.permission.BLUETOOTH_SCAN
        )
    } else {
        arrayOf(
            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.BLUETOOTH_ADMIN,
        )
    }

    val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var adapter: ArrayAdapter<BluetoothDevice>

    var handler = object :Handler(Looper.getMainLooper()){
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)


            val data = msg.data
            var numBytes = data.getInt("numBytes")
            var bytearray = data.getByteArray("ByteArray")

        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this,android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
            // Request the permissions
            ActivityCompat.requestPermissions(this, requiredPermissions , 0);
        } else {
            // The permissions have already been granted
        }

        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, ToneGenerator.MAX_VOLUME * 100)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevices)
        val listView = findViewById<ListView>(R.id.ListView)
        listView.adapter = adapter

        listView.setOnItemClickListener { parent, view, position, id ->
            val device = discoveredDevices[position]
            connectThread = ConnectThread(device)
            connectThread.start()
        }

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = bluetoothManager.getAdapter()
        if (bluetoothAdapter == null) {
            Log.d("My app" , "This device does not support Bluetooth")
        }
        else{
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                var REQUEST_ENABLE_BT: Int = 0
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
            bluetoothAdapter.startDiscovery();
        }

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        pairedDevices?.forEach { device ->
            val deviceName = device.name
            val deviceHardwareAddress = device.address // MAC address
        }

        Log.d("My App" , "Working MainActivity!")
    }

    private val receiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action: String? = intent.action
            when(action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    val device: BluetoothDevice? =
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    val deviceName = device!!.name
                    val deviceHardwareAddress = device!!.address // MAC address
                    Log.d("My app" , deviceName)

                    discoveredDevices.add(device)
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()


        toneGenerator?.release()
        connectThread.cancel()
        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver)
    }


    inner class ConnectThread(device: BluetoothDevice) : Thread() {

        lateinit var service: ConnectedThread
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
        }

        public override fun run() {

            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter?.cancelDiscovery()

            mmSocket?.let { socket ->
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                runOnUiThread(Runnable {
                    Toast.makeText(
                        this@MainActivity,
                        "Connecting!",
                        Toast.LENGTH_SHORT
                    ).show()
                })
                socket.connect()
                runOnUiThread(Runnable {
                    Toast.makeText(
                        this@MainActivity,
                        "Connected!",
                        Toast.LENGTH_LONG
                    ).show()
                })
                // The connection attempt succeeded. Perform work associated with
                // the connection in a separate thread.
                manageMyConnectedSocket(socket)
            }
        }

        private fun manageMyConnectedSocket(socket: BluetoothSocket) {
            service = ConnectedThread(socket)
            service.run()
        }

        // Closes the client socket and causes the thread to finish.
        fun cancel() {
            try {
                service.cancel()
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e("My App", "Could not close the client socket", e)
            }
        }

        private val TAG = "MY_APP_DEBUG_TAG"

        val MESSAGE_READ: Int = 0

        inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {

            private val mmInStream: InputStream = mmSocket.inputStream
            private val mmBuffer: ByteArray = ByteArray(1024) // mmBuffer store for the stream

            override fun run() {
                var numBytes: Int // bytes returned from read()

                // Keep listening to the InputStream until an exception occurs.
                while (true) {
                    // Read from the InputStream.
                    numBytes = try {
                        mmInStream.read(mmBuffer)
                    } catch (e: IOException) {
                        Log.e(TAG, "Input stream was disconnected", e)
                        break
                    }

                    val msg: Message = handler.obtainMessage(MESSAGE_READ)
                    val bundle = Bundle()
                    bundle.putInt("numBytes" , numBytes)
                    bundle.putByteArray("ByteArray" , mmBuffer)
                    msg.data = bundle

                    msg.sendToTarget()

                    var ch = numBytes
                    var aa = mmBuffer.decodeToString(0 , 1)
                    Log.d("MYMYA PAP OSJDOIDO" , aa)

                    //TODO("ToneGenerator and Button Press")
                    if (aa == "0"){
                        toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2000)
                    }
                }
            }

            // Call this method from the main activity to shut down the connection.
            fun cancel() {
                try {
                    mmSocket.close()
                } catch (e: IOException) {
                    Log.e(TAG, "Could not close the connect socket", e)
                }
            }
        }
    }
}