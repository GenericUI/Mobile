package com.nexustools.genericui.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.graphics.SurfaceTexture
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import com.nexustools.genericui.jvm.style.JvmBackdropCapturePlan
import com.nexustools.genericui.jvm.style.JvmBackdropCapturePlanner
import com.nexustools.genericui.style.css.CssFilter
import java.lang.ref.WeakReference
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared real-time backdrop blur & filter controller for [AndroidFlexContainerView] and
 * [AndroidStyledButtonView].
 *
 * - Walks up to the nearest backdrop root (`android.R.id.content` or outermost host container)
 *   and records strictly pre-order preceding branches (including nested `android.view.TextureView`
 *   video layers) into the clipped blur tile.
 * - API 31+ hardware-accelerated path: GPU `RenderNode` + `RenderEffect.createBlurEffect` +
 *   `ColorMatrixColorFilter` composited at 60–120Hz and invalidated on `TextureView` frame updates.
 * - API 24–30 / software / Robolectric path: preallocated downsampled `Bitmap` +
 *   `TextureView.getBitmap(downsampledBitmap)` (safely guarded) + zero-alloc `FastBackdropBlurKernel`.
 */
class AndroidHardwareBackdropBlurController(
    private val hostView: View
) {
    var backdropFilter: CssFilter? = null
        set(value) {
            if (field != value) {
                field = value
                cachedRenderEffectFilter = null
                hostView.invalidate()
            }
        }

    var backdropBlurPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                cachedRenderEffectFilter = null
                hostView.invalidate()
            }
        }

    var backdropCornerRadiusPx: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                hostView.invalidate()
            }
        }

    var backdropOverlayDrawable: Drawable? = null
        set(value) {
            if (field !== value) {
                field = value
                hostView.invalidate()
            }
        }

    private var cachedBackdropBitmap: Bitmap? = null
    private var cachedBackdropPixels: IntArray? = null
    private var cachedTextureBitmap: Bitmap? = null
    private var renderNodeApi29: Any? = null
    private var cachedRenderEffectFilter: CssFilter? = null
    private var cachedRenderEffect: Any? = null
    private val observedTextureViews = LinkedHashSet<TextureView>(2)
    private val reusablePath = ArrayList<View>(16)
    private val reusableOrderIndices = ArrayList<Int>(16)

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val clipPath = Path()
    private val clipRectF = RectF()
    private val textureDestRect = Rect()
    private val srcRect = Rect()
    private val destRectF = RectF()

    fun hasActiveBackdrop(): Boolean =
        CssFilter.backdropBlurOrFilter(backdropBlurPx.takeIf { it > 0f }, backdropFilter) != null

    fun drawBackdropIfActive(canvas: Canvas) {
        val w = hostView.width
        val h = hostView.height
        if (w <= 0 || h <= 0) return
        val effFilter = CssFilter.backdropBlurOrFilter(backdropBlurPx.takeIf { it > 0f }, backdropFilter)
            ?: return
        val filterOpacity = effFilter.opacity
        if (filterOpacity != null && filterOpacity <= 0.004f) return
        val activeSet = activeCaptureThreadLocal.get() ?: return
        if (!activeSet.add(hostView)) return
        try {
            val rootGroup = resolveBackdropRootView() ?: return
            val path = buildAncestorPath(rootGroup) ?: return
            val relOffset = computeHostOffsetInRoot(path)
            val plan = JvmBackdropCapturePlanner.planCapture(effFilter, w, h, minHardwareBlurPx = 0.5f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                canvas.isHardwareAccelerated &&
                !plan.hasDropShadow
            ) {
                drawHardwareApi31(canvas, effFilter, plan, rootGroup, path, relOffset.first, relOffset.second, w, h)
            } else {
                drawDownsampledFallback(canvas, effFilter, plan, rootGroup, path, relOffset.first, relOffset.second, w, h)
            }
        } finally {
            activeSet.remove(hostView)
        }
    }

    fun drawOverlayDrawable(canvas: Canvas) {
        val overlay = backdropOverlayDrawable ?: return
        val w = hostView.width
        val h = hostView.height
        if (w <= 0 || h <= 0) return
        overlay.setBounds(0, 0, w, h)
        overlay.draw(canvas)
    }

    fun release() {
        cachedBackdropBitmap?.recycle()
        cachedBackdropBitmap = null
        cachedBackdropPixels = null
        cachedTextureBitmap?.recycle()
        cachedTextureBitmap = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (renderNodeApi29 as? RenderNode)?.discardDisplayList()
        }
        renderNodeApi29 = null
        cachedRenderEffect = null
        cachedRenderEffectFilter = null
        detachAllTextureObservers()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawHardwareApi31(
        canvas: Canvas,
        effFilter: CssFilter,
        plan: JvmBackdropCapturePlan,
        rootGroup: ViewGroup,
        path: List<View>,
        hostX: Float,
        hostY: Float,
        width: Int,
        height: Int
    ) {
        val blurPx = plan.blurPx
        val pad = plan.blurPaddingPx
        val rootW = max(rootGroup.width, ceil(hostX + width).toInt())
        val rootH = max(rootGroup.height, ceil(hostY + height).toInt())
        val effPadL = pad.coerceIn(0, floor(hostX).toInt().coerceAtLeast(0))
        val effPadT = pad.coerceIn(0, floor(hostY).toInt().coerceAtLeast(0))
        val effPadR = pad.coerceIn(0, max(0, rootW - ceil(hostX + width).toInt()))
        val effPadB = pad.coerceIn(0, max(0, rootH - ceil(hostY + height).toInt()))
        val recW = width + effPadL + effPadR
        val recH = height + effPadT + effPadB

        val node = (renderNodeApi29 as? RenderNode) ?: RenderNode("GenericUiBackdropBlur").also {
            renderNodeApi29 = it
        }
        node.setPosition(0, 0, recW, recH)
        node.setClipToBounds(true)
        node.setRenderEffect(obtainOrBuildRenderEffect(effFilter, blurPx))

        val recCanvas = node.beginRecording(recW, recH)
        try {
            recCanvas.save()
            recCanvas.translate(effPadL.toFloat() - hostX, effPadT.toFloat() - hostY)
            drawSubtreeUpToPath(rootGroup, path, 0, recCanvas)
            recCanvas.restore()
        } finally {
            node.endRecording()
        }

        val saveCount = canvas.save()
        clipToHostRoundedRect(canvas, width, height)
        canvas.translate(-effPadL.toFloat(), -effPadT.toFloat())
        canvas.drawRenderNode(node)
        canvas.restoreToCount(saveCount)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun obtainOrBuildRenderEffect(effFilter: CssFilter, blurPx: Float): RenderEffect? {
        if (cachedRenderEffectFilter == effFilter && cachedRenderEffect != null) {
            return cachedRenderEffect as? RenderEffect
        }
        var effect: RenderEffect? = if (blurPx > 0f) {
            RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
        } else {
            null
        }
        if (effFilter.hasPixelOps) {
            val matrix = JvmBackdropCapturePlanner.buildAndroidColorMatrix20(effFilter)?.let { ColorMatrix(it) }
            if (matrix != null) {
                val colorFilter = ColorMatrixColorFilter(matrix)
                effect = if (effect != null) {
                    RenderEffect.createColorFilterEffect(colorFilter, effect)
                } else {
                    RenderEffect.createColorFilterEffect(colorFilter)
                }
            }
        }
        cachedRenderEffectFilter = effFilter
        cachedRenderEffect = effect
        return effect
    }

    private fun computeDirectionalHaloPadding(filter: CssFilter): IntArray {
        val blurPx = if (filter.hasBlur) (filter.blur?.resolvePx() ?: 0f).coerceIn(0f, 48f) else 0f
        val blurPad = if (blurPx > 0f) ceil(blurPx * 1.5f).toInt().coerceIn(1, 72) else 0
        val shadow = filter.dropShadow?.takeIf { filter.hasDropShadow }
        val shadowBlurPx = if (shadow != null) (shadow.blurRadius.resolvePx() ?: 0f).coerceIn(0f, 48f) else 0f
        val shadowBlurPad = if (shadow != null && shadowBlurPx > 0f) ceil(shadowBlurPx * 1.5f).toInt().coerceIn(1, 72) else 0
        val shadowOffX = shadow?.offsetX?.resolvePx() ?: 0f
        val shadowOffY = shadow?.offsetY?.resolvePx() ?: 0f
        val shadowSpread = abs(shadow?.spreadRadius?.resolvePx() ?: 0f)
        val padLeft = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, shadowOffX) + shadowSpread).toInt().coerceAtMost(64)) else blurPad
        val padRight = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, -shadowOffX) + shadowSpread).toInt().coerceAtMost(64)) else blurPad
        val padTop = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, shadowOffY) + shadowSpread).toInt().coerceAtMost(64)) else blurPad
        val padBottom = if (shadow != null) max(blurPad, shadowBlurPad + ceil(max(0f, -shadowOffY) + shadowSpread).toInt().coerceAtMost(64)) else blurPad
        return intArrayOf(padLeft, padTop, padRight, padBottom)
    }

    private fun drawDownsampledFallback(
        canvas: Canvas,
        effFilter: CssFilter,
        plan: JvmBackdropCapturePlan,
        rootGroup: ViewGroup,
        path: List<View>,
        hostX: Float,
        hostY: Float,
        width: Int,
        height: Int
    ) {
        val dsScale = plan.downsampleScale
        val regW = plan.captureWidth
        val regH = plan.captureHeight
        val pads = computeDirectionalHaloPadding(effFilter)
        val rootW = max(rootGroup.width, ceil(hostX + width).toInt())
        val rootH = max(rootGroup.height, ceil(hostY + height).toInt())
        val effPadL = pads[0].coerceIn(0, floor(hostX).toInt().coerceAtLeast(0))
        val effPadT = pads[1].coerceIn(0, floor(hostY).toInt().coerceAtLeast(0))
        val effPadR = pads[2].coerceIn(0, max(0, rootW - ceil(hostX + width).toInt()))
        val effPadB = pads[3].coerceIn(0, max(0, rootH - ceil(hostY + height).toInt()))

        val dsPadL = if (dsScale > 1) ceil(effPadL / dsScale.toFloat()).toInt() else effPadL
        val dsPadT = if (dsScale > 1) ceil(effPadT / dsScale.toFloat()).toInt() else effPadT
        val dsPadR = if (dsScale > 1) ceil(effPadR / dsScale.toFloat()).toInt() else effPadR
        val dsPadB = if (dsScale > 1) ceil(effPadB / dsScale.toFloat()).toInt() else effPadB
        val bmpW = regW + dsPadL + dsPadR
        val bmpH = regH + dsPadT + dsPadB

        var bmp = cachedBackdropBitmap
        if (bmp == null || bmp.width != bmpW || bmp.height != bmpH || bmp.isRecycled) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            cachedBackdropBitmap = bmp
        }
        val needed = bmpW * bmpH
        var pixels = cachedBackdropPixels
        if (pixels == null || pixels.size < needed) {
            pixels = IntArray(needed)
            cachedBackdropPixels = pixels
        }

        bmp.eraseColor(0)
        val capCanvas = Canvas(bmp)
        capCanvas.save()
        capCanvas.translate(dsPadL.toFloat(), dsPadT.toFloat())
        if (dsScale > 1) {
            capCanvas.scale(regW.toFloat() / width.toFloat(), regH.toFloat() / height.toFloat())
        }
        capCanvas.translate(-hostX, -hostY)
        try {
            drawSubtreeUpToPath(rootGroup, path, 0, capCanvas)
        } finally {
            capCanvas.restore()
        }

        bmp.getPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)
        plan.scaledFilter.applyBackdropToArgbRegion(
            argbPixels = pixels,
            bufferWidth = bmpW,
            bufferHeight = bmpH,
            regionX = dsPadL,
            regionY = dsPadT,
            regionWidth = regW,
            regionHeight = regH,
            cornerRadiusPx = 0f
        )
        bmp.setPixels(pixels, 0, bmpW, 0, 0, bmpW, bmpH)

        val saveCount = canvas.save()
        clipToHostRoundedRect(canvas, width, height)
        srcRect.set(dsPadL, dsPadT, dsPadL + regW, dsPadT + regH)
        destRectF.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(bmp, srcRect, destRectF, bitmapPaint)
        canvas.restoreToCount(saveCount)
    }

    private fun clipToHostRoundedRect(canvas: Canvas, width: Int, height: Int) {
        val maxR = min(width, height) * 0.5f
        val r = backdropCornerRadiusPx.coerceIn(0f, maxR)
        if (r > 0.5f) {
            clipPath.reset()
            clipRectF.set(0f, 0f, width.toFloat(), height.toFloat())
            clipPath.addRoundRect(clipRectF, r, r, Path.Direction.CW)
            canvas.clipPath(clipPath)
        } else {
            canvas.clipRect(0, 0, width, height)
        }
    }

    private fun resolveBackdropRootView(): ViewGroup? {
        val firstParent = hostView.parent as? ViewGroup ?: return null
        var cur: ViewGroup? = firstParent
        var topGroup: ViewGroup = firstParent
        while (cur != null) {
            if (cur.id == android.R.id.content) {
                return cur
            }
            val nextParent = cur.parent as? ViewGroup ?: break
            val nextClass = nextParent.javaClass.name
            if (nextClass.contains("DecorView")) {
                topGroup = cur
                break
            }
            topGroup = nextParent
            cur = nextParent
        }
        return topGroup
    }

    private fun buildAncestorPath(rootGroup: ViewGroup): List<View>? {
        reusablePath.clear()
        var cur: View? = hostView
        while (cur != null) {
            reusablePath.add(cur)
            if (cur === rootGroup) {
                reusablePath.reverse()
                return reusablePath
            }
            cur = cur.parent as? View
        }
        return null
    }

    private fun computeHostOffsetInRoot(path: List<View>): Pair<Float, Float> {
        var ox = 0f
        var oy = 0f
        for (i in 1 until path.size) {
            val child = path[i]
            val parent = path[i - 1]
            ox += child.left + child.translationX - parent.scrollX.toFloat()
            oy += child.top + child.translationY - parent.scrollY.toFloat()
        }
        return ox to oy
    }

    private fun drawSubtreeUpToPath(
        currentGroup: ViewGroup,
        path: List<View>,
        depth: Int,
        capCanvas: Canvas
    ): Boolean {
        currentGroup.background?.draw(capCanvas)
        val count = currentGroup.childCount
        if (count <= 0 || depth + 1 >= path.size) return false
        val targetChild = path[depth + 1]
        fillPaintOrderIndices(currentGroup, count)

        val scScroll = capCanvas.save()
        if (currentGroup.scrollX != 0 || currentGroup.scrollY != 0) {
            capCanvas.translate(-currentGroup.scrollX.toFloat(), -currentGroup.scrollY.toFloat())
        }
        try {
            for (k in 0 until reusableOrderIndices.size) {
                val i = reusableOrderIndices[k]
                val child = currentGroup.getChildAt(i) ?: continue
                if (child === targetChild) {
                    if (child === hostView) {
                        return true
                    }
                    val childGroup = child as? ViewGroup ?: return true
                    val sc = capCanvas.save()
                    capCanvas.translate(child.left + child.translationX, child.top + child.translationY)
                    try {
                        drawSubtreeUpToPath(childGroup, path, depth + 1, capCanvas)
                    } finally {
                        capCanvas.restoreToCount(sc)
                    }
                    return true
                }
                if (child.visibility != View.VISIBLE || child.width <= 0 || child.height <= 0) continue
                val sc = capCanvas.save()
                capCanvas.translate(child.left + child.translationX, child.top + child.translationY)
                try {
                    drawBranchView(child, capCanvas)
                } finally {
                    capCanvas.restoreToCount(sc)
                }
            }
        } finally {
            capCanvas.restoreToCount(scScroll)
        }
        return false
    }

    private fun fillPaintOrderIndices(group: ViewGroup, count: Int) {
        reusableOrderIndices.clear()
        for (i in 0 until count) {
            reusableOrderIndices.add(i)
        }
        if (group is AndroidFlexContainerView && (group.isLayered || group.isRatioContainer) && count > 1) {
            reusableOrderIndices.sortWith { a, b ->
                val za = (group.getChildAt(a)?.layoutParams as? AndroidFlexContainerView.LayoutParams)?.layerZIndex ?: 0
                val zb = (group.getChildAt(b)?.layoutParams as? AndroidFlexContainerView.LayoutParams)?.layerZIndex ?: 0
                if (za != zb) za.compareTo(zb) else a.compareTo(b)
            }
        }
    }

    private fun drawBranchView(view: View, capCanvas: Canvas) {
        if (view is TextureView) {
            view.background?.draw(capCanvas)
            ensureTextureViewObserver(view)
            val drawnFromSurface = if (view.isAvailable && view.width > 0 && view.height > 0) {
                captureTextureViewIntoCanvas(view, capCanvas)
            } else {
                false
            }
            if (!drawnFromSurface) {
                view.draw(capCanvas)
            }
            return
        }

        if (view is ViewGroup && containsTextureViewDescendant(view)) {
            view.background?.draw(capCanvas)
            val scScroll = capCanvas.save()
            if (view.scrollX != 0 || view.scrollY != 0) {
                capCanvas.translate(-view.scrollX.toFloat(), -view.scrollY.toFloat())
            }
            try {
                val count = view.childCount
                if (count > 0) {
                    val indices = if (view is AndroidFlexContainerView && (view.isLayered || view.isRatioContainer) && count > 1) {
                        (0 until count).sortedWith(
                            compareBy<Int>(
                                { (view.getChildAt(it)?.layoutParams as? AndroidFlexContainerView.LayoutParams)?.layerZIndex ?: 0 },
                                { it }
                            )
                        )
                    } else {
                        (0 until count).toList()
                    }
                    for (idx in indices) {
                        val child = view.getChildAt(idx) ?: continue
                        if (child.visibility != View.VISIBLE || child.width <= 0 || child.height <= 0) continue
                        val sc = capCanvas.save()
                        capCanvas.translate(child.left + child.translationX, child.top + child.translationY)
                        try {
                            drawBranchView(child, capCanvas)
                        } finally {
                            capCanvas.restoreToCount(sc)
                        }
                    }
                }
            } finally {
                capCanvas.restoreToCount(scScroll)
            }
            return
        }

        view.draw(capCanvas)
    }

    private fun containsTextureViewDescendant(group: ViewGroup): Boolean {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i) ?: continue
            if (child is TextureView) return true
            if (child is ViewGroup && containsTextureViewDescendant(child)) return true
        }
        return false
    }

    private fun captureTextureViewIntoCanvas(tv: TextureView, capCanvas: Canvas): Boolean {
        val tw = tv.width
        val th = tv.height
        if (tw <= 0 || th <= 0) return false
        val maxSide = 480
        val scale = if (max(tw, th) > maxSide) maxSide.toFloat() / max(tw, th).toFloat() else 1f
        val capW = max(1, (tw * scale).roundToInt())
        val capH = max(1, (th * scale).roundToInt())
        var bmp = cachedTextureBitmap
        if (bmp == null || bmp.width != capW || bmp.height != capH || bmp.isRecycled) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(capW, capH, Bitmap.Config.ARGB_8888)
            cachedTextureBitmap = bmp
        }
        val captured = try {
            tv.getBitmap(bmp)
        } catch (_: Throwable) {
            null
        } ?: return false
        textureDestRect.set(0, 0, tw, th)
        capCanvas.drawBitmap(captured, null, textureDestRect, bitmapPaint)
        return true
    }

    private fun ensureTextureViewObserver(tv: TextureView) {
        observedTextureViews.add(tv)
        val existing = tv.surfaceTextureListener
        if (existing is SurfaceUpdateRelayListener) {
            existing.register(this)
        } else {
            val relay = SurfaceUpdateRelayListener(existing).also { it.register(this) }
            tv.surfaceTextureListener = relay
        }
    }

    private fun detachAllTextureObservers() {
        for (tv in observedTextureViews) {
            val listener = tv.surfaceTextureListener as? SurfaceUpdateRelayListener
            listener?.unregister(this)
        }
        observedTextureViews.clear()
    }

    internal fun onObservedTextureFrameUpdated() {
        if (hostView.isAttachedToWindow && hasActiveBackdrop()) {
            hostView.postInvalidateOnAnimation()
        }
    }

    private class SurfaceUpdateRelayListener(
        private val delegate: TextureView.SurfaceTextureListener?
    ) : TextureView.SurfaceTextureListener {
        private val observers = LinkedHashSet<WeakReference<AndroidHardwareBackdropBlurController>>()

        fun register(controller: AndroidHardwareBackdropBlurController) {
            prune()
            if (observers.none { it.get() === controller }) {
                observers.add(WeakReference(controller))
            }
        }

        fun unregister(controller: AndroidHardwareBackdropBlurController) {
            observers.removeAll { it.get() == null || it.get() === controller }
        }

        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            delegate?.onSurfaceTextureAvailable(surface, width, height)
            notifyAllObservers()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            delegate?.onSurfaceTextureSizeChanged(surface, width, height)
            notifyAllObservers()
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
            val handled = delegate?.onSurfaceTextureDestroyed(surface) ?: true
            return handled
        }

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
            delegate?.onSurfaceTextureUpdated(surface)
            notifyAllObservers()
        }

        private fun notifyAllObservers() {
            prune()
            for (ref in observers) {
                ref.get()?.onObservedTextureFrameUpdated()
            }
        }

        private fun prune() {
            observers.removeAll { it.get() == null }
        }
    }

    companion object {
        private val activeCaptureThreadLocal = object : ThreadLocal<MutableSet<View>>() {
            override fun initialValue(): MutableSet<View> = LinkedHashSet(4)
        }
    }
}
