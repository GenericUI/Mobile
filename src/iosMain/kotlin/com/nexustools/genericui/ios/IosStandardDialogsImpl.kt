@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertActionStyleDestructive
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIAlertControllerStyleAlert
import platform.UIKit.UIColorPickerViewController
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

/**
 * Native UIKit `UIAlertController` + `UIColorPickerViewController` implementation of
 * [StandardDialogsNativeImpl].
 */
class IosStandardDialogsImpl : StandardDialogsNativeImpl {
    override var isDisposed: Boolean = false
    private var activeColorPickerDelegate: UikitColorPickerDelegate? = null

    override fun showAlert(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        type: BootstrapType,
        okButtonText: String,
        onDismissed: () -> Unit
    ) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addAction(
            UIAlertAction.actionWithTitle(
                title = okButtonText,
                style = UIAlertActionStyleDefault
            ) { _ -> onDismissed() }
        )
        if (!presentOnOwnerOrRoot(owner, alert)) {
            onDismissed()
        }
    }

    override fun showConfirm(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        type: BootstrapType,
        onResult: (Boolean) -> Unit
    ) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert
        )
        val confirmStyle = if (type == BootstrapType.DANGER) {
            UIAlertActionStyleDestructive
        } else {
            UIAlertActionStyleDefault
        }
        alert.addAction(UIAlertAction.actionWithTitle(cancelText, style = UIAlertActionStyleCancel) { _ -> onResult(false) })
        alert.addAction(UIAlertAction.actionWithTitle(confirmText, style = confirmStyle) { _ -> onResult(true) })
        if (!presentOnOwnerOrRoot(owner, alert)) {
            onResult(false)
        }
    }

    override fun showPrompt(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        defaultValue: String,
        placeholder: String,
        confirmText: String,
        cancelText: String,
        onResult: (String?) -> Unit
    ) {
        val alert = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleAlert
        )
        alert.addTextFieldWithConfigurationHandler { field ->
            field?.text = defaultValue
            field?.placeholder = placeholder
        }
        alert.addAction(UIAlertAction.actionWithTitle(cancelText, style = UIAlertActionStyleCancel) { _ -> onResult(null) })
        alert.addAction(
            UIAlertAction.actionWithTitle(confirmText, style = UIAlertActionStyleDefault) { _ ->
                val textField = alert.textFields?.firstOrNull() as? platform.UIKit.UITextField
                onResult(textField?.text ?: defaultValue)
            }
        )
        if (!presentOnOwnerOrRoot(owner, alert)) {
            onResult(null)
        }
    }

    override fun showChoice(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        options: List<String>,
        selectedIndex: Int,
        onResult: (Int?) -> Unit
    ) {
        val sheet = UIAlertController.alertControllerWithTitle(
            title = title,
            message = message,
            preferredStyle = UIAlertControllerStyleActionSheet
        )
        options.forEachIndexed { idx, opt ->
            val label = if (idx == selectedIndex) "✓ $opt" else opt
            sheet.addAction(
                UIAlertAction.actionWithTitle(label, style = UIAlertActionStyleDefault) { _ ->
                    onResult(idx)
                }
            )
        }
        sheet.addAction(UIAlertAction.actionWithTitle("Cancel", style = UIAlertActionStyleCancel) { _ -> onResult(null) })

        val presenter = resolvePresenter(owner)
        if (presenter != null) {
            sheet.popoverPresentationController?.let { pop ->
                pop.sourceView = presenter.view
                pop.sourceRect = presenter.view.bounds
            }
        }
        if (!presentOnOwnerOrRoot(owner, sheet)) {
            onResult(null)
        }
    }

    override fun showColorChooser(
        owner: WindowNativeImpl<*>?,
        title: String,
        initialColor: RgbaColor,
        onResult: (RgbaColor?) -> Unit
    ) {
        val picker = UIColorPickerViewController().apply {
            this.title = title
            selectedColor = initialColor.toUiColor()
        }
        val delegate = UikitColorPickerDelegate { chosen ->
            activeColorPickerDelegate = null
            onResult(chosen)
        }
        activeColorPickerDelegate = delegate
        picker.delegate = delegate
        if (!presentOnOwnerOrRoot(owner, picker)) {
            activeColorPickerDelegate = null
            onResult(null)
        }
    }

    private fun resolvePresenter(owner: WindowNativeImpl<*>?): UIViewController? =
        (owner?.nativeWidget as? UikitViewHandle)?.uiViewController
            ?: UikitEnvironment.topMostViewController()

    private fun presentOnOwnerOrRoot(owner: WindowNativeImpl<*>?, vc: UIViewController): Boolean {
        val presenter = resolvePresenter(owner)
        if (presenter != null && (presenter.view.window != null || UikitEnvironment.registeredKeyWindow != null)) {
            presenter.presentViewController(vc, animated = true, completion = null)
            return true
        }
        return UikitEnvironment.presentIfWindowAttached(vc, animated = true)
    }

    override fun dispose() {
        isDisposed = true
        activeColorPickerDelegate = null
    }
}
