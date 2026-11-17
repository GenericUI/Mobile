@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import platform.UIKit.UITextField
import platform.UIKit.UITextFieldDelegateProtocol
import platform.UIKit.UITextView
import platform.UIKit.UITextViewDelegateProtocol
import platform.darwin.NSObject

/**
 * `UITextFieldDelegate` + `UITextViewDelegate` bridge relaying editing permissions,
 * multiline `UITextView` changes, and Return-key submission back to [IosTextInputImpl].
 */
class UikitTextInputDelegate : NSObject(), UITextFieldDelegateProtocol, UITextViewDelegateProtocol {
    var isReadOnly: Boolean = false
    var onTextChanged: ((String) -> Unit)? = null
    var onSubmit: ((String) -> Unit)? = null
    var onFocusChanged: ((Boolean) -> Unit)? = null

    override fun textFieldShouldBeginEditing(textField: UITextField): Boolean = !isReadOnly

    override fun textFieldDidBeginEditing(textField: UITextField) {
        onFocusChanged?.invoke(true)
    }

    override fun textFieldDidEndEditing(textField: UITextField) {
        onFocusChanged?.invoke(false)
    }

    override fun textFieldShouldReturn(textField: UITextField): Boolean {
        onSubmit?.invoke(textField.text ?: "")
        textField.resignFirstResponder()
        return true
    }

    override fun textViewShouldBeginEditing(textView: UITextView): Boolean = !isReadOnly

    override fun textViewDidBeginEditing(textView: UITextView) {
        onFocusChanged?.invoke(true)
    }

    override fun textViewDidEndEditing(textView: UITextView) {
        onFocusChanged?.invoke(false)
    }

    override fun textViewDidChange(textView: UITextView) {
        onTextChanged?.invoke(textView.text)
    }
}
