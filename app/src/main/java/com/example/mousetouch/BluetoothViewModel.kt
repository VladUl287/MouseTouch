package com.example.mousetouch

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Context.BLUETOOTH_SERVICE
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.concurrent.Executors

class BluetoothViewModel : ViewModel() {
    private val _pairedDevices = MutableLiveData<List<BluetoothDevice>>()
    val pairedDevices: LiveData<List<BluetoothDevice>> = _pairedDevices

    private val _connectedDevice = MutableLiveData<BluetoothDevice?>()
    val connectedDevice: LiveData<BluetoothDevice?> = _connectedDevice

    private var bluetoothHidDevice: BluetoothHidDevice? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun loadPairedDevices(context: Context) {
        val bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val devices = mutableListOf<BluetoothDevice>()

        bluetoothAdapter?.bondedDevices?.forEach { device ->
            devices.add(device)
        }
        _pairedDevices.value = devices
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun initializeHid(context: Context) {
        val bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHidDevice = proxy as BluetoothHidDevice
                    registerHidDeviceApp(context)
                }
            }
            override fun onServiceDisconnected(profile: Int) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    bluetoothHidDevice = null
                }
            }
        }, BluetoothProfile.HID_DEVICE)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun registerHidDeviceApp(context: Context) {
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
//                    connectToFirstPairedDevice(context)
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun connectToFirstPairedDevice(context: Context) {
        val bluetoothManager = context.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        val device = bluetoothAdapter?.bondedDevices?.firstOrNull()
        if (device != null) {
            connectToDevice(device)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        bluetoothHidDevice?.connect(device)
        _connectedDevice.value = device
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendClick(button: Int) {
        sendMouseReport(button, 0, 0, 0)
        Thread.sleep(100)
        sendMouseReport(0, 0, 0, 0)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendScroll(wheel: Int) {
        sendMouseReport(0, 0, 0, wheel)
    }

    companion object {
        private const val MOUSE_REPORT_ID = 1
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMouseReport(buttons: Int, dx: Int, dy: Int, wheel: Int) {
        if (connectedDevice.value == null || bluetoothHidDevice == null) {
            return
        }
        val report = byteArrayOf(
            buttons.toByte(),
            dx.toByte(),
            dy.toByte(),
            wheel.toByte()
        )
        bluetoothHidDevice?.sendReport(_connectedDevice.value, MOUSE_REPORT_ID, report)
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