package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabIconPlacement
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android tab bar + content host implementation of [TabContainerNativeImpl],
 * rendering tab titles, icons, badges, pin/dirty indicators, close buttons,
 * inline editable tab titles, and custom tab header views.
 */
class AndroidTabContainerImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "nav-tabs"), TabContainerNativeImpl<AndroidViewHandle> {
    override val supportsNativeFadeTransition: Boolean = true

    private val tabImpls = mutableListOf<TabNativeImpl<*>?>()
    private val tabTitles = mutableListOf<String>()
    private val tabContents = mutableListOf<NativeImpl<*>?>()
    private var selectedIndexVal: Int = 0
    private var fullWidthTabsVal: Boolean = false
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    private var headerScroll: HorizontalScrollView? = null
    private var headerRow: LinearLayout? = null
    private var headerBottomBorder: View? = null
    private var contentHost: AndroidFlexContainerView? = null

    override fun createDefaultAndroidView(context: Context): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
        }
        headerScroll = scroll
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val padH = AndroidViewHandle.dpToPx(4)
            val padV = AndroidViewHandle.dpToPx(4)
            setPadding(padH, padV, padH, padV)
        }
        headerRow = row
        scroll.addView(
            row,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        val borderView = View(context).apply {
            val isDark = AndroidViewHandle.isDarkTheme()
            setBackgroundColor(if (isDark) 0xFF2A2B36.toInt() else 0xFFD0D5DD.toInt())
        }
        headerBottomBorder = borderView
        val host = AndroidFlexContainerView(context)
        contentHost = host
        nativeWidget.childHostViewGroup = host

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            borderView,
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
        return root
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
        nativeWidget.attributes["tabPosition"] = position.name
    }

    override fun setTabBarStyleNative(style: TabBarStyle) {
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
            rebuildHeaderButtons()
        }
    }

    private fun rebuildHeaderButtons() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val row = headerRow ?: return@runCatching
                val ctx = row.context ?: return@runCatching
                row.removeAllViews()
                val isDark = AndroidViewHandle.isDarkTheme()
                headerBottomBorder?.setBackgroundColor(if (isDark) 0xFF2A2B36.toInt() else 0xFFD0D5DD.toInt())

                val anyTopIcon = tabImpls.any {
                    val t = it as? AndroidTabImpl
                    t != null && t.iconPlacementVal == TabIconPlacement.TOP && !t.iconVal.isNullOrBlank()
                }
                val useEqualWidth = fullWidthTabsVal || anyTopIcon
                headerScroll?.isFillViewport = useEqualWidth
                if (useEqualWidth) {
                    row.setPadding(0, 0, 0, 0)
                } else {
                    val padH = AndroidViewHandle.dpToPx(4)
                    val padV = AndroidViewHandle.dpToPx(4)
                    row.setPadding(padH, padV, padH, padV)
                }

                val activeAccentArgb = if (isDark) 0xFF9AB8F7.toInt() else 0xFF234B9E.toInt()
                val inactiveLabelArgb = if (isDark) 0xFFE3E2E6.toInt() else 0xFF49454F.toInt()
                val primaryArgb = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark).toArgbInt()
                val inactiveBgArgb = if (isDark) 0xFF1E222A.toInt() else 0xFFEAECEF.toInt()
                val inactiveFgArgb = if (isDark) 0xFFB8C0D0.toInt() else 0xFF495057.toInt()

                for (i in tabTitles.indices) {
                    val androidTab = tabImpls.getOrNull(i) as? AndroidTabImpl
                    val title = androidTab?.titleVal?.takeIf { it.isNotEmpty() } ?: tabTitles[i]
                    val isSelected = i == selectedIndexVal
                    val iconSrc = androidTab?.iconVal?.takeIf { it.isNotBlank() }
                    val iconSizeDp = androidTab?.iconSizePxVal ?: 20
                    val placement = androidTab?.iconPlacementVal ?: TabIconPlacement.START
                    val tintToTheme = androidTab?.tintImageToThemeVal ?: true
                    val iconTintArgb = if (tintToTheme) {
                        if (isDark) 0xFFFFFFFF.toInt() else 0xFF1C1B1F.toInt()
                    } else {
                        null
                    }
                    val resolvedDrawable = if (iconSrc != null) {
                        AndroidImageImpl.resolveDrawable(ctx, iconSrc, iconTintArgb)
                    } else {
                        null
                    }

                    val hasRichDecorations = androidTab != null && (
                        androidTab.closableVal ||
                            androidTab.dirtyVal ||
                            androidTab.pinnedVal ||
                            androidTab.editableVal ||
                            !androidTab.badgeTextVal.isNullOrEmpty()
                        )

                    if (placement == TabIconPlacement.TOP && (resolvedDrawable != null || iconSrc != null)) {
                        val tabItem = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_HORIZONTAL
                            isClickable = true
                            isFocusable = true
                            val padTop = AndroidViewHandle.dpToPx(8)
                            val padH = AndroidViewHandle.dpToPx(4)
                            setPadding(padH, padTop, padH, 0)
                            setOnClickListener {
                                setSelectedTabIndex(i, false)
                            }
                        }

                        val iconPx = AndroidViewHandle.dpToPx(iconSizeDp).coerceAtLeast(16)
                        val iconView = AppCompatImageView(ctx).apply {
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            if (resolvedDrawable != null) {
                                setImageDrawable(resolvedDrawable)
                            }
                        }
                        tabItem.addView(
                            iconView,
                            LinearLayout.LayoutParams(iconPx, iconPx).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                            }
                        )

                        val labelText = if (androidTab?.dirtyVal == true) "• $title" else title
                        val labelView = TextView(ctx).apply {
                            text = labelText
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(if (isSelected) activeAccentArgb else (if (isDark) 0xFF9AB8F7.toInt() else inactiveLabelArgb))
                        }
                        tabItem.addView(
                            labelView,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                                topMargin = AndroidViewHandle.dpToPx(6)
                                bottomMargin = AndroidViewHandle.dpToPx(8)
                            }
                        )

                        val indicatorView = View(ctx).apply {
                            setBackgroundColor(if (isSelected) activeAccentArgb else Color.TRANSPARENT)
                        }
                        tabItem.addView(
                            indicatorView,
                            LinearLayout.LayoutParams(
                                AndroidViewHandle.dpToPx(56),
                                AndroidViewHandle.dpToPx(3).coerceAtLeast(2)
                            ).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                            }
                        )

                        val lp = if (useEqualWidth) {
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        } else {
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        row.addView(tabItem, lp)
                    } else if (hasRichDecorations) {
                        val chipRow = buildDecoratedTabChip(
                            ctx = ctx,
                            index = i,
                            title = title,
                            androidTab = androidTab!!,
                            isSelected = isSelected,
                            isDark = isDark,
                            primaryArgb = primaryArgb,
                            inactiveBgArgb = inactiveBgArgb,
                            inactiveFgArgb = inactiveFgArgb,
                            resolvedDrawable = resolvedDrawable,
                            iconSizeDp = iconSizeDp
                        )
                        val lp = if (useEqualWidth) {
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        } else {
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                if (i > 0) leftMargin = AndroidViewHandle.dpToPx(6)
                            }
                        }
                        row.addView(chipRow, lp)
                    } else if (useEqualWidth) {
                        val tabItem = LinearLayout(ctx).apply {
                            orientation = LinearLayout.VERTICAL
                            gravity = Gravity.CENTER_HORIZONTAL
                            isClickable = true
                            isFocusable = true
                            setOnClickListener {
                                setSelectedTabIndex(i, false)
                            }
                        }
                        val labelView = TextView(ctx).apply {
                            text = title
                            gravity = Gravity.CENTER
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (isSelected) activeAccentArgb else (if (isDark) 0xFF9AB8F7.toInt() else inactiveLabelArgb))
                            val padH = AndroidViewHandle.dpToPx(8)
                            val padV = AndroidViewHandle.dpToPx(12)
                            setPadding(padH, padV, padH, padV)
                            if (resolvedDrawable != null) {
                                val px = AndroidViewHandle.dpToPx(iconSizeDp).coerceAtLeast(14)
                                resolvedDrawable.setBounds(0, 0, px, px)
                                setCompoundDrawables(resolvedDrawable, null, null, null)
                                compoundDrawablePadding = AndroidViewHandle.dpToPx(6)
                            }
                        }
                        tabItem.addView(
                            labelView,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                gravity = Gravity.CENTER_HORIZONTAL
                            }
                        )
                        val indicatorView = View(ctx).apply {
                            setBackgroundColor(if (isSelected) activeAccentArgb else Color.TRANSPARENT)
                        }
                        tabItem.addView(
                            indicatorView,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                AndroidViewHandle.dpToPx(3).coerceAtLeast(2)
                            )
                        )
                        row.addView(
                            tabItem,
                            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        )
                    } else {
                        val btn = AppCompatButton(ctx).apply {
                            text = title
                            isAllCaps = false
                            gravity = Gravity.CENTER
                            minWidth = 0
                            minimumWidth = 0
                            minHeight = AndroidViewHandle.dpToPx(34)
                            minimumHeight = AndroidViewHandle.dpToPx(34)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                            setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else inactiveFgArgb)
                            val hPad = AndroidViewHandle.dpToPx(14)
                            val vPad = AndroidViewHandle.dpToPx(6)
                            setPadding(hPad, vPad, hPad, vPad)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = AndroidViewHandle.dpToPxFloat(6f)
                                setColor(if (isSelected) primaryArgb else inactiveBgArgb)
                            }
                            if (resolvedDrawable != null) {
                                val px = AndroidViewHandle.dpToPx(iconSizeDp).coerceAtLeast(14)
                                resolvedDrawable.setBounds(0, 0, px, px)
                                setCompoundDrawables(resolvedDrawable, null, null, null)
                                compoundDrawablePadding = if (title.isNotEmpty()) AndroidViewHandle.dpToPx(6) else 0
                            }
                            setOnClickListener {
                                setSelectedTabIndex(i, false)
                            }
                        }
                        val lp = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (i > 0) {
                                leftMargin = AndroidViewHandle.dpToPx(6)
                            }
                        }
                        row.addView(btn, lp)
                    }
                }
            }
        }
    }

    private fun buildDecoratedTabChip(
        ctx: Context,
        index: Int,
        title: String,
        androidTab: AndroidTabImpl,
        isSelected: Boolean,
        isDark: Boolean,
        primaryArgb: Int,
        inactiveBgArgb: Int,
        inactiveFgArgb: Int,
        resolvedDrawable: android.graphics.drawable.Drawable?,
        iconSizeDp: Int
    ): View {
        val fgColor = if (isSelected) 0xFFFFFFFF.toInt() else inactiveFgArgb
        val chip = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            minimumHeight = AndroidViewHandle.dpToPx(34)
            val hPad = AndroidViewHandle.dpToPx(12)
            val vPad = AndroidViewHandle.dpToPx(6)
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AndroidViewHandle.dpToPxFloat(6f)
                setColor(if (isSelected) primaryArgb else inactiveBgArgb)
            }
            setOnClickListener {
                setSelectedTabIndex(index, false)
            }
        }

        if (androidTab.pinnedVal) {
            chip.addView(
                TextView(ctx).apply {
                    text = "📌"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setPadding(0, 0, AndroidViewHandle.dpToPx(4), 0)
                }
            )
        }

        if (resolvedDrawable != null) {
            val iconPx = AndroidViewHandle.dpToPx(iconSizeDp).coerceAtLeast(14)
            chip.addView(
                AppCompatImageView(ctx).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setImageDrawable(resolvedDrawable)
                },
                LinearLayout.LayoutParams(iconPx, iconPx).apply {
                    rightMargin = AndroidViewHandle.dpToPx(6)
                }
            )
        }

        if (androidTab.dirtyVal) {
            chip.addView(
                TextView(ctx).apply {
                    text = "•"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(BootstrapColorPalette.resolveColor(BootstrapType.WARNING, isDark).toArgbInt())
                    setPadding(0, 0, AndroidViewHandle.dpToPx(4), 0)
                }
            )
        }

        val titleTv = TextView(ctx).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(fgColor)
            isSingleLine = true
        }
        chip.addView(titleTv)

        if (androidTab.editableVal) {
            val editTv = AppCompatEditText(ctx).apply {
                setText(title)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setTextColor(fgColor)
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
                        editTv.requestFocus()
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
                editTv.requestFocus()
                editTv.selectAll()
                true
            }
            chip.addView(editTv)
        }

        val bText = androidTab.badgeTextVal
        if (!bText.isNullOrEmpty()) {
            val badgeType = when (androidTab.badgeVariantVal.lowercase()) {
                "danger", "error" -> BootstrapType.DANGER
                "success" -> BootstrapType.SUCCESS
                "warning" -> BootstrapType.WARNING
                "secondary" -> BootstrapType.SECONDARY
                "info" -> BootstrapType.INFO
                else -> BootstrapType.PRIMARY
            }
            val pillColor = BootstrapColorPalette.resolveColor(badgeType, isDark).toArgbInt()
            chip.addView(
                TextView(ctx).apply {
                    text = bText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    val bPadH = AndroidViewHandle.dpToPx(6)
                    val bPadV = AndroidViewHandle.dpToPx(2)
                    setPadding(bPadH, bPadV, bPadH, bPadV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = AndroidViewHandle.dpToPxFloat(10f)
                        setColor(pillColor)
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = AndroidViewHandle.dpToPx(6)
                }
            )
        }

        if (androidTab.closableVal && !androidTab.pinnedVal) {
            chip.addView(
                AppCompatButton(ctx).apply {
                    text = "✕"
                    isAllCaps = false
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setBackgroundColor(Color.TRANSPARENT)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(fgColor)
                    setPadding(AndroidViewHandle.dpToPx(4), 0, AndroidViewHandle.dpToPx(2), 0)
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

        return chip
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
        rebuildHeaderButtons()
    }
}
