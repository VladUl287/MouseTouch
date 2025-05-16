package com.example.mousetouch

import android.Manifest
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

class TouchpadMotionHandler(
    private val controller: BluetoothViewModel,
    private val touchListener: MouseGestureListener
) {
    private var lastX = 0f
    private var lastY = 0f
    private var initialY = 0f

    private val speedMultiplier = 0.5f
    private val scrollThreshold = 20f

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                    val dxRaw = (event.x - lastX) * speedMultiplier
                    val dyRaw = (event.y - lastY) * speedMultiplier

                    val movementThreshold = if (abs(dxRaw) > 1 || abs(dyRaw) > 1) 1f else 0.5f

//                        val dx = ((event.x - lastX) * speedMultiplier).toInt().coerceIn(-127, 127)
//                        val dy = ((event.y - lastY) * speedMultiplier).toInt().coerceIn(-127, 127)
//                        val dx = roundTowardsLarger(((event.x - lastX) * speedMultiplier)).coerceIn(-127, 127)
//                        val dy = roundTowardsLarger(((event.y - lastY) * speedMultiplier)).coerceIn(-127, 127)

                    val dx = roundTowardsLarger(dxRaw, movementThreshold).coerceIn(-127, 127)
                    val dy = roundTowardsLarger(dyRaw, movementThreshold).coerceIn(-127, 127)

                    controller.sendMouseReport(0, dx, dy, 0)
                    lastX = event.x
                    lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                touchListener.handleTouchRelease()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun onScrollEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - initialY
                if (abs(deltaY) > scrollThreshold) {
                    val direction = if (deltaY > 0) -1 else 1
                    controller.sendScroll(direction)
                    initialY = event.y
                }
            }
        }
    }

    private fun roundTowardsLarger(value: Float, threshold: Float): Int {
        return if (value > 0) {
            if (value - floor(value) >= threshold) ceil(value).toInt() else floor(value).toInt()
        } else {
            if (abs(value - floor(value)) >= threshold) floor(value).toInt() else ceil(value).toInt()
        }
    }
}
