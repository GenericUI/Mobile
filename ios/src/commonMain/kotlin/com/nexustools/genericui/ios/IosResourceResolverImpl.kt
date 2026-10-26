package com.nexustools.genericui.ios

import com.nexustools.genericui.resources.DefaultResourceResolverNativeImpl
import com.nexustools.genericui.resources.ResourceID

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.resources.ResourceResolverNativeImpl]
 * backed by UIKit `NSBundle.mainBundle` and [DefaultResourceResolverNativeImpl].
 */
class IosResourceResolverImpl : DefaultResourceResolverNativeImpl() {

    override fun resolveUrl(resId: ResourceID): String =
        super.resolveUrl(resId)

    override fun resolveBytes(resId: ResourceID): ByteArray? =
        super.resolveBytes(resId)
}
