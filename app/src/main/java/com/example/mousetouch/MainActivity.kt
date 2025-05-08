package com.example.mousetouch

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.example.mousetouch.ui.theme.MouseTouchTheme
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.log

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null

    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val MOUSE_REPORT_ID = 1
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            if (permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
                permissions[Manifest.permission.BLUETOOTH] == true
            ) {
                // Permissions are granted, proceed with Bluetooth scanning
            } else {
                // Permissions were denied
                Toast.makeText(this, "Permissions denied. Can't scan for Bluetooth devices.", Toast.LENGTH_LONG).show()
            }
        }

    var isLeftButtonHeld = false
    var isDoubleTapDetected = false

    private val doubleTapDelay = 300L // Adjust as needed to match your double-tap window
    private val handler = Handler(Looper.getMainLooper())
    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!isLeftButtonHeld) {
                handler.postDelayed({
                    if (!isDoubleTapDetected) {
                        sendMouseClick(LEFT_CLICK)
                    }
                }, doubleTapDelay)
            }
            return true
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!isLeftButtonHeld) {
                sendMouseClick(RIGHT_CLICK)
            }
            isDoubleTapDetected = true
            return true
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        @RequiresApi(Build.VERSION_CODES.P)
        override fun onLongPress(e: MotionEvent) {
            sendMouseReport(LEFT_CLICK, 0, 0, 0) // button down only
            isLeftButtonHeld = true
        }

        // Reset double-tap detection after a complete single tap is confirmed
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            isDoubleTapDetected = false
            return super.onSingleTapConfirmed(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }

        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }
        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this,  "Bluetooth need to be enabled", Toast.LENGTH_SHORT).show()
        }

        val layout = findViewById<View>(R.id.touchpadView)
//        layout.setOnTouchListener { v, event ->
//            when (event.action) {
//                MotionEvent.ACTION_MOVE -> {
//                    // Capture the touch coordinates
//                    val x = event.x
//                    val y = event.y
//                    // Send the x and y coordinates via Bluetooth to the Android TV
//                    sendMouseMovement(x, y)
//                }
//            }
//            true
//        }

        var lastX = 0f
        var lastY = 0f
        val speedMultiplier = 1.0f

        var lastReportTime = 0L
        val reportInterval = 10L // ms

// Variables for detecting tap and double tap
        var lastTapTime = 0L
        val doubleTapInterval = 300L // 300 ms threshold for double tap
        var lastTapX = 0f
        var lastTapY = 0f

        var initialX = 0f
        var initialY = 0f
        var isScrolling = false
        var scrollThreshold = 20f

        var gestureDetector = GestureDetector(this, gestureListener)

        layout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            // Handle movement manually
            if (event.action == MotionEvent.ACTION_MOVE && !isLeftButtonHeld) {
                if(event.pointerCount == 2) {
                        val deltaY = event.y - initialY
                        if (deltaY > scrollThreshold) {
                            sendMouseScroll(-1) // Scroll down
                            initialY = event.y
                        } else if (deltaY < -scrollThreshold) {
                            sendMouseScroll(1) // Scroll up
                            initialY = event.y
                        }
                } else {
                    val dx = ((event.x - lastX) * speedMultiplier).toInt().coerceIn(-127, 127)
                    val dy = ((event.y - lastY) * speedMultiplier).toInt().coerceIn(-127, 127)
                    sendMouseReport(0, dx, dy, 0)
                    lastX = event.x
                    lastY = event.y
                }
            }

            if (event.action == MotionEvent.ACTION_UP && isLeftButtonHeld) {
                // Release left button on finger lift
                sendMouseReport(0, 0, 0, 0)
                isLeftButtonHeld = false
            }

            true
        }

//        layout.setOnTouchListener { _, event ->
//            when (event.action) {
//                // Finger down (touch started)
//                MotionEvent.ACTION_DOWN -> {
//                    lastX = event.x
//                    lastY = event.y
//                    sendMouseClick(LEFT_CLICK)
//                }
//
//                // Finger moved (gesture)
//                MotionEvent.ACTION_MOVE -> {
//                    val now = System.currentTimeMillis()
//                    if(event.pointerCount == 2) {
//                        val deltaY = event.y - initialY
//                        if (deltaY > scrollThreshold) {
//                            sendMouseScroll(-1) // Scroll down
//                            initialY = event.y
//                        } else if (deltaY < -scrollThreshold) {
//                            sendMouseScroll(1) // Scroll up
//                            initialY = event.y
//                        }
//                    } else {
//                        if (now - lastReportTime > reportInterval) {
//                            val dx = ((event.x - lastX) * speedMultiplier).toInt().coerceIn(-127, 127)
//                            val dy = ((event.y - lastY) * speedMultiplier).toInt().coerceIn(-127, 127)
//
//                            // Send mouse movement if there is any movement
//                            if (dx != 0 || dy != 0) {
//                                sendMouseMovement(dx, dy)
//                                lastX = event.x
//                                lastY = event.y
//                                lastReportTime = now
//                            }
//                        }
//                    }
//                }
//
//                // Finger lifted (tap or click)
//                MotionEvent.ACTION_UP -> {
//                    val now = System.currentTimeMillis()
//
//                    // Detect if it's a double-tap or single tap
//                    if (now - lastTapTime < doubleTapInterval) {
//                        // Double tap detected: Right click
////                        sendMouseClick(RIGHT_CLICK) // Right-click on double tap
//                    } else {
//                        // Single tap detected: Left click
////                        sendMouseClick(LEFT_CLICK) // Left-click on single tap
//                    }
//
//                    // Update last tap time and position for double tap detection
//                    lastTapTime = now
//                    lastTapX = event.x
//                    lastTapY = event.y
//                }
//            }
//            true
//        }

//        layout.setOnTouchListener { _, event ->
//            when (event.action) {
//                MotionEvent.ACTION_DOWN -> {
//                    lastX = event.x
//                    lastY = event.y
//                    sendMouseEvent(0, 0, 1) // Button 1 down
//                    Thread.sleep(50)
//                    sendMouseEvent(0, 0, 0) // Button release
//                }
//
//                MotionEvent.ACTION_MOVE -> {
//                    val dx = ((event.x - lastX) * speedMultiplier).toInt().coerceIn(-127, 127)
//                    val dy = ((event.y - lastY) * speedMultiplier).toInt().coerceIn(-127, 127)
//
//                    sendMouseMovement(dx.toFloat(), dy.toFloat())
//
//                    lastX = event.x
//                    lastY = event.y
//                }
//            }
//            true
//        }

        setupHidDevice()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendMouseClick(button: Int) {
        sendMouseReport(button, 0, 0, 0)
        Thread.sleep(100)
        sendMouseReport(0, 0, 0, 0)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        val report = byteArrayOf(
            buttons.toByte(),
            dx.toByte(),
            dy.toByte(),
            wheel.toByte()
        )
        bluetoothHidDevice?.sendReport(connectedDevice, MOUSE_REPORT_ID, report)
    }

    // Left-click button status
    val LEFT_CLICK = 1
    val RIGHT_CLICK = 2

    // Helper function to send mouse movement
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendMouseMovement(dx: Int, dy: Int) {
        sendMouseEvent(dx, dy, 0)  // No button pressed, just move
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMouseScroll(wheel: Int) {
        val report = byteArrayOf(
            0x00, // buttons
            0x00, // dx
            0x00, // dy
            wheel.toByte() // wheel scroll
        )
        bluetoothHidDevice?.sendReport(connectedDevice, MOUSE_REPORT_ID, report)
    }

    private fun setupHidDevice() {
        bluetoothAdapter?.getProfileProxy(
            this,
            @RequiresApi(Build.VERSION_CODES.P)
            object : BluetoothProfile.ServiceListener {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        bluetoothHidDevice = proxy as BluetoothHidDevice
                        registerHidDeviceApp()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        bluetoothHidDevice = null
                    }
                }
            },
            BluetoothProfile.HID_DEVICE
        )
    }

    private fun getHidDescriptor(): ByteArray {
        return byteArrayOf(
            0x05, 0x01,        // Usage Page (Generic Desktop)
            0x09, 0x02,        // Usage (Mouse)
            0xA1.toByte(), 0x01,  // Collection (Application)
            0x09, 0x01,        //   Usage (Pointer)
            0xA1.toByte(), 0x00,  //   Collection (Physical)
            0x05, 0x09,        //     Usage Page (Button)
            0x19, 0x01,        //     Usage Minimum (1)
            0x29, 0x03,        //     Usage Maximum (3)
            0x15, 0x00,        //     Logical Minimum (0)
            0x25, 0x01,        //     Logical Maximum (1)
            0x95.toByte(), 0x03,  //     Report Count (3 buttons)
            0x75, 0x01,        //     Report Size (1)
            0x81.toByte(), 0x02,  //     Input (Data,Var,Abs)
            0x95.toByte(), 0x01,  //     Report Count (1 padding)
            0x75, 0x05,        //     Report Size (5)
            0x81.toByte(), 0x01,  //     Input (Const,Array,Abs)
            0x05, 0x01,        //     Usage Page (Generic Desktop)
            0x09, 0x30,        //     Usage (X)
            0x09, 0x31,        //     Usage (Y)
            0x09, 0x38,        //     Usage (Wheel)
            0x15, 0x81.toByte(),  //     Logical Minimum (-127)
            0x25, 0x7F,        //     Logical Maximum (127)
            0x75, 0x08,        //     Report Size (8)
            0x95.toByte(), 0x03,  //     Report Count (3: X, Y, Wheel)
            0x81.toByte(), 0x06,  //     Input (Data,Var,Rel)
            0xC0.toByte(),        //   End Collection
            0xC0.toByte()         // End Collection
        )
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.P)
    private fun registerHidDeviceApp() {
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            "Android TV Mouse",
            "Remote mouse for Android TV",
            "Your Company",
            BluetoothHidDevice.SUBCLASS1_MOUSE,
//            byteArrayOf(0x00) // Descriptor
            getHidDescriptor()
        )

        bluetoothHidDevice?.registerApp(
            sdpSettings,
            null,
            null,
            Executors.newSingleThreadExecutor(),
            object : BluetoothHidDevice.Callback() {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
                    runOnUiThread {
                        connectedDevice = pluggedDevice
                        connectToPairedDevice()
                    }
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToPairedDevice() {
        bluetoothAdapter?.bondedDevices?.firstOrNull()?.let { device ->
            bluetoothHidDevice?.connect(device)
        } ?: run {
            Toast.makeText(this, "Please pair with your Android TV first", Toast.LENGTH_LONG).show()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                sendMouseEvent(0, 0, 0x01) // Left button down
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x.toInt().coerceIn(-127, 127)
                val dy = event.y.toInt().coerceIn(-127, 127)
                sendMouseEvent(dx, dy, 0x01) // Move with left button pressed
                true
            }
            MotionEvent.ACTION_UP -> {
                sendMouseEvent(0, 0, 0x00) // Button release
                true
            }
            else -> false
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMouseEvent(dx: Int, dy: Int, buttons: Int) {
        if (bluetoothHidDevice == null || connectedDevice == null) return

        val report = byteArrayOf(
            buttons.toByte(),
            dx.toByte(),
            dy.toByte()
        )
        bluetoothHidDevice?.sendReport(connectedDevice, MOUSE_REPORT_ID, report)

//        val report = byteArrayOf(0x00, 0x10, 0x10) // No buttons, X+16, Y+16
//        var res = bluetoothHidDevice?.sendReport(connectedDevice, 0, report)

//        val press = byteArrayOf(0x01, 0x00, 0x00) // Left click press
//        bluetoothHidDevice?.sendReport(connectedDevice, 0, press)
//        Thread.sleep(100)
//// Button 1 up
//        val release = byteArrayOf(0x00, 0x00, 0x00) // Left click release
//        bluetoothHidDevice?.sendReport(connectedDevice, 0, release)

        // Try different report formats
//        val formatsToTry = listOf(
//            // Standard 4-byte format
//            byteArrayOf(0, 67, 67, 0),
//
//            // 3-byte format (buttons, x, y)
//            byteArrayOf(0, 67, 67),
//
//            // With header byte
//            byteArrayOf(0x02, 0, 67, 67),
//
//            // Larger movement
//            byteArrayOf(0, 127, 127, 0),
//
//            // Negative movement
//            byteArrayOf(0, -50, -50, 0)
//        )
//
//        formatsToTry.forEachIndexed { index, report ->
//            val success = bluetoothHidDevice?.sendReport(connectedDevice, MOUSE_REPORT_ID, report)
//            Log.d("HID", "Sending: ${report.joinToString(", ") { it.toUByte().toString() }}")
//            Thread.sleep(200) // Small delay between attempts
//        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun checkHidConnection(): Boolean {
        return bluetoothHidDevice?.getConnectionState(connectedDevice) ==
                BluetoothProfile.STATE_CONNECTED
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onStart() {
        super.onStart()
//        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
//        registerReceiver(receiver, filter)
//        bluetoothAdapter?.startDiscovery()
    }

    private val receiver = object : BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(context: Context, intent: Intent) {
            val action: String = intent.action ?: return
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?: return
                val deviceName = device.name
                val deviceAddress = device.address
                // You can now connect to your Android TV device
                connectToDevice(device)
                Toast.makeText(this@MainActivity, "Device found: $deviceName", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private lateinit var bluetoothSocket: BluetoothSocket

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.P)
    private fun sendMouseMovement(x: Float, y: Float) {
        sendMouseEvent(x.toInt(), y.toInt(), 0)
//        val outputStream: OutputStream = bluetoothSocket.outputStream
//        outputStream.write(message.toByteArray())
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToDevice(device: BluetoothDevice) {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard Serial Port Profile UUID
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid)
            bluetoothSocket.connect()

            // If successful, you can now send and receive data
            val outputStream = bluetoothSocket.outputStream
            val inputStream = bluetoothSocket.inputStream

            // Implement reading from the input stream and sending data via output stream
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(receiver)
    }
}