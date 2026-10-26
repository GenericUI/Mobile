package com.nexustools.genericui.mobile.gesture

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sign

/**
 * Shared mobile scroll deceleration spline and overscroll spring physics for `:android` and `:ios`
 * scroll views and virtualized lazy containers.
 */
class MobileFlingPhysics(
    val frictionCoef: Float = 0.015f,
    val springStiffness: Float = 220f,
    val springDampingRatio: Float = 1.0f
) {
    private val decelerationRate: Float = (ln(0.78) / ln(0.9)).toFloat()

    /**
     * Computes the total resting distance (in pixels) for an initial velocity (in px/s).
     */
    fun predictFlingDistancePx(initialVelocityPxPerSec: Float, densityDpi: Float = 160f): Float {
        val absVel = abs(initialVelocityPxPerSec)
        if (absVel < 1f) return 0f
        val ppi = densityDpi * 386.0878f * 0.84f
        val decel = ppi * frictionCoef
        val l = ln((0.35f * absVel) / decel)
        val distance = decel * exp((decelerationRate / (decelerationRate - 1.0f)) * l)
        return distance * sign(initialVelocityPxPerSec)
    }

    /**
     * Computes the total duration (in milliseconds) of a fling deceleration curve.
     */
    fun predictFlingDurationMs(initialVelocityPxPerSec: Float, densityDpi: Float = 160f): Int {
        val absVel = abs(initialVelocityPxPerSec)
        if (absVel < 1f) return 0
        val ppi = densityDpi * 386.0878f * 0.84f
        val decel = ppi * frictionCoef
        val l = ln((0.35f * absVel) / decel)
        return (1000.0f * exp(l / (decelerationRate - 1.0f))).toInt()
    }

    /**
     * Advances a 1D critically-damped overscroll spring by `deltaMs` and returns the updated `(position, velocity)`.
     */
    fun stepOverscrollSpring(
        currentOverscrollPx: Float,
        currentVelocityPxPerSec: Float,
        deltaMs: Double
    ): Pair<Float, Float> {
        val dt = (deltaMs / 1000.0).toFloat().coerceIn(0.001f, 0.05f)
        val damping = 2f * springDampingRatio * kotlin.math.sqrt(springStiffness)
        val acceleration = -springStiffness * currentOverscrollPx - damping * currentVelocityPxPerSec
        val nextVel = currentVelocityPxPerSec + acceleration * dt
        val nextPos = currentOverscrollPx + nextVel * dt
        return if (abs(nextPos) < 0.5f && abs(nextVel) < 5f) {
            0f to 0f
        } else {
            nextPos to nextVel
        }
    }
}
