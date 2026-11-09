package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.widget.SeekBar
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import kotlin.math.roundToInt

/**
 * Android `android.widget.SeekBar` implementation of [RangeSliderNativeImpl].
 */
class AndroidRangeSliderImpl : AndroidBaseNativeImpl("com.google.android.material.slider.Slider", "form-range"), RangeSliderNativeImpl<AndroidViewHandle> {
    private var minVal: Double = 0.0
    private var maxVal: Double = 100.0
    private var stepVal: Double = 1.0
    private var currentVal: Double = 0.0
    private var onValueChangedListener: ((Double) -> Unit)? = null
    private var suppressSeekBarListener: Boolean = false

    init {
        nativeWidget.fillMaxWidth = true
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        SeekBar(context).apply {
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (suppressSeekBarListener) return
                    val step = stepVal.takeIf { it > 0.0 } ?: 1.0
                    val value = (minVal + progress * step).coerceIn(minVal, maxVal)
                    if (currentVal != value) {
                        currentVal = value
                        onValueChangedListener?.invoke(currentVal)
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }

    override fun setRange(min: Double, max: Double, step: Double) {
        minVal = min
        maxVal = max
        stepVal = step.takeIf { it > 0.0 } ?: 1.0
        currentVal = currentVal.coerceIn(minVal, maxVal)
        syncAndroidView()
    }

    override fun setValue(value: Double) {
        currentVal = value.coerceIn(minVal, maxVal)
        syncAndroidView()
        onValueChangedListener?.invoke(currentVal)
    }

    override fun getValue(): Double = currentVal

    override fun setOnValueChanged(listener: ((Double) -> Unit)?) {
        onValueChangedListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        runCatching {
            val sb = nativeWidget.androidView as? SeekBar ?: return@runCatching
            val step = stepVal.takeIf { it > 0.0 } ?: 1.0
            val stepsCount = (((maxVal - minVal) / step).roundToInt()).coerceAtLeast(1)
            val progressVal = (((currentVal - minVal) / step).roundToInt()).coerceIn(0, stepsCount)
            suppressSeekBarListener = true
            try {
                if (sb.max != stepsCount) {
                    sb.max = stepsCount
                }
                if (sb.progress != progressVal) {
                    sb.progress = progressVal
                }
            } finally {
                suppressSeekBarListener = false
            }
        }
    }
}
