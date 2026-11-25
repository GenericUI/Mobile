package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom `AppCompatButton` view used by [AndroidStyledButtonImpl], pairing styled gradients,
 * borders, typography, and centered icon rendering with real-time [AndroidHardwareBackdropBlurController]
 * `backdrop-filter` glass compositing.
 */
class AndroidStyledButtonView(
    context: Context,
    private val implProvider: () -> AndroidStyledButtonImpl?
) : AppCompatButton(context) {

    internal val backdropController = AndroidHardwareBackdropBlurController(this)
    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isAllCaps = false
        gravity = Gravity.CENTER
        includeFontPadding = false
        minWidth = 0
        minimumWidth = 0
        setHorizontallyScrolling(false)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backdropController.release()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val impl = implProvider()
        if (impl != null && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
            val label = text?.toString().orEmpty()
            val leftIcon = compoundDrawables.getOrNull(0)
            val iconW = leftIcon?.bounds?.width()?.takeIf { it > 0 }
                ?: leftIcon?.intrinsicWidth?.takeIf { it > 0 }
                ?: 0
            if (label.isNotEmpty() || iconW > 0) {
                measurePaint.textSize = textSize
                measurePaint.typeface = typeface ?: Typeface.DEFAULT_BOLD
                val textW = if (label.isNotEmpty()) measurePaint.measureText(label) else 0f
                val gap = if (iconW > 0 && label.isNotEmpty()) AndroidViewHandle.dpToPx(6) else 0
                val safetyPx = AndroidViewHandle.dpToPx(4)
                val requiredW = (textW + iconW + gap + paddingLeft + paddingRight + safetyPx).roundToInt()
                if (requiredW > measuredWidth) {
                    setMeasuredDimension(resolveSize(requiredW, widthMeasureSpec), measuredHeight)
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
        if (implProvider()?.nativeWidget?.layerPassThrough == true) return false
        return super.dispatchTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        backdropController.drawBackdropIfActive(canvas)
        backdropController.drawOverlayDrawable(canvas)
        super.onDraw(canvas)
        val iconDrawable = implProvider()?.resolvedStyledIconDrawable
        if (iconDrawable != null && text.isNullOrEmpty() && width > 0 && height > 0) {
            val targetSize = (min(width, height) * 0.45f).roundToInt()
                .coerceIn(AndroidViewHandle.dpToPx(16), AndroidViewHandle.dpToPx(28))
            val left = (width - targetSize) / 2
            val top = (height - targetSize) / 2
            iconDrawable.setBounds(left, top, left + targetSize, top + targetSize)
            iconDrawable.draw(canvas)
        }
    }
}
