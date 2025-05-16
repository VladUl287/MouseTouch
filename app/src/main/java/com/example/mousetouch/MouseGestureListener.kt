package com.example.mousetouch

import android.Manifest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission

class MouseGestureListener(
    private val controller: BluetoothViewModel
) : GestureDetector.SimpleOnGestureListener() {

    private var isLeftButtonHeld = false
    private var isDoubleTapDetected = false
    private val doubleTapDelay = 300L
    private val handler = Handler(Looper.getMainLooper())

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (!isLeftButtonHeld) {
            handler.postDelayed({
                if (!isDoubleTapDetected) {
                    controller.sendClick(LEFT_CLICK)
                }
            }, doubleTapDelay)
        }
        return true
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDoubleTap(e: MotionEvent): Boolean {
        if (!isLeftButtonHeld) {
            controller.sendClick(RIGHT_CLICK)
        }
        isDoubleTapDetected = true
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        isDoubleTapDetected = false
        return super.onSingleTapConfirmed(e)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onLongPress(e: MotionEvent) {
        controller.sendMouseReport(LEFT_CLICK, 0, 0, 0) // Press and hold left button
        isLeftButtonHeld = true
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun handleTouchRelease() {
        if (isLeftButtonHeld) {
            controller.sendMouseReport(0, 0, 0, 0) // Release all buttons
            isLeftButtonHeld = false
        }
    }

    companion object {
        const val LEFT_CLICK = 1
        const val RIGHT_CLICK = 2
    }
}
