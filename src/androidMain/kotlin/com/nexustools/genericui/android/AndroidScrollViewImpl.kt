package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * Android `android.widget.ScrollView` implementation of [ScrollViewNativeImpl]
 * hosting an inner [AndroidFlexContainerView] so multiple children can be added safely.
 */
class AndroidScrollViewImpl : AndroidBaseContainerNativeImpl("androidx.core.widget.NestedScrollView", "overflow-auto"), ScrollViewNativeImpl<AndroidViewHandle> {

    private var innerContainerView: AndroidFlexContainerView? = null

    init {
        nativeWidget.fillMaxWidth = true
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View? {
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
        }
        val inner = AndroidFlexContainerView(context).apply {
            flexDirection = FlexDirection.COLUMN
            alignItems = AlignItems.STRETCH
        }
        innerContainerView = inner
        nativeWidget.childHostViewGroup = inner
        scrollView.addView(
            inner,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scrollView
    }

    override fun scrollTo(x: Int, y: Int) {
        nativeWidget.scrollX = x
        nativeWidget.scrollY = y
        runCatching {
            val sv = nativeWidget.androidView as? ScrollView
            sv?.post {
                runCatching { sv.scrollTo(AndroidViewHandle.dpToPx(x), AndroidViewHandle.dpToPx(y)) }
            }
        }
    }

    override fun scrollToBottom() {
        nativeWidget.scrollY = nativeWidget.bounds.height
        runCatching {
            val sv = nativeWidget.androidView as? ScrollView
            sv?.post {
                runCatching { sv.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }
}
