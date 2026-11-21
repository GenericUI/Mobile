package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl

/**
 * Android `com.google.android.material.bottomnavigation.BottomNavigationView` implementation of [BottomNavigationBarNativeImpl].
 *
 * The Activity chrome (`GenericUiFragmentActivity.bottomNavigationView`) renders the actual Material
 * bottom navigation bar, so the in-layout placeholder view remains `View.GONE` (`0x0`).
 */
class AndroidBottomNavigationBarImpl : AndroidBaseNativeImpl("com.google.android.material.bottomnavigation.BottomNavigationView", "navbar fixed-bottom"), BottomNavigationBarNativeImpl<AndroidViewHandle> {
    private var selectedId: String? = null
    private var onItemSelectedListener: ((String) -> Unit)? = null

    internal var itemsList: List<Triple<String, String, String?>> = emptyList()
        private set

    internal var onBarChanged: (() -> Unit)? = null

    override fun createDefaultAndroidView(context: Context): View =
        View(context).apply { visibility = View.GONE }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val view = nativeWidget.androidView ?: return@runCatching
                if (view.visibility != View.GONE) {
                    view.visibility = View.GONE
                }
            }
        }
    }

    override fun setItems(items: List<Triple<String, String, String?>>) {
        itemsList = items.toList()
        nativeWidget.attributes["itemCount"] = items.size.toString()
        if (selectedId == null && items.isNotEmpty()) {
            selectedId = items.first().first
        }
        onBarChanged?.invoke()
    }

    override fun setSelectedItemId(itemId: String) {
        selectedId = itemId
        onBarChanged?.invoke()
        onItemSelectedListener?.invoke(itemId)
    }

    override fun getSelectedItemId(): String? = selectedId

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }

    internal fun dispatchItemSelectedFromView(itemId: String) {
        if (selectedId != itemId) {
            selectedId = itemId
            onItemSelectedListener?.invoke(itemId)
        }
    }
}
