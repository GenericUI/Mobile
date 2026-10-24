package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl

/**
 * Android `com.google.android.material.slider.Slider` implementation of [RangeSliderNativeImpl].
 */
class AndroidRangeSliderImpl : AndroidBaseNativeImpl("com.google.android.material.slider.Slider", "form-range"), RangeSliderNativeImpl<AndroidViewHandle> {
    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 1.0
    private var currentVal: Double = 0.0
    private var onValueChangedListener: ((Double) -> Unit)? = null

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max
        stepVal = step
        currentVal = currentVal.coerceIn(minVal, maxVal)
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
