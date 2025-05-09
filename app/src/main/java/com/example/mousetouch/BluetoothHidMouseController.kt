package com.example.mousetouch

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import java.util.concurrent.Executors

@RequiresApi(Build.VERSION_CODES.P)
class BluetoothHidMouseController(
    private val context: Context,
    bluetoothManager: BluetoothManager
) {
    companion object {
        private const val MOUSE_REPORT_ID = 1
    }

    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private var bluetoothHidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun initialize() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Bluetooth not available or not enabled", Toast.LENGTH_SHORT).show()
            return
        }

        bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
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
        }, BluetoothProfile.HID_DEVICE)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun sendClick(button: Int) {
        sendMouseReport(button, 0, 0, 0)
        Thread.sleep(100)
        sendMouseReport(0, 0, 0, 0)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun sendScroll(wheel: Int) {
        sendMouseReport(0, 0, 0, wheel)
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        val report = byteArrayOf(
            buttons.toByte(),
            dx.toByte(),
            dy.toByte(),
            wheel.toByte()
        )
        bluetoothHidDevice?.sendReport(connectedDevice, MOUSE_REPORT_ID, report)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
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
                override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
                    connectedDevice = device
                    if (registered && device == null) {
                        connectToPairedDevice()
                    }
                }
            }
        )
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToPairedDevice() {
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull()
        if (device != null) {
            bluetoothHidDevice?.connect(device)
        } else {
            Toast.makeText(context, "Please pair with your Android TV first", Toast.LENGTH_LONG).show()
        }
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
            0x95.toByte(), 0x03,  //     Report Count (3)
            0x75, 0x01,        //     Report Size (1)
            0x81.toByte(), 0x02,  //     Input (Data,Var,Abs)
            0x95.toByte(), 0x01,  //     Report Count (1)
            0x75, 0x05,        //     Report Size (5)
            0x81.toByte(), 0x01,  //     Input (Const,Array,Abs)
            0x05, 0x01,        //     Usage Page (Generic Desktop)
            0x09, 0x30,        //     Usage (X)
            0x09, 0x31,        //     Usage (Y)
            0x09, 0x38,        //     Usage (Wheel)
            0x15, 0x81.toByte(),  //     Logical Minimum (-127)
            0x25, 0x7F,        //     Logical Maximum (127)
            0x75, 0x08,        //     Report Size (8)
            0x95.toByte(), 0x03,  //     Report Count (3)
            0x81.toByte(), 0x06,  //     Input (Data,Var,Rel)
            0xC0.toByte(),        //   End Collection
            0xC0.toByte()         // End Collection
        )
    }
}
