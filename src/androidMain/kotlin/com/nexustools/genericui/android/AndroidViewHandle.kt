package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlin.math.roundToInt

/**
 * Wraps an Android [View] / [ViewGroup] instance while remaining safe to construct in headless JVM unit tests
 * when no Android [Context] is attached yet.
 */
class AndroidViewHandle(
    val viewClassName: String = "android.view.View",
    @field:Volatile var androidView: View? = null
) {
    var tagId: String = ""
    var text: String = ""
    var hint: String = ""
    var tooltip: String? = null
    var visible: Boolean = true
    var enabled: Boolean = true
    var alpha: Float = 1.0f
    var bounds: Rect = Rect(0, 0, 412, 915)
    var padding: EdgeInsets = EdgeInsets.ZERO
    var margin: EdgeInsets = EdgeInsets.ZERO
    var scrollX: Int = 0
    var scrollY: Int = 0
    val classes: MutableSet<String> = linkedSetOf()
    val attributes: MutableMap<String, String> = linkedMapOf()

    var flexDirection: FlexDirection = FlexDirection.COLUMN
    var flexWrap: FlexWrap = FlexWrap.NOWRAP
    var alignItems: AlignItems = AlignItems.STRETCH
    var justifyContent: JustifyContent = JustifyContent.START
    var gapPx: Int = 0
    var layoutWeight: Float = 0f
    var fillMaxWidth: Boolean = false
    var fillMaxHeight: Boolean = false
    var maxLines: Int = Int.MAX_VALUE
    var hasExplicitPreferredWidth: Boolean = false
    var hasExplicitPreferredHeight: Boolean = false
    var aspectRatio: Float = 0f
    var isLayeredContainer: Boolean = false
    var isRatioContainer: Boolean = false
    var isLayerItem: Boolean = false
    var layerFill: Boolean = false
    var layerFloat: Boolean = false
    var layerAlignment: LayerAlignment = LayerAlignment.TOP_START
    var layerOffsetXPx: Int = 0
    var layerOffsetYPx: Int = 0
    var layerLeftPercent: Float? = null
    var layerTopPercent: Float? = null
    var layerRightPx: Int? = null
    var layerBottomPx: Int? = null
    var layerZIndex: Int = 0
    var layerPassThrough: Boolean = false
    var widthPercent: Float? = null
    var heightPercent: Float? = null
    var onViewMutated: (() -> Unit)? = null

    /**
     * Optional inner [ViewGroup] that receives child views when [androidView] is a wrapper
     * such as `ScrollView` (which can only host a single direct child view).
     */
    var childHostViewGroup: ViewGroup? = null

    @Volatile
    var disposed: Boolean = false

    @Volatile
    var parentHandle: AndroidViewHandle? = null
        private set

    private val childrenLock = Any()
    private val _children = mutableListOf<AndroidViewHandle>()
    val children: List<AndroidViewHandle>
        get() = synchronized(childrenLock) { _children.toList() }

    internal fun attachChildHandleOnMainThread(child: AndroidViewHandle) {
        attachChildViewOnMainThread(child)
    }

    private fun attachChildViewOnMainThread(child: AndroidViewHandle) {
        val isLiveChild = synchronized(childrenLock) {
            !disposed && !child.disposed && child.parentHandle === this && _children.contains(child)
        }
        if (!isLiveChild) return
        val vg = childHostViewGroup ?: (androidView as? ViewGroup) ?: return
        val cv = child.androidView ?: return
        if (vg === cv) return
        val lp = if (vg is AndroidFlexContainerView) {
            AndroidFlexContainerView.buildLayoutParamsFor(child)
        } else {
            val w = when {
                child.fillMaxWidth || child.attributes["layoutWidth"] == "MATCH_PARENT" ->
                    ViewGroup.LayoutParams.MATCH_PARENT
                child.hasExplicitPreferredWidth && child.bounds.width > 0 ->
                    dpToPx(child.bounds.width)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val h = when {
                child.fillMaxHeight || child.attributes["layoutHeight"] == "MATCH_PARENT" ->
                    ViewGroup.LayoutParams.MATCH_PARENT
                child.hasExplicitPreferredHeight && child.bounds.height > 0 ->
                    dpToPx(child.bounds.height)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            ViewGroup.MarginLayoutParams(w, h).apply {
                setMargins(
                    dpToPx(child.margin.left),
                    dpToPx(child.margin.top),
                    dpToPx(child.margin.right),
                    dpToPx(child.margin.bottom)
                )
            }
        }
        val isHostedInActivity = (cv.parent as? View)?.getTag(GenericUiFragmentActivity.TAG_ACTIVITY_CONTENT_HOST) == true
        val needsReattach = cv.parent === vg && vg.isAttachedToWindow && !cv.isAttachedToWindow
        if (needsReattach) {
            vg.removeView(cv)
        }
        if ((cv.parent !== vg || needsReattach) && !isHostedInActivity) {
            (cv.parent as? ViewGroup)?.removeView(cv)
            val logicalIdx = synchronized(childrenLock) {
                if (disposed || child.disposed || child.parentHandle !== this) -1 else _children.indexOf(child)
            }
            if (logicalIdx < 0) return
            val insertIdx = logicalIdx.coerceIn(0, vg.childCount)
            vg.addView(cv, insertIdx, lp)
        } else if (cv.parent === vg) {
            cv.layoutParams = lp
        }
        val grandChildren = synchronized(child.childrenLock) { child._children.toList() }
        for (grandChild in grandChildren) {
            child.attachChildViewOnMainThread(grandChild)
        }
        vg.requestLayout()
        vg.invalidate()
    }

    fun addChild(child: AndroidViewHandle, index: Int = -1) {
        if (disposed || child.disposed) return
        child.removeFromParent()
        synchronized(childrenLock) {
            if (disposed || child.disposed) return
            child.parentHandle = this
            _children.remove(child)
            if (index < 0 || index >= _children.size) {
                _children.add(child)
            } else {
                _children.add(index, child)
            }
        }
        runOnMainThread {
            runCatching {
                attachChildViewOnMainThread(child)
            }
        }
        onViewMutated?.invoke()
    }

    fun removeChild(child: AndroidViewHandle) {
        val removed = synchronized(childrenLock) {
            if (_children.remove(child)) {
                if (child.parentHandle === this) {
                    child.parentHandle = null
                }
                true
            } else {
                false
            }
        }
        if (removed) {
            runOnMainThread {
                runCatching {
                    if (child.parentHandle !== this) {
                        val vg = childHostViewGroup ?: (androidView as? ViewGroup)
                        val cv = child.androidView
                        if (vg != null && cv != null && cv.parent === vg) {
                            vg.removeView(cv)
                        }
                    }
                }
            }
            onViewMutated?.invoke()
        }
    }

    fun removeFromParent() {
        parentHandle?.removeChild(this)
    }

    fun dispose() {
        disposed = true
        removeFromParent()
    }

    fun clearChildren() {
        synchronized(childrenLock) {
            for (c in _children) {
                if (c.parentHandle === this) {
                    c.parentHandle = null
                }
            }
            _children.clear()
        }
        runOnMainThread {
            runCatching {
                val vg = childHostViewGroup ?: (androidView as? ViewGroup)
                if (vg != null) {
                    vg.removeAllViews()
                    val snapshot = synchronized(childrenLock) { _children.toList() }
                    for (c in snapshot) {
                        attachChildViewOnMainThread(c)
                    }
                }
            }
        }
        onViewMutated?.invoke()
    }

    companion object {
        @Volatile
        var activeContext: Context? = null

        @Volatile
        private var cachedMainHandler: android.os.Handler? = null

        private fun obtainMainHandler(looper: android.os.Looper): android.os.Handler? {
            val existing = cachedMainHandler
            if (existing != null && runCatching { existing.looper === looper }.getOrDefault(false)) {
                return existing
            }
            return runCatching {
                android.os.Handler(looper).also { cachedMainHandler = it }
            }.getOrNull()
        }

        fun isOnMainThread(): Boolean {
            val looper = runCatching { android.os.Looper.getMainLooper() }.getOrNull()
            val looperThread: Thread? = runCatching { looper?.thread }.getOrNull()
            return looper == null || looperThread == null || looperThread === Thread.currentThread()
        }

        fun runOnMainThread(block: () -> Unit) {
            val looper = runCatching { android.os.Looper.getMainLooper() }.getOrNull()
            val looperThread: Thread? = runCatching { looper?.thread }.getOrNull()
            if (looper == null || looperThread == null || looperThread === Thread.currentThread()) {
                block()
            } else {
                val h = obtainMainHandler(looper)
                if (h != null) {
                    h.post(block)
                } else if (activeContext == null) {
                    block()
                }
            }
        }

        fun dispatchOnLogicThread(block: () -> Unit) {
            if (com.nexustools.genericui.core.GenericUI.activeFactory != null &&
                com.nexustools.genericui.threading.GenericUiThreading.isSplitThreadingEnabled &&
                !com.nexustools.genericui.threading.GenericUiThreading.isOnLogicThread()
            ) {
                com.nexustools.genericui.threading.GenericUiThreading.runOnLogic(block)
            } else {
                block()
            }
        }

        fun density(): Float = runCatching {
            activeContext?.resources?.displayMetrics?.density?.takeIf { it > 0f }
        }.getOrNull() ?: 1f

        fun dpToPx(dp: Int): Int = (dp * density()).roundToInt()

        fun dpToPx(dp: Float): Int = (dp * density()).roundToInt()

        fun dpToPxFloat(dp: Float): Float = dp * density()

        fun spToPxFloat(sp: Float): Float = runCatching {
            val scaled = activeContext?.resources?.displayMetrics?.scaledDensity?.takeIf { it > 0f } ?: density()
            sp * scaled
        }.getOrDefault(sp)

        fun isDarkTheme(): Boolean {
            val appResolved = Application.current?.getResolvedTheme()
            if (appResolved == ThemeMode.DARK) return true
            if (appResolved == ThemeMode.LIGHT) return false
            return SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        }
    }
}
