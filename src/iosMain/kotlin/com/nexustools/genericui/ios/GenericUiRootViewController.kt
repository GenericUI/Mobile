@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.mobile.layout.MobileSafeAreaTracker
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSize
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIKeyCommand
import platform.UIKit.UIMenuBuilderProtocol
import platform.UIKit.UIPress
import platform.UIKit.UIPressesEvent
import platform.UIKit.UITextField
import platform.UIKit.UITextView
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIViewControllerTransitionCoordinatorProtocol
import platform.UIKit.addKeyCommand
import platform.UIKit.removeKeyCommand

/**
 * Primary `UIViewController` hosting an iOS or iPadOS GenericUI window, wiring:
 * - `viewDidLayoutSubviews` and `viewWillTransitionToSize` to [safeAreaTracker] and [onViewportResized]
 * - `traitCollectionDidChange` to [SystemThemeDetector.notifySystemThemeChanged]
 * - `pressesBegan` / `pressesEnded` hardware keyboard forwarding (`KeyEvent`)
 * - `UIKeyCommand` registration + `buildMenuWithBuilder` for iPadOS / iOS menus
 */
class GenericUiRootViewController(
    val hostView: UIView = UikitFlexContainerView(UikitEnvironment.mainScreenBounds()),
    val safeAreaTracker: MobileSafeAreaTracker = MobileSafeAreaTracker()
) : UIViewController(nibName = null, bundle = null) {

    var onLayoutSubviews: (() -> Unit)? = null
    var onViewportResized: ((widthPt: Int, heightPt: Int) -> Unit)? = null
    var onKeyDown: ((KeyEvent) -> Unit)? = null
    var onKeyUp: ((KeyEvent) -> Unit)? = null
    var onBuildMenus: ((UIMenuBuilderProtocol) -> Unit)? = null

    private val shortcutCallbacks = LinkedHashMap<String, () -> Unit>()
    private val registeredCommands = mutableListOf<UIKeyCommand>()

    val keyCommands: List<UIKeyCommand>
        get() = registeredCommands.toList()

    override fun loadView() {
        view = hostView
    }

    fun registerKeyShortcut(shortcut: KeyShortcut, title: String, action: () -> Unit): UIKeyCommand {
        val (input, modifiers) = UikitGeometryAndColorUtil.toUikitKeyInputAndModifiers(shortcut)
        val propertyKey = "${modifiers}:$input:$title"
        shortcutCallbacks[propertyKey] = action
        val cmd = UIKeyCommand.commandWithTitle(
            title = title,
            image = null,
            action = NSSelectorFromString("handleRegisteredKeyCommand:"),
            input = input,
            modifierFlags = modifiers,
            propertyList = propertyKey
        )
        registeredCommands += cmd
        addKeyCommand(cmd)
        return cmd
    }

    fun clearRegisteredKeyShortcuts() {
        for (cmd in registeredCommands) {
            removeKeyCommand(cmd)
        }
        registeredCommands.clear()
        shortcutCallbacks.clear()
    }

    @ObjCAction
    fun handleRegisteredKeyCommand(sender: UIKeyCommand?) {
        val key = sender?.propertyList as? String ?: return
        shortcutCallbacks[key]?.invoke()
    }

    override fun viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        val insets = view.safeAreaInsets.useContents {
            com.nexustools.genericui.geometry.EdgeInsets(
                top = top.toInt(),
                right = right.toInt(),
                bottom = bottom.toInt(),
                left = left.toInt()
            )
        }
        safeAreaTracker.updateSystemBarInsets(insets)
        view.bounds.useContents {
            onViewportResized?.invoke(size.width.toInt(), size.height.toInt())
        }
        onLayoutSubviews?.invoke()
    }

    override fun viewWillTransitionToSize(
        size: CValue<CGSize>,
        withTransitionCoordinator: UIViewControllerTransitionCoordinatorProtocol
    ) {
        super.viewWillTransitionToSize(size, withTransitionCoordinator)
        size.useContents {
            onViewportResized?.invoke(width.toInt(), height.toInt())
        }
    }

    override fun traitCollectionDidChange(previousTraitCollection: UITraitCollection?) {
        super.traitCollectionDidChange(previousTraitCollection)
        val currentStyle = traitCollection.userInterfaceStyle
        if (previousTraitCollection == null || previousTraitCollection.userInterfaceStyle != currentStyle) {
            val mode = if (currentStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark) {
                com.nexustools.genericui.enums.ui.ThemeMode.DARK
            } else {
                com.nexustools.genericui.enums.ui.ThemeMode.LIGHT
            }
            SystemThemeDetector.notifySystemThemeChanged(mode)
        }
    }

    override fun buildMenuWithBuilder(builder: UIMenuBuilderProtocol) {
        super.buildMenuWithBuilder(builder)
        onBuildMenus?.invoke(builder)
    }

    override fun pressesBegan(presses: Set<*>, withEvent: UIPressesEvent?) {
        dispatchKeyPresses(presses, isDown = true)
        super.pressesBegan(presses, withEvent)
    }

    override fun pressesEnded(presses: Set<*>, withEvent: UIPressesEvent?) {
        dispatchKeyPresses(presses, isDown = false)
        super.pressesEnded(presses, withEvent)
    }

    private fun dispatchKeyPresses(presses: Set<*>, isDown: Boolean) {
        val handler = if (isDown) onKeyDown else onKeyUp
        if (handler == null) return
        val firstResponder = findFirstResponder(view)
        val isTextInput = firstResponder is UITextField || firstResponder is UITextView
        for (item in presses) {
            val press = item as? UIPress ?: continue
            val uiKey = press.key ?: continue
            handler(UikitGeometryAndColorUtil.uiKeyToKeyEvent(uiKey, isTextInput))
        }
    }

    private fun findFirstResponder(root: UIView): UIView? {
        if (root.isFirstResponder) return root
        for (child in root.subviews) {
            val sub = child as? UIView ?: continue
            findFirstResponder(sub)?.let { return it }
        }
        return null
    }
}
