package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl

/**
 * iOS `UISlider` implementation of [RangeSliderNativeImpl].
 */
class IosRangeSliderImpl : IosBaseNativeImpl("UISlider", "form-range"), RangeSliderNativeImpl<IosNativeView> {
    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 1.0
    private var currentVal: Double = 0.0
    private var onValueChangedListener: ((Double) -> Unit)? = null

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max
        stepVal = step
    }

    override fun setValue(value: Double) {
        currentVal = value.coerceIn(minVal, maxVal)
        onValueChangedListener?.invoke(currentVal)
    }

    override fun getValue(): Double = currentVal

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        onValueChangedListener = listener
    }
}
