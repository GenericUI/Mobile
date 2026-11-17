@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UITabBar
import platform.UIKit.UITabBarItem

/**
 * Native UIKit `UITabBar` + `UITabBarItem` implementation of [BottomNavigationBarNativeImpl].
 */
class IosBottomNavigationBarImpl(
    val tabBar: UITabBar = UITabBar(frame = CGRectMake(0.0, 0.0, 390.0, 56.0))
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(tabBar),
    componentClassPrefix = "bottom-nav"
), BottomNavigationBarNativeImpl<UikitViewHandle> {

    private var itemsData: List<Triple<String, String, String?>> = emptyList()
    private var tabBarItems: List<UITabBarItem> = emptyList()
    private var selectedId: String? = null
    private var onItemSelectedListener: ((String) -> Unit)? = null

    private val tabDelegate = UikitTabBarDelegate { index ->
        val id = itemsData.getOrNull(index)?.first ?: return@UikitTabBarDelegate
        selectedId = id
        onItemSelectedListener?.invoke(id)
    }

    init {
        tabBar.delegate = tabDelegate
    }

    override fun setItems(items: List<Triple<String, String, String?>>) {
        itemsData = items
        val built = items.mapIndexed { index, (_, label, iconName) ->
            val icon = UikitGeometryAndColorUtil.systemIconImage(iconName)
            UITabBarItem(title = label, image = icon, tag = index.toLong())
        }
        tabBarItems = built
        tabBar.setItems(built, animated = false)
        if (selectedId == null && items.isNotEmpty()) {
            setSelectedItemId(items.first().first)
        } else {
            selectedId?.let { setSelectedItemId(it) }
        }
    }

    override fun setSelectedItemId(itemId: String) {
        selectedId = itemId
        val idx = itemsData.indexOfFirst { it.first == itemId }
        if (idx in tabBarItems.indices) {
            tabBar.selectedItem = tabBarItems[idx]
        }
    }

    override fun getSelectedItemId(): String? = selectedId

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }
}
