package com.example.mousetouch

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    companion object {
        private const val MOUSE_REPORT_ID = 1
        const val LEFT_CLICK = 1
        const val RIGHT_CLICK = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_SCAN] != true || permissions[Manifest.permission.BLUETOOTH] != true) {
            Toast
                .makeText(this, "Permissions denied. Can't scan for Bluetooth devices.", Toast.LENGTH_LONG)
                .show()
        }
    }

    private var isLeftButtonHeld = false
    private var isDoubleTapDetected = false
    private val doubleTapDelay = 300L
    private val gestureListener = createGestureListener()

    private fun createGestureListener(): GestureDetector.SimpleOnGestureListener {
        return object : GestureDetector.SimpleOnGestureListener() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            @RequiresApi(Build.VERSION_CODES.P)
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isLeftButtonHeld) {
                    handler.postDelayed({
                        if (!isDoubleTapDetected) sendMouseClick(LEFT_CLICK)
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
                sendMouseReport(LEFT_CLICK, 0, 0, 0) // Left button down
                isLeftButtonHeld = true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                isDoubleTapDetected = false
                return super.onSingleTapConfirmed(e)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresApi(Build.VERSION_CODES.S)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
        initializeBluetooth()

        val layout = findViewById<View>(R.id.touchpadView)

        val gestureDetector = GestureDetector(this, gestureListener)

        layout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            handleTouchEvent(event)
            true
        }

        setupHidDevice()
    }

    private var lastX = 0f
    private var lastY = 0f
    private var initialY = 0f
    private val speedMultiplier = 0.5f
    private val scrollThreshold = 20f

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isLeftButtonHeld) {
                    if (event.pointerCount == 2) {
                        val deltaY = event.y - initialY
                        if (deltaY > scrollThreshold) {
                            sendMouseScroll(-1) // Scroll down
                            initialY = event.y
                        } else if (deltaY < -scrollThreshold) {
                            sendMouseScroll(1) // Scroll up
                            initialY = event.y
                        }
                    } else {
                        fun roundTowardsLarger(value: Float, movementThreshold: Float): Int {
                            // Dynamically adjust rounding sensitivity based on the movement size (velocity)
                            return if (value > 0) {
                                // For positive values, round up if the fractional part is >= movementThreshold
                                if (value - floor(value) >= movementThreshold) ceil(value).toInt() else floor(value).toInt()
                            } else {
                                // For negative values, round down if the fractional part is <= -movementThreshold
                                if (abs(value - floor(value)) >= movementThreshold) floor(value).toInt() else ceil(value).toInt()
                            }
                        }
                        val dxRaw = (event.x - lastX) * speedMultiplier
                        val dyRaw = (event.y - lastY) * speedMultiplier

                        // Adjust the movement threshold based on how much the mouse is moving
                        val movementThreshold = if (abs(dxRaw) > 1 || abs(dyRaw) > 1) 1f else 0.4f  // Adjusted for faster movements

//                        val dx = ((event.x - lastX) * speedMultiplier).toInt().coerceIn(-127, 127)
//                        val dy = ((event.y - lastY) * speedMultiplier).toInt().coerceIn(-127, 127)
//                        val dx = roundTowardsLarger(((event.x - lastX) * speedMultiplier)).coerceIn(-127, 127)
//                        val dy = roundTowardsLarger(((event.y - lastY) * speedMultiplier)).coerceIn(-127, 127)

                        // Apply custom rounding with dynamic threshold
                        val dx = roundTowardsLarger(dxRaw, movementThreshold).coerceIn(-127, 127)
                        val dy = roundTowardsLarger(dyRaw, movementThreshold).coerceIn(-127, 127)

                        sendMouseReport(0, dx, dy, 0)
                        lastX = event.x
                        lastY = event.y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isLeftButtonHeld) {
                    sendMouseReport(0, 0, 0, 0) // Release left button
                    isLeftButtonHeld = false
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionsLauncher.launch(arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_CONNECT
            ))
        }
    }

    private fun initializeBluetooth() {
        bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth needs to be enabled", Toast.LENGTH_SHORT).show()
            return
        }
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

    @RequiresApi(Build.VERSION_CODES.P)
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
}