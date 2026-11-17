package com.nexustools.genericui.ios

import platform.UIKit.UITabBar
import platform.UIKit.UITabBarDelegateProtocol
import platform.UIKit.UITabBarItem
import platform.darwin.NSObject

/**
 * `UITabBarDelegate` forwarding bottom navigation item taps by `UITabBarItem.tag` index.
 */
class UikitTabBarDelegate(
    private val onItemIndexSelected: (Int) -> Unit
) : NSObject(), UITabBarDelegateProtocol {
    override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
        onItemIndexSelected(didSelectItem.tag.toInt())
    }
}
