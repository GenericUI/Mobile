@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.tabs.TabIconPlacement
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import kotlinx.coroutines.Job
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UILabel
import platform.UIKit.UITextField
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Native UIKit tab metadata and fallback header holder for [TabNativeImpl], consumed by
 * [IosTabContainerImpl]'s `UISegmentedControl` without applying custom Bootstrap chip styling.
 */
class IosTabImpl(
    val closable: Boolean = false
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 128.0, 38.0)).apply {
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            itemGapPt = 4.0
        }
    ),
    componentClassPrefix = "tab"
), TabNativeImpl<UikitViewHandle> {

    val selectButton: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 84.0, 34.0))
    }
    private val defaultSelectTintColor = selectButton.tintColor
    private val closeButton: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 22.0, 22.0))
        setTitle("×", forState = UIControlStateNormal)
    }
    private val badgeLabel: UILabel = UILabel(frame = CGRectMake(0.0, 0.0, 28.0, 18.0)).apply {
        font = UIFont.boldSystemFontOfSize(11.0)
        textAlignment = platform.UIKit.NSTextAlignmentCenter
        layer.cornerRadius = 9.0
        clipsToBounds = true
        hidden = true
    }
    private val inlineEditor: UITextField = UITextField(frame = CGRectMake(0.0, 0.0, 84.0, 30.0)).apply {
        hidden = true
    }
    private val closeTarget = UikitControlActionTarget()
    private val editorDelegate = UikitTextInputDelegate()

    private var tabId: String = ""
    private var tabTitle: String = ""
    private var tabIcon: String? = null
    private var iconSizePxVal: Int = 20
    private var iconPlacementVal: TabIconPlacement = TabIconPlacement.START
    private var tintImageToThemeVal: Boolean = true
    private var loadedBaseIconImage: UIImage? = null
    private var activeIconLoadJob: Job? = null
    private var iconLoadGeneration: Int = 0
    private var dirtyState: Boolean = false
    private var closableState: Boolean = closable
    private var pinnedState: Boolean = false
    private var editableState: Boolean = false
    private var selectedState: Boolean = false
    private var badgeTextVal: String? = null
    private var customHeaderImpl: NativeImpl<*>? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onTitleEditedListener: ((String) -> Unit)? = null
    var onSelectTabRequested: (() -> Unit)? = null
    internal var onTabUpdated: (() -> Unit)? = null

    internal fun currentTitle(): String = tabTitle
    internal fun currentIcon(): String? = tabIcon
    internal fun currentIconSizePx(): Int = iconSizePxVal
    internal fun isTintImageToTheme(): Boolean = tintImageToThemeVal
    internal fun isDirty(): Boolean = dirtyState
    internal fun isPinned(): Boolean = pinnedState
    internal fun isClosable(): Boolean = closableState
    internal fun currentBadgeText(): String? = badgeTextVal

    internal fun resolvedTabUiImage(): UIImage? {
        val base = loadedBaseIconImage ?: UikitGeometryAndColorUtil.systemIconImage(tabIcon) ?: return null
        val mode = if (tintImageToThemeVal) {
            UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate
        } else {
            UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal
        }
        return base.imageWithRenderingMode(mode)
    }

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(selectButton)
        host.addSubview(inlineEditor)
        host.addSubview(badgeLabel)
        host.addSubview(closeButton)

        selectButton.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        actionTarget.onPrimaryAction = {
            if (editableState && selectedState) {
                beginInlineEdit()
            } else {
                onSelectTabRequested?.invoke()
            }
        }
        closeButton.addTarget(
            target = closeTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        closeTarget.onPrimaryAction = {
            onCloseListener?.invoke()
        }
        inlineEditor.delegate = editorDelegate
        editorDelegate.onSubmit = { committed ->
            endInlineEdit(committed)
        }
        refreshTabHeader()
    }

    override fun setTabIdNative(id: String) {
        tabId = id
    }

    override fun setTitleNative(title: String) {
        tabTitle = title
        refreshTabHeader()
        onTabUpdated?.invoke()
    }

    override fun setIconNative(icon: String?) {
        tabIcon = icon
        activeIconLoadJob?.cancel()
        activeIconLoadJob = null
        loadedBaseIconImage = null
        val gen = ++iconLoadGeneration
        val trimmed = icon?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            applyResolvedSelectButtonIcon()
            onTabUpdated?.invoke()
            return
        }
        val sysImg = UikitGeometryAndColorUtil.systemIconImage(trimmed)
        if (sysImg != null && !trimmed.contains("://") && !trimmed.contains("/") &&
            !trimmed.startsWith("<svg", ignoreCase = true) && !trimmed.startsWith("data:", ignoreCase = true)
        ) {
            loadedBaseIconImage = sysImg
            applyResolvedSelectButtonIcon()
            onTabUpdated?.invoke()
            return
        }
        if (trimmed.startsWith("memory://", ignoreCase = true)) {
            ImageLoader.resolveMemoryBuffer(trimmed)?.let { buf ->
                loadedBaseIconImage = UikitGeometryAndColorUtil.imageBufferToUiImage(buf)
                applyResolvedSelectButtonIcon()
                onTabUpdated?.invoke()
                return
            }
        }
        applyResolvedSelectButtonIcon()
        onTabUpdated?.invoke()
        val px = iconSizePxVal.coerceIn(12, 32)
        activeIconLoadJob = ImageLoader.launchLoad(
            source = ImageSource.fromUrl(trimmed),
            options = ImageDecodeOptions(targetWidth = px, targetHeight = px, antialiasing = true),
            onLoaded = { buf ->
                val img = UikitGeometryAndColorUtil.imageBufferToUiImage(buf) ?: return@launchLoad
                dispatch_async(dispatch_get_main_queue()) {
                    if (gen != iconLoadGeneration || isDisposed) return@dispatch_async
                    loadedBaseIconImage = img
                    applyResolvedSelectButtonIcon()
                    onTabUpdated?.invoke()
                }
            },
            onError = {}
        )
    }

    override fun setIconSizePxNative(sizePx: Int) {
        val next = sizePx.coerceAtLeast(1)
        if (iconSizePxVal != next) {
            iconSizePxVal = next
            if (!tabIcon.isNullOrBlank()) {
                setIconNative(tabIcon)
                return
            }
        }
        onTabUpdated?.invoke()
    }

    override fun setIconPlacementNative(placement: TabIconPlacement) {
        iconPlacementVal = placement
        onTabUpdated?.invoke()
    }

    override fun setTintImageToThemeNative(tintToTheme: Boolean) {
        tintImageToThemeVal = tintToTheme
        applyResolvedSelectButtonIcon()
        onTabUpdated?.invoke()
    }

    private fun applyResolvedSelectButtonIcon() {
        if (tintImageToThemeVal) {
            selectButton.tintColor = defaultSelectTintColor
        }
        selectButton.setImage(resolvedTabUiImage(), forState = UIControlStateNormal)
    }

    override fun dispose() {
        activeIconLoadJob?.cancel()
        activeIconLoadJob = null
        loadedBaseIconImage = null
        super.dispose()
    }

    override fun setTooltipNative(tooltip: String?) {
        setTooltipText(tooltip)
    }

    override fun setClosableNative(closable: Boolean) {
        closableState = closable
        refreshTabHeader()
        onTabUpdated?.invoke()
    }

    override fun setDirtyNative(dirty: Boolean) {
        dirtyState = dirty
        refreshTabHeader()
        onTabUpdated?.invoke()
    }

    override fun setPinnedNative(pinned: Boolean) {
        pinnedState = pinned
        refreshTabHeader()
        onTabUpdated?.invoke()
    }

    override fun setEditableNative(editable: Boolean) {
        editableState = editable
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        val clean = badgeText?.takeIf { it.isNotEmpty() }
        badgeTextVal = clean
        badgeLabel.hidden = clean == null
        badgeLabel.text = clean
        onTabUpdated?.invoke()
    }

    override fun setSelectedNative(selected: Boolean) {
        selectedState = selected
        refreshTabHeader()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        (customHeaderImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        customHeaderImpl = customImpl
        val view = (customImpl?.nativeWidget as? UikitViewHandle)?.uiView
        selectButton.hidden = view != null
        if (view != null) {
            (uiView as UikitFlexContainerView).insertSubview(view, atIndex = 0L)
        }
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }

    private fun beginInlineEdit() {
        if (!isFocusable()) return
        inlineEditor.text = tabTitle
        inlineEditor.hidden = false
        selectButton.hidden = true
        inlineEditor.becomeFirstResponder()
    }

    private fun endInlineEdit(newTitle: String) {
        inlineEditor.hidden = true
        selectButton.hidden = customHeaderImpl != null
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty() && trimmed != tabTitle) {
            tabTitle = trimmed
            refreshTabHeader()
            onTitleEditedListener?.invoke(trimmed)
            onTabUpdated?.invoke()
        }
    }

    private fun refreshTabHeader() {
        val display = if (dirtyState) "$tabTitle •" else tabTitle
        selectButton.setTitle(display, forState = UIControlStateNormal)
        closeButton.hidden = !closableState || pinnedState
        uiView.backgroundColor = UIColor.clearColor
        uiView.setNeedsLayout()
    }
}
