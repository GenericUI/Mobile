package com.nexustools.genericui.android

import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl

/**
 * Android `androidx.appcompat.widget.AppCompatImageView` implementation of [ImageNativeImpl].
 */
class AndroidImageImpl : AndroidBaseNativeImpl("androidx.appcompat.widget.AppCompatImageView", "img-fluid"), ImageNativeImpl<AndroidViewHandle> {
    private var sourceUrl: String? = null
    private var objectFit: ObjectFit = ObjectFit.CONTAIN

    override fun setSourceUrl(url: String?) {
        sourceUrl = url
        if (url != null) {
            nativeWidget.metadata["imageUri"] = url
        } else {
            nativeWidget.metadata.remove("imageUri")
        }
    }

    override fun getSourceUrl(): String? = sourceUrl

    override fun setFallbackText(fallback: String?) {
        nativeWidget.contentDescription = fallback ?: ""
    }

    override fun setObjectFit(fit: ObjectFit) {
        objectFit = fit
        nativeWidget.metadata["scaleType"] = fit.name.lowercase()
    }

    override fun getObjectFit(): ObjectFit = objectFit
}
