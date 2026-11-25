@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.transition.ContainerAnimationFrameState
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSThread
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewContentMode
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Base UIKit container implementation of [AbstractContainerNativeImpl] supporting real child
 * `UIView` insertion/removal, `CALayer.shouldRasterize`, offscreen `CGBitmapContext` capture via
 * `CALayer.renderInContext`, and animated frame overlay presentation.
 */
open class IosBaseContainerNativeImpl(
    nativeWidget: UikitViewHandle = UikitViewHandle(UikitFlexContainerView()),
    componentClassPrefix: String = "container",
    initialTagName: String = ""
) : IosBaseNativeImpl(nativeWidget, componentClassPrefix, initialTagName), AbstractContainerNativeImpl<UikitViewHandle> {

    protected val childImpls: MutableList<NativeImpl<*>> = mutableListOf()
    private var animationOverlayView: UIImageView? = null
    private var batchDepth: Int = 0

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        val handle = childImpl.nativeWidget as? UikitViewHandle ?: return
        if (childImpls.contains(childImpl)) {
            childImpls.remove(childImpl)
            handle.uiView.removeFromSuperview()
        }
        val clampedIndex = if (index < 0 || index > childImpls.size) childImpls.size else index
        childImpls.add(clampedIndex, childImpl)
        val container = contentContainerView()
        container.insertSubview(handle.uiView, atIndex = clampedIndex.toLong())
        if (batchDepth == 0) {
            container.setNeedsLayout()
        }
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        childImpls.remove(childImpl)
        val handle = childImpl.nativeWidget as? UikitViewHandle ?: return
        handle.uiView.removeFromSuperview()
        (uiView as? UikitFlexContainerView)?.removeChildMetadata(handle.uiView)
        if (batchDepth == 0) {
            contentContainerView().setNeedsLayout()
        }
    }

    override fun clearChildrenNative() {
        for (child in childImpls.toList()) {
            val handle = child.nativeWidget as? UikitViewHandle
            handle?.uiView?.removeFromSuperview()
            if (handle != null) {
                (uiView as? UikitFlexContainerView)?.removeChildMetadata(handle.uiView)
            }
        }
        childImpls.clear()
        if (batchDepth == 0) {
            contentContainerView().setNeedsLayout()
        }
    }

    protected open fun contentContainerView(): UIView = uiView

    override fun beginBatchAttach() {
        batchDepth++
    }

    override fun endBatchAttach() {
        if (batchDepth > 0) batchDepth--
        if (batchDepth == 0) {
            contentContainerView().setNeedsLayout()
        }
    }

    override fun enableHardwareLayerCache(enabled: Boolean) {
        uiView.layer.shouldRasterize = enabled
        uiView.layer.rasterizationScale = if (enabled) UikitEnvironment.mainScreenScale().toDouble() else 1.0
    }

    override fun captureOffscreenBuffer(width: Int, height: Int): ImageBuffer? {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bytes = ByteArray(w * h * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
        return try {
            bytes.usePinned { pinned ->
                val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
                val ctx = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = w.toULong(),
                    height = h.toULong(),
                    bitsPerComponent = 8uL,
                    bytesPerRow = (w * 4).toULong(),
                    space = colorSpace,
                    bitmapInfo = bitmapInfo
                ) ?: return null
                try {
                    uiView.setFrame(CGRectMake(0.0, 0.0, w.toDouble(), h.toDouble()))
                    uiView.setNeedsLayout()
                    uiView.layoutIfNeeded()
                    uiView.layer.renderInContext(ctx)
                } finally {
                    CGContextRelease(ctx)
                }
            }
            ImageBuffer(
                width = w,
                height = h,
                pixelFormat = PixelFormat.RGBA_8888,
                colorSpace = ImageColorSpace.SRGB,
                bytes = bytes
            )
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    override fun presentAnimatedBuffer(buffer: ImageBuffer, frameState: ContainerAnimationFrameState) {
        val image = UikitGeometryAndColorUtil.imageBufferToUiImage(buffer) ?: return
        val applyOverlay = {
            if (!isDisposed) {
                val overlay = animationOverlayView ?: UIImageView(frame = uiView.bounds).apply {
                    contentMode = UIViewContentMode.UIViewContentModeScaleToFill
                    userInteractionEnabled = false
                    uiView.addSubview(this)
                    animationOverlayView = this
                }
                overlay.setFrame(uiView.bounds)
                overlay.image = image
                overlay.alpha = frameState.opacity.coerceIn(0f, 1f).toDouble()
                overlay.hidden = false
            }
        }
        if (NSThread.isMainThread) {
            applyOverlay()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                applyOverlay()
            }
        }
    }

    override fun releaseAnimatedBuffer() {
        val clearOverlay = {
            animationOverlayView?.removeFromSuperview()
            animationOverlayView = null
        }
        if (NSThread.isMainThread) {
            clearOverlay()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                clearOverlay()
            }
        }
    }
}
