package com.nexustools.genericui.ios

import platform.UIKit.UIView
import platform.UIKit.UIViewController

/**
 * Native handle exposed by all UIKit backend implementations, wrapping a genuine
 * `platform.UIKit.UIView` (and optional backing `platform.UIKit.UIViewController`).
 */
open class UikitViewHandle(
    val uiView: UIView,
    val uiViewController: UIViewController? = null
) {
    /**
     * Convenience reference to the underlying `CALayer` of [uiView].
     */
    val caLayer get() = uiView.layer
}
