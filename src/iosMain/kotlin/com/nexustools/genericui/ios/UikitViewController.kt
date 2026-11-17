@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGSize
import platform.UIKit.UIMenuBuilderProtocol
import platform.UIKit.UIPressesEvent
import platform.UIKit.UITraitCollection
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol

/**
 * Concrete UIKit `UIViewController` class used for GenericUI modal sheets, popovers, and
 * utility dock controllers.
 */
class UikitViewController(
    private val customRootView: UIView? = null
) : UIViewController(nibName = null, bundle = null) {

    var onLayoutSubviews: (() -> Unit)? = null
    var onTransitionToSize: ((CValue<CGSize>) -> Unit)? = null
    var onTraitCollectionChanged: (() -> Unit)? = null
    var onMenuBuild: ((UIMenuBuilderProtocol) -> Unit)? = null

    override fun loadView() {
        val root = customRootView ?: UikitFlexContainerView(UikitEnvironment.mainScreenBounds())
        view = root
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        onLayoutSubviews?.invoke()
    }

    override fun viewWillTransitionToSize(
        size: CValue<CGSize>,
        withTransitionCoordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator)
        onTransitionToSize?.invoke(size)
    }

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        onTraitCollectionChanged?.invoke()
    }

    override fun buildMenuWithBuilder(builder: UIMenuBuilderProtocol) {
        super.buildMenuWithBuilder(builder)
        onMenuBuild?.invoke(builder)
    }

    override fun pressesBegan(presses: Set<*>, withEvent: UIPressesEvent?) {
        super.pressesBegan(presses, withEvent)
    }

    override fun pressesEnded(presses: Set<*>, withEvent: UIPressesEvent?) {
        super.pressesEnded(presses, withEvent)
    }
}
