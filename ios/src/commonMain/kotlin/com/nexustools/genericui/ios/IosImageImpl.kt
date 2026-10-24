package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl

/**
 * iOS `UIImageView` implementation of [ImageNativeImpl].
 */
class IosImageImpl : IosBaseNativeImpl("UIImageView", "img-fluid"), ImageNativeImpl<IosNativeView> {
    private var sourceUrl: String? = null
    private var objectFit: ObjectFit = ObjectFit.CONTAIN

    override fun setSourceUrl(url: String?) {
        sourceUrl = url
        if (url != null) nativeWidget.metadata["sourceUrl"] = url else nativeWidget.metadata.remove("sourceUrl")
    }

    override fun getSourceUrl(): String? = sourceUrl

    override fun setFallbackText(fallback: String?) {
        nativeWidget.accessibilityIdentifier = fallback ?: ""
    }

    override fun setObjectFit(fit: ObjectFit) {
        objectFit = fit
        nativeWidget.metadata["contentMode"] = fit.name
    }

    override fun getObjectFit(): ObjectFit = objectFit
}
