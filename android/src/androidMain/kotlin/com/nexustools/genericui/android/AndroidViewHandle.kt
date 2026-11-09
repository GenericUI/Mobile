package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
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
    var androidView: View? = null
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
    var onViewMutated: (() -> Unit)? = null

    /**
     * Optional inner [ViewGroup] that receives child views when [androidView] is a wrapper
     * such as `ScrollView` (which can only host a single direct child view).
     */
    var childHostViewGroup: ViewGroup? = null

    var parentHandle: AndroidViewHandle? = null
        private set

    private val _children = mutableListOf<AndroidViewHandle>()
    val children: List<AndroidViewHandle>
        get() = _children

    private fun attachChildViewOnMainThread(child: AndroidViewHandle) {
        if (child.parentHandle !== this || !_children.contains(child)) return
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
        if (cv.parent !== vg) {
            (cv.parent as? ViewGroup)?.removeView(cv)
            val logicalIdx = _children.indexOf(child)
            val insertIdx = if (logicalIdx < 0) vg.childCount else logicalIdx.coerceIn(0, vg.childCount)
            vg.addView(cv, insertIdx, lp)
        } else {
            cv.layoutParams = lp
        }
        for (grandChild in child._children.toList()) {
            child.attachChildViewOnMainThread(grandChild)
        }
    }

    fun addChild(child: AndroidViewHandle, index: Int = -1) {
        child.removeFromParent()
        child.parentHandle = this
        if (index < 0 || index >= _children.size) {
            _children.add(child)
        } else {
            _children.add(index, child)
        }
        runOnMainThread {
            runCatching {
                attachChildViewOnMainThread(child)
            }
        }
        onViewMutated?.invoke()
    }

    fun removeChild(child: AndroidViewHandle) {
        if (_children.remove(child)) {
            child.parentHandle = null
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

    fun clearChildren() {
        for (c in _children) {
            c.parentHandle = null
        }
        _children.clear()
        runOnMainThread {
            runCatching {
                val vg = childHostViewGroup ?: (androidView as? ViewGroup)
                if (vg != null) {
                    vg.removeAllViews()
                    for (c in _children.toList()) {
                        attachChildViewOnMainThread(c)
                    }
                }
            }
        }
        onViewMutated?.invoke()
    }

    companion object {
        var activeContext: Context? = null

        @Volatile
        private var cachedMainHandler: android.os.Handler? = null

        private fun obtainMainHandler(looper: android.os.Looper): android.os.Handler? {
            cachedMainHandler?.let { return it }
            return runCatching {
                android.os.Handler(looper).also { cachedMainHandler = it }
            }.getOrNull()
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
