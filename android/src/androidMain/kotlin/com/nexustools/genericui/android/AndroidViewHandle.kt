package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect

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
    val metadata: MutableMap<String, String>
        get() = attributes

    var visibility: Boolean
        get() = visible
        set(value) { visible = value }

    var contentDescription: String
        get() = tagId
        set(value) { tagId = value }

    fun addChildAt(child: AndroidViewHandle, index: Int) {
        addChild(child, index)
    }

    var parentHandle: AndroidViewHandle? = null
        private set

    private val _children = mutableListOf<AndroidViewHandle>()
    val children: List<AndroidViewHandle>
        get() = _children

    fun addChild(child: AndroidViewHandle, index: Int = -1) {
        child.removeFromParent()
        child.parentHandle = this
        if (index < 0 || index >= _children.size) {
            _children.add(child)
        } else {
            _children.add(index, child)
        }
        val vg = androidView as? ViewGroup
        val cv = child.androidView
        if (vg != null && cv != null) {
            (cv.parent as? ViewGroup)?.removeView(cv)
            if (index < 0 || index >= vg.childCount) {
                vg.addView(cv)
            } else {
                vg.addView(cv, index)
            }
        }
    }

    fun removeChild(child: AndroidViewHandle) {
        if (_children.remove(child)) {
            child.parentHandle = null
            val vg = androidView as? ViewGroup
            val cv = child.androidView
            if (vg != null && cv != null) {
                vg.removeView(cv)
            }
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
        (androidView as? ViewGroup)?.removeAllViews()
    }

    companion object {
        var activeContext: Context? = null
    }
}
