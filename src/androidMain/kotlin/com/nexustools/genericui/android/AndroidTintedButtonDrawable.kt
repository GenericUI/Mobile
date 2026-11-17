package com.nexustools.genericui.android

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable

/**
 * [BitmapDrawable] for tinted native button backgrounds that suppresses intrinsic bitmap dimensions
 * (`-1`) so high-DPI background bitmaps never inflate `TextView` measurement.
 */
class AndroidTintedButtonDrawable(
    resources: Resources,
    bitmap: Bitmap
) : BitmapDrawable(resources, bitmap) {
    override fun getIntrinsicWidth(): Int = -1
    override fun getIntrinsicHeight(): Int = -1
}
