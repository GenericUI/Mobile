@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISlider
import kotlin.math.round

/**
 * Native UIKit `UISlider` implementation of [RangeSliderNativeImpl].
 */
class IosRangeSliderImpl(
    val slider: UISlider = UISlider(frame = CGRectMake(0.0, 0.0, 220.0, 34.0))
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(slider),
    componentClassPrefix = "range-slider"
), RangeSliderNativeImpl<UikitViewHandle> {

    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 0.0
    private var currentVal: Double = 0.0
    private var listener: ((Double) -> Unit)? = null

    init {
        slider.minimumValue = minVal.toFloat()
        slider.maximumValue = maxVal.toFloat()
        slider.value = currentVal.toFloat()
        slider.continuous = true
        slider.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_VALUE_CHANGED,
            forControlEvents = UIControlEventValueChanged
        )
        actionTarget.onValueChanged = {
            val stepped = quantize(slider.value.toDouble())
            slider.value = stepped.toFloat()
            if (stepped != currentVal) {
                currentVal = stepped
                listener?.invoke(stepped)
            }
        }
    }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max.coerceAtLeast(min)
        stepVal = step.coerceAtLeast(0.0)
        slider.minimumValue = minVal.toFloat()
        slider.maximumValue = maxVal.toFloat()
        setValue(currentVal)
    }

    override fun setValue(value: Double) {
        val quantized = quantize(value)
        currentVal = quantized
        slider.value = quantized.toFloat()
    }

    override fun getValue(): Double = currentVal

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        this.listener = listener
    }

    override fun onBootstrapAppearanceChanged() {
        slider.minimumTrackTintColor = null
    }

    private fun quantize(raw: Double): Double {
        val clamped = raw.coerceIn(minVal, maxVal)
        if (stepVal <= 0.0) return clamped
        val steps = round((clamped - minVal) / stepVal)
        return (minVal + steps * stepVal).coerceIn(minVal, maxVal)
    }
}
