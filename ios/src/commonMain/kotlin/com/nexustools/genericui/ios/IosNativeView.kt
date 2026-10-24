package com.nexustools.genericui.ios

import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Represents a native iOS UIKit view (`UIView` / `UIControl` / `UIScrollView` / `UICollectionView` / `MTKView`)
 * handle and its CoreAnimation layer (`CALayer`) properties.
 */
class IosNativeView(
    val uiViewClassName: String = "UIView"
) {
    var accessibilityIdentifier: String = ""
    var text: String = ""
    var placeholder: String = ""
    var isHidden: Boolean = false
    var isUserInteractionEnabled: Boolean = true
    var alpha: Float = 1.0f
    var cornerRadius: Float = 0f
    var borderWidth: Float = 0f
    var backgroundColor: RgbaColor? = null
    var tintColor: RgbaColor? = null
    var frame: Rect = Rect(0, 0, 390, 844)
    var layoutMargins: EdgeInsets = EdgeInsets.ZERO
    var contentOffsetX: Float = 0f
    var contentOffsetY: Float = 0f
    val metadata: MutableMap<String, String> = linkedMapOf()
    val attributes: MutableMap<String, String>
        get() = metadata

    var superview: IosNativeView? = null
        private set

    private val _subviews = mutableListOf<IosNativeView>()
    val subviews: List<IosNativeView>
        get() = _subviews

    fun addSubview(view: IosNativeView) {
        view.removeFromSuperview()
        view.superview = this
        _subviews.add(view)
    }

    fun insertSubview(view: IosNativeView, index: Int) {
        view.removeFromSuperview()
        view.superview = this
        if (index < 0 || index >= _subviews.size) {
            _subviews.add(view)
        } else {
            _subviews.add(index, view)
        }
    }

    fun removeFromSuperview() {
        superview?._subviews?.remove(this)
        superview = null
    }

    fun clearSubviews() {
        for (sub in _subviews) {
            sub.superview = null
        }
        _subviews.clear()
    }
}
