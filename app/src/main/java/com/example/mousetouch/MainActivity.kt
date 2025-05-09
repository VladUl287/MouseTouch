package com.example.mousetouch

import android.Manifest
import android.bluetooth.BluetoothManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.S)
class MainActivity : ComponentActivity() {

    private lateinit var bluetoothController: BluetoothHidMouseController
    private lateinit var gestureDetector: GestureDetector
    private lateinit var touchpadMotionHandler: TouchpadMotionHandler
    private lateinit var permissionHelper: PermissionHelper

    private var airMouseEnabled = false

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val permissionsNotGranted = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (!results.all { it.value }) {
                Toast.makeText(this, "Permissions denied. Can't proceed.", Toast.LENGTH_LONG).show()
            }
        }
        permissionHelper = PermissionHelper(this, permissionsNotGranted)
        permissionHelper.checkAndRequestPermissions()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothController = BluetoothHidMouseController(this, bluetoothManager)
        bluetoothController.initialize()

        val gestureListener = MouseGestureListener(bluetoothController)
        gestureDetector = GestureDetector(this, gestureListener)
        touchpadMotionHandler = TouchpadMotionHandler(bluetoothController, gestureListener)

        setupUi()
        setupAirMouseSensor()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupUi() {
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val btnOpenDrawer = findViewById<Button>(R.id.btnOpenDrawer)
        val drawerMenu = findViewById<LinearLayout>(R.id.drawerMenu)
        drawerMenu.layoutParams.width = resources.displayMetrics.widthPixels

        btnOpenDrawer.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<Switch>(R.id.switchEnableFeature).setOnCheckedChangeListener { _, isChecked ->
            airMouseEnabled = isChecked
        }

        findViewById<View>(R.id.touchpadView).setOnTouchListener { view, event ->
            gestureDetector.onTouchEvent(event)
            touchpadMotionHandler.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                view.performClick()
            }
            true
        }
//
//        findViewById<View>(R.id.touchpadView).setOnTouchListener { _, event ->
//            gestureDetector.onTouchEvent(event)
//            touchpadMotionHandler.onTouchEvent(event)
//            true
//        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupAirMouseSensor() {
        AirMouseSensorHelper(
            context = this,
            lifecycle = this.lifecycle,
            onMovement = { dx, dy ->
                lifecycleScope.launch {
                    if (airMouseEnabled) {
                        bluetoothController.sendMouseReport(0, dx, dy, 0)
                    }
                }
            }
        )
    }
}
