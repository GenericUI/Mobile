package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import com.google.android.material.tabs.TabLayout
import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabIconPlacement
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Android Material [TabLayout] + content host implementation of [TabContainerNativeImpl],
 * rendering genuine Material tabs while ignoring `BootstrapType` overrides.
 */
class AndroidTabContainerImpl : AndroidBaseContainerNativeImpl("com.google.android.material.tabs.TabLayout", "nav-tabs"), TabContainerNativeImpl<AndroidViewHandle> {
    override val supportsNativeFadeTransition: Boolean = true

    private val tabImpls = mutableListOf<TabNativeImpl<*>?>()
    private val tabTitles = mutableListOf<String>()
    private val tabContents = mutableListOf<NativeImpl<*>?>()
    private var selectedIndexVal: Int = 0
    private var fullWidthTabsVal: Boolean = false
    private var tabPositionVal: TabPosition = TabPosition.TOP
    private var tabBarStyleVal: TabBarStyle = TabBarStyle.TABS
    private var suppressTabSelectionCallback: Boolean = false
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    var tabLayout: TabLayout? = null
        private set
    private var rootLayout: LinearLayout? = null
    private var headerBottomBorder: View? = null
    private var contentHost: AndroidFlexContainerView? = null

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    private fun resolveMaterialContext(context: Context, isDark: Boolean): Context {
        val tv = TypedValue()
        val theme = context.theme
        val hasAppCompat = runCatching {
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        }.getOrDefault(false)
        val hasMaterial = runCatching {
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryVariant, tv, true)
        }.getOrDefault(false)
        if (hasAppCompat && hasMaterial) {
            return context
        }
        val styleRes = if (isDark) {
            com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar
        } else {
            com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
        }
        return ContextThemeWrapper(context, styleRes)
    }

    override fun createDefaultAndroidView(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        rootLayout = root
        val isDark = AndroidViewHandle.isDarkTheme()
        val materialContext = resolveMaterialContext(context, isDark)
        val materialTabLayout = TabLayout(materialContext).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            tabGravity = TabLayout.GRAVITY_START
            isInlineLabel = true
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    if (suppressTabSelectionCallback) return
                    val pos = tab.position
                    if (pos !in tabTitles.indices) return
                    if (tabContents.getOrNull(pos) == null && tabImpls.getOrNull(pos) != null) {
                        onTabSelectedListener?.invoke(pos)
                        val restoreTab = getTabAt(selectedIndexVal)
                        if (restoreTab != null && restoreTab !== tab) {
                            suppressTabSelectionCallback = true
                            try {
                                restoreTab.select()
                            } finally {
                                suppressTabSelectionCallback = false
                            }
                        }
                        return
                    }
                    setSelectedTabIndex(pos, false)
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit

                override fun onTabReselected(tab: TabLayout.Tab) {
                    if (suppressTabSelectionCallback) return
                    val pos = tab.position
                    if (pos !in tabTitles.indices) return
                    if (tabContents.getOrNull(pos) == null && tabImpls.getOrNull(pos) != null) {
                        onTabSelectedListener?.invoke(pos)
                    }
                }
            })
        }
        tabLayout = materialTabLayout
        val borderView = View(context).apply {
            setBackgroundColor(if (isDark) 0xFF2A2B36.toInt() else 0xFFD0D5DD.toInt())
        }
        headerBottomBorder = borderView
        val host = AndroidFlexContainerView(context)
        contentHost = host
        nativeWidget.childHostViewGroup = host

        assembleRootChildren(root)
        return root
    }

    private fun assembleRootChildren(root: LinearLayout) {
        val layout = tabLayout ?: return
        val border = headerBottomBorder ?: return
        val host = contentHost ?: return
        root.removeAllViews()
        if (tabPositionVal == TabPosition.BOTTOM) {
            root.addView(
                host,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            root.addView(
                border,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidViewHandle.dpToPx(1).coerceAtLeast(1)
                )
            )
            root.addView(
                layout,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            root.addView(
                layout,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            root.addView(
                border,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    AndroidViewHandle.dpToPx(1).coerceAtLeast(1)
                )
            )
            root.addView(
                host,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
        }
    }

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val androidTab = tabImpl as? AndroidTabImpl
        androidTab?.onTabUpdated = { syncTabsVisibilityAndHeader() }
        val title = androidTab?.titleVal ?: ""
        if (index < 0 || index >= tabTitles.size) {
            tabImpls.add(tabImpl)
            tabTitles.add(title)
            tabContents.add(contentImpl)
            addChildNative(tabImpl, -1)
            addChildNative(contentImpl, -1)
        } else {
            tabImpls.add(index, tabImpl)
            tabTitles.add(index, title)
            tabContents.add(index, contentImpl)
            addChildNative(tabImpl, index)
            addChildNative(contentImpl, index)
        }
        nativeWidget.attributes["tabTitles"] = tabTitles.joinToString("|")
        syncTabsVisibilityAndHeader()
    }

    override fun addButtonTabNative(tabImpl: TabNativeImpl<*>, index: Int) {
        val androidTab = tabImpl as? AndroidTabImpl
        androidTab?.onTabUpdated = { syncTabsVisibilityAndHeader() }
        val title = androidTab?.titleVal ?: ""
        if (index < 0 || index >= tabTitles.size) {
            tabImpls.add(tabImpl)
            tabTitles.add(title)
            tabContents.add(null)
            addChildNative(tabImpl, -1)
        } else {
            tabImpls.add(index, tabImpl)
            tabTitles.add(index, title)
            tabContents.add(index, null)
            addChildNative(tabImpl, index)
        }
        nativeWidget.attributes["tabTitles"] = tabTitles.joinToString("|")
        syncTabsVisibilityAndHeader()
    }

    override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {
        if (index < 0 || index >= tabTitles.size) {
            tabImpls.add(null)
            tabTitles.add(title)
            tabContents.add(contentImpl)
            addChildNative(contentImpl, -1)
        } else {
            tabImpls.add(index, null)
            tabTitles.add(index, title)
            tabContents.add(index, contentImpl)
            addChildNative(contentImpl, index)
        }
        nativeWidget.attributes["tabTitles"] = tabTitles.joinToString("|")
        syncTabsVisibilityAndHeader()
    }

    override fun updateTabNative(index: Int, tabImpl: TabNativeImpl<*>) {
        if (index in tabImpls.indices) {
            tabImpls[index] = tabImpl
            (tabImpl as? AndroidTabImpl)?.let {
                it.onTabUpdated = { syncTabsVisibilityAndHeader() }
                tabTitles[index] = it.titleVal
            }
            nativeWidget.attributes["tabTitles"] = tabTitles.joinToString("|")
            syncTabsVisibilityAndHeader()
        }
    }

    override fun removeTabNative(index: Int) {
        if (index !in tabTitles.indices) return
        val removedTab = tabImpls.removeAt(index)
        (removedTab as? AndroidTabImpl)?.onTabUpdated = null
        removedTab?.let { removeChildNative(it) }
        tabTitles.removeAt(index)
        val removedContent = tabContents.removeAt(index)
        removedContent?.let { removeChildNative(it) }
        selectedIndexVal = selectedIndexVal.coerceIn(0, (tabTitles.size - 1).coerceAtLeast(0))
        nativeWidget.attributes["tabTitles"] = tabTitles.joinToString("|")
        syncTabsVisibilityAndHeader()
    }

    override fun setTabTitleNative(index: Int, title: String) {
        if (index in tabTitles.indices) {
            tabTitles[index] = title
            tabImpls[index]?.setTitleNative(title)
            nativeWidget.attributes["tabTitles"] = tabTitles.joinToString("|")
            syncTabsVisibilityAndHeader()
        }
    }

    override fun setTabPositionNative(position: TabPosition) {
        tabPositionVal = position
        nativeWidget.attributes["tabPosition"] = position.name
        AndroidViewHandle.runOnMainThread {
            runCatching {
                rootLayout?.let { assembleRootChildren(it) }
            }
        }
    }

    override fun setTabBarStyleNative(style: TabBarStyle) {
        tabBarStyleVal = style
        nativeWidget.attributes["tabBarStyle"] = style.name
        syncTabsVisibilityAndHeader()
    }

    override fun setFullWidthTabsNative(fullWidth: Boolean) {
        fullWidthTabsVal = fullWidth
        nativeWidget.attributes["fullWidthTabs"] = fullWidth.toString()
        syncTabsVisibilityAndHeader()
    }

    override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) {
        if (tabTitles.isNotEmpty() && index !in tabTitles.indices) return
        if (tabContents.getOrNull(index) == null && tabImpls.getOrNull(index) != null) {
            onTabSelectedListener?.invoke(index)
            return
        }
        selectedIndexVal = index
        syncTabsVisibilityAndHeader()
        onTabSelectedListener?.invoke(index)
    }

    override fun getSelectedTabIndex(): Int = selectedIndexVal

    override fun setOnTabSelected(listener: ((Int) -> Unit)?) {
        onTabSelectedListener = listener
    }

    override fun setOnTabCloseRequested(listener: ((Int) -> Boolean)?) {
        onTabCloseRequestedListener = listener
    }

    private fun handleCloseTabIndex(index: Int, androidTab: AndroidTabImpl?) {
        androidTab?.dispatchCloseClicked()
        val shouldRemove = onTabCloseRequestedListener?.invoke(index) ?: true
        if (shouldRemove && index in tabTitles.indices) {
            removeTabNative(index)
        }
    }

    private fun syncTabsVisibilityAndHeader() {
        tabContents.forEachIndexed { idx, impl ->
            impl?.setVisible(idx == selectedIndexVal)
        }
        tabImpls.forEachIndexed { idx, impl ->
            (impl as? AndroidTabImpl)?.setSelectedNative(idx == selectedIndexVal)
        }
        AndroidViewHandle.runOnMainThread {
            runCatching {
                hideTabPlaceholderViews()
            }
            rebuildMaterialTabs()
        }
    }

    private fun rebuildMaterialTabs() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val layout = tabLayout ?: return@runCatching
                val ctx = layout.context ?: return@runCatching
                val isDark = AndroidViewHandle.isDarkTheme()
                headerBottomBorder?.setBackgroundColor(if (isDark) 0xFF2A2B36.toInt() else 0xFFD0D5DD.toInt())

                val anyTopIcon = tabImpls.any {
                    val t = it as? AndroidTabImpl
                    t != null && t.iconPlacementVal == TabIconPlacement.TOP && !t.iconVal.isNullOrBlank()
                }
                val useEqualWidth = fullWidthTabsVal || anyTopIcon
                layout.isInlineLabel = !anyTopIcon
                layout.tabMode = if (useEqualWidth) TabLayout.MODE_FIXED else TabLayout.MODE_SCROLLABLE
                layout.tabGravity = if (useEqualWidth) TabLayout.GRAVITY_FILL else TabLayout.GRAVITY_START

                suppressTabSelectionCallback = true
                try {
                    layout.removeAllTabs()
                    val labelFallbackColor = if (isDark) 0xFFE3E2E6.toInt() else 0xFF1C1B1F.toInt()
                    for (i in tabTitles.indices) {
                        val androidTab = tabImpls.getOrNull(i) as? AndroidTabImpl
                        val rawTitle = androidTab?.titleVal?.takeIf { it.isNotEmpty() } ?: tabTitles[i]
                        val displayTitle = buildString {
                            if (androidTab?.pinnedVal == true) append("\uD83D\uDCCC ")
                            if (androidTab?.dirtyVal == true) append("\u2022 ")
                            append(rawTitle)
                        }
                        val iconSrc = androidTab?.iconVal?.takeIf { it.isNotBlank() }
                        val iconSizeDp = androidTab?.iconSizePxVal ?: 20
                        val tintToTheme = androidTab?.tintImageToThemeVal ?: true
                        val iconTintArgb = if (tintToTheme) labelFallbackColor else null
                        val resolvedDrawable = if (iconSrc != null) {
                            AndroidImageImpl.resolveDrawable(ctx, iconSrc, iconTintArgb)?.also { d ->
                                val px = AndroidViewHandle.dpToPx(iconSizeDp).coerceAtLeast(14)
                                d.setBounds(0, 0, px, px)
                            }
                        } else {
                            null
                        }

                        val tab = layout.newTab()
                        tab.text = displayTitle
                        val needsCustomView = androidTab != null && (
                            androidTab.customHeaderImpl != null ||
                                androidTab.editableVal ||
                                (androidTab.closableVal && !androidTab.pinnedVal)
                            )

                        if (needsCustomView && androidTab != null) {
                            tab.customView = buildInteractiveTabCustomView(
                                ctx = ctx,
                                index = i,
                                title = rawTitle,
                                displayTitle = displayTitle,
                                androidTab = androidTab,
                                isSelected = i == selectedIndexVal,
                                labelColor = labelFallbackColor,
                                resolvedDrawable = resolvedDrawable,
                                iconSizeDp = iconSizeDp
                            )
                        } else if (resolvedDrawable != null) {
                            tab.icon = resolvedDrawable
                        }

                        val bText = androidTab?.badgeTextVal
                        if (!bText.isNullOrEmpty()) {
                            runCatching {
                                tab.orCreateBadge.apply {
                                    isVisible = true
                                    text = bText
                                }
                            }
                        } else {
                            runCatching { tab.removeBadge() }
                        }

                        layout.addTab(tab, i == selectedIndexVal)
                    }
                    if (tabTitles.isNotEmpty() && selectedIndexVal in tabTitles.indices) {
                        val targetTab = layout.getTabAt(selectedIndexVal)
                        if (targetTab != null && !targetTab.isSelected) {
                            targetTab.select()
                        }
                    }
                } finally {
                    suppressTabSelectionCallback = false
                }
            }
        }
    }

    private fun buildInteractiveTabCustomView(
        ctx: Context,
        index: Int,
        title: String,
        displayTitle: String,
        androidTab: AndroidTabImpl,
        isSelected: Boolean,
        labelColor: Int,
        resolvedDrawable: android.graphics.drawable.Drawable?,
        iconSizeDp: Int
    ): View {
        val customHeaderView = androidTab.customHeaderImpl?.let { headerImpl ->
            (headerImpl.nativeWidget as? AndroidViewHandle)?.androidView
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.TRANSPARENT)
        }

        if (customHeaderView != null) {
            (customHeaderView.parent as? ViewGroup)?.removeView(customHeaderView)
            row.addView(
                customHeaderView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            return row
        }

        if (resolvedDrawable != null) {
            val iconPx = AndroidViewHandle.dpToPx(iconSizeDp).coerceAtLeast(14)
            row.addView(
                AppCompatImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(resolvedDrawable)
                },
                LinearLayout.LayoutParams(iconPx, iconPx).apply {
                    rightMargin = AndroidViewHandle.dpToPx(6)
                }
            )
        }

        val titleTv = TextView(ctx).apply {
            id = android.R.id.text1
            text = displayTitle
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(labelColor)
            isSingleLine = true
        }
        row.addView(titleTv)

        if (androidTab.editableVal) {
            val editTv = AppCompatEditText(ctx).apply {
                setText(title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(labelColor)
                isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE
                visibility = View.GONE
            }
            fun finishEdit() {
                if (editTv.visibility != View.VISIBLE) return
                val updated = editTv.text?.toString()?.trim().orEmpty().ifEmpty { title }
                editTv.visibility = View.GONE
                titleTv.visibility = View.VISIBLE
                if (updated != title) {
                    setTabTitleNative(index, updated)
                    androidTab.dispatchTitleEdited(updated)
                }
            }
            editTv.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    finishEdit()
                    true
                } else {
                    false
                }
            }
            editTv.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    finishEdit()
                    true
                } else {
                    false
                }
            }
            editTv.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) finishEdit()
            }
            val detector = runCatching {
                GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        setSelectedTabIndex(index, false)
                        return true
                    }

                    override fun onDoubleTap(e: MotionEvent): Boolean {
                        titleTv.visibility = View.GONE
                        editTv.visibility = View.VISIBLE
                        editTv.setText(title)
                        val canFocus = this@AndroidTabContainerImpl.isFocusable() && androidTab.isFocusable()
                        editTv.isFocusable = canFocus
                        editTv.isFocusableInTouchMode = canFocus
                        if (canFocus) {
                            editTv.requestFocus()
                        }
                        editTv.selectAll()
                        return true
                    }
                })
            }.getOrNull()
            titleTv.setOnTouchListener { _, event ->
                detector?.onTouchEvent(event) ?: false
            }
            titleTv.setOnLongClickListener {
                titleTv.visibility = View.GONE
                editTv.visibility = View.VISIBLE
                editTv.setText(title)
                val canFocus = this@AndroidTabContainerImpl.isFocusable() && androidTab.isFocusable()
                editTv.isFocusable = canFocus
                editTv.isFocusableInTouchMode = canFocus
                if (canFocus) {
                    editTv.requestFocus()
                }
                editTv.selectAll()
                true
            }
            row.addView(editTv)
        }

        if (androidTab.closableVal && !androidTab.pinnedVal) {
            row.addView(
                AppCompatButton(ctx).apply {
                    text = "✕"
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(labelColor)
                    setPadding(AndroidViewHandle.dpToPx(6), 0, AndroidViewHandle.dpToPx(2), 0)
                    setOnClickListener {
                        handleCloseTabIndex(index, androidTab)
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = AndroidViewHandle.dpToPx(4)
                }
            )
        }

        return row
    }

    private fun hideTabPlaceholderViews() {
        for (tabImpl in tabImpls) {
            val tabView = (tabImpl?.nativeWidget as? AndroidViewHandle)?.androidView
            if (tabView != null && tabView.visibility != View.GONE) {
                tabView.visibility = View.GONE
            }
        }
    }

    override fun ensureAndroidView(context: Context?): View? {
        val view = super.ensureAndroidView(context)
        AndroidViewHandle.runOnMainThread {
            runCatching { hideTabPlaceholderViews() }
        }
        return view
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching { hideTabPlaceholderViews() }
        }
        rebuildMaterialTabs()
    }
}
