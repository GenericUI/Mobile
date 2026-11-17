package com.nexustools.genericui.mobile.gesture

import com.nexustools.genericui.geometry.Point
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Shared multi-touch gesture recognizer and velocity tracker used by `:android` and `:ios` native widgets
 * and canvases.
 */
class MobileGestureRecognizer(
    val touchSlopPx: Float = 8f,
    val edgeSwipeThresholdPx: Float = 24f,
    val doubleTapTimeoutMs: Long = 300L,
    val longPressTimeoutMs: Long = 500L,
    val minimumFlingVelocityPxPerSec: Float = 250f
) {
    private data class PointerSample(
        val id: Int,
        var startX: Float,
        var startY: Float,
        var currentX: Float,
        var currentY: Float,
        var previousX: Float,
        var previousY: Float,
        val startTimestampMs: Long,
        var lastTimestampMs: Long,
        var longPressTriggered: Boolean = false
    )

    private val activePointers = linkedMapOf<Int, PointerSample>()
    private var lastTapTimestampMs: Long = 0L
    private var lastTapPoint: Point = Point.ZERO
    private var isPanning: Boolean = false
    private var initialPinchDistance: Float = 0f
    private var initialRotationRadians: Float = 0f
    var lastVelocityX: Float = 0f
        private set
    var lastVelocityY: Float = 0f
        private set

    var onGestureDetected: ((
        type: MobileGestureType,
        focalPoint: Point,
        deltaX: Float,
        deltaY: Float,
        scaleFactor: Float,
        rotationDeltaRadians: Float
    ) -> Unit)? = null

    var onLongPress: ((x: Float, y: Float) -> Unit)? = null

    fun onPointerDown(pointerId: Int, x: Float, y: Float, timestampMs: Long) {
        activePointers[pointerId] = PointerSample(
            id = pointerId,
            startX = x,
            startY = y,
            currentX = x,
            currentY = y,
            previousX = x,
            previousY = y,
            startTimestampMs = timestampMs,
            lastTimestampMs = timestampMs
        )
        if (activePointers.size == 1) {
            isPanning = false
            lastVelocityX = 0f
            lastVelocityY = 0f
        } else if (activePointers.size == 2) {
            val pts = activePointers.values.toList()
            initialPinchDistance = hypot(pts[1].currentX - pts[0].currentX, pts[1].currentY - pts[0].currentY)
            initialRotationRadians = atan2(pts[1].currentY - pts[0].currentY, pts[1].currentX - pts[0].currentX)
        }
    }

    fun onPointerMove(pointerId: Int, x: Float, y: Float, timestampMs: Long) {
        val sample = activePointers[pointerId] ?: return
        val dtSec = ((timestampMs - sample.lastTimestampMs).coerceAtLeast(1L)) / 1000f
        val dx = x - sample.currentX
        val dy = y - sample.currentY
        sample.previousX = sample.currentX
        sample.previousY = sample.currentY
        sample.currentX = x
        sample.currentY = y
        sample.lastTimestampMs = timestampMs

        if (activePointers.size == 1) {
            lastVelocityX = dx / dtSec
            lastVelocityY = dy / dtSec
            val totalDist = hypot(x - sample.startX, y - sample.startY)
            if (!isPanning && totalDist >= touchSlopPx) {
                isPanning = true
                if (sample.startX <= edgeSwipeThresholdPx && (x - sample.startX) > touchSlopPx) {
                    onGestureDetected?.invoke(MobileGestureType.EDGE_SWIPE_BACK, Point(x, y), dx, dy, 1f, 0f)
                    return
                }
            }
            if (isPanning) {
                onGestureDetected?.invoke(MobileGestureType.PAN, Point(x, y), dx, dy, 1f, 0f)
            } else if (!sample.longPressTriggered && (timestampMs - sample.startTimestampMs) >= longPressTimeoutMs) {
                sample.longPressTriggered = true
                onGestureDetected?.invoke(MobileGestureType.LONG_PRESS, Point(x, y), 0f, 0f, 1f, 0f)
                onLongPress?.invoke(x, y)
            }
        } else if (activePointers.size >= 2) {
            val pts = activePointers.values.toList()
            val p0 = pts[0]
            val p1 = pts[1]
            val focal = Point((p0.currentX + p1.currentX) * 0.5f, (p0.currentY + p1.currentY) * 0.5f)
            val dist = hypot(p1.currentX - p0.currentX, p1.currentY - p0.currentY)
            val angle = atan2(p1.currentY - p0.currentY, p1.currentX - p0.currentX)
            val scale = if (initialPinchDistance > 1f) dist / initialPinchDistance else 1f
            val rotDelta = angle - initialRotationRadians
            initialPinchDistance = dist
            initialRotationRadians = angle
            onGestureDetected?.invoke(MobileGestureType.PINCH_ZOOM, focal, dx, dy, scale, rotDelta)
            if (kotlin.math.abs(rotDelta) > 0.01f) {
                onGestureDetected?.invoke(MobileGestureType.TWO_FINGER_ROTATE, focal, dx, dy, scale, rotDelta)
            }
        }
    }

    fun onPointerUp(pointerId: Int, x: Float, y: Float, timestampMs: Long) {
        val sample = activePointers.remove(pointerId) ?: return
        if (activePointers.isEmpty()) {
            val focal = Point(x, y)
            if (isPanning) {
                val speed = hypot(lastVelocityX, lastVelocityY)
                if (speed >= minimumFlingVelocityPxPerSec) {
                    onGestureDetected?.invoke(MobileGestureType.FLING, focal, lastVelocityX, lastVelocityY, 1f, 0f)
                }
            } else {
                val totalDist = hypot(x - sample.startX, y - sample.startY)
                if (totalDist < touchSlopPx) {
                    val holdDuration = timestampMs - sample.startTimestampMs
                    if (!sample.longPressTriggered && holdDuration >= longPressTimeoutMs) {
                        sample.longPressTriggered = true
                        onGestureDetected?.invoke(MobileGestureType.LONG_PRESS, focal, 0f, 0f, 1f, 0f)
                        onLongPress?.invoke(x, y)
                    } else if (!sample.longPressTriggered) {
                        val sinceLastTap = timestampMs - lastTapTimestampMs
                        val tapDist = hypot(x - lastTapPoint.x, y - lastTapPoint.y)
                        if (sinceLastTap in 1..doubleTapTimeoutMs && tapDist < touchSlopPx * 3f) {
                            lastTapTimestampMs = 0L
                            onGestureDetected?.invoke(MobileGestureType.DOUBLE_TAP, focal, 0f, 0f, 1f, 0f)
                        } else {
                            lastTapTimestampMs = timestampMs
                            lastTapPoint = focal
                            onGestureDetected?.invoke(MobileGestureType.TAP, focal, 0f, 0f, 1f, 0f)
                        }
                    }
                }
            }
            isPanning = false
        }
    }

    fun triggerLongPressIfStationary(pointerId: Int) {
        val sample = activePointers[pointerId] ?: return
        if (!isPanning && activePointers.size == 1 && !sample.longPressTriggered) {
            sample.longPressTriggered = true
            onGestureDetected?.invoke(
                MobileGestureType.LONG_PRESS,
                Point(sample.currentX, sample.currentY),
                0f,
                0f,
                1f,
                0f
            )
            onLongPress?.invoke(sample.currentX, sample.currentY)
        }
    }

    fun performLongPress(x: Float = 0f, y: Float = 0f) {
        onGestureDetected?.invoke(MobileGestureType.LONG_PRESS, Point(x, y), 0f, 0f, 1f, 0f)
        onLongPress?.invoke(x, y)
    }

    fun reset() {
        activePointers.clear()
        isPanning = false
        lastVelocityX = 0f
        lastVelocityY = 0f
    }
}
