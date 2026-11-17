@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.theme.ThemeColorSet
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.valueForKey
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIButton
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UILabel
import platform.UIKit.UIView
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosSystemThemeAndControlTintTest {

    @AfterTest
    fun resetThemeConfig() {
        com.nexustools.genericui.component.shell.Application.current?.setThemeConfig(ThemeConfig.DEFAULT)
        GenericUI.defaultThemeConfig = ThemeConfig.DEFAULT
    }

    @Test
    fun buttonUsesSystemDefaultWhenNoneAndOffscreenSpriteTintWhenTyped() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val btn = IosButtonImpl()
        btn.setText("Done")
        btn.setType(BootstrapType.NONE)
        val uiButton = btn.nativeWidget.uiView as UIButton
        assertEquals("Done", uiButton.titleForState(UIControlStateNormal))
        assertNull(uiButton.backgroundImageForState(UIControlStateNormal))
        assertNull(btn.lastOffscreenTintedPixels)

        val customPrimary = RgbaColor(195, 38, 88, 255)
        val app = IosApplicationImpl()
        val customTheme = ThemeConfig(
            lightColors = ThemeColorSet(primary = customPrimary),
            darkColors = ThemeColorSet(primary = customPrimary)
        )
        app.setThemeConfig(customTheme)
        assertEquals(customTheme, app.getThemeConfig())

        btn.setType(BootstrapType.PRIMARY)
        assertNotNull(uiButton.backgroundImageForState(UIControlStateNormal))
        val pixels = btn.lastOffscreenTintedPixels
        assertNotNull(pixels)
        val centerArgb = pixels[(pixels.size / 2)]
        val r = (centerArgb ushr 16) and 0xFF
        val b = centerArgb and 0xFF
        assertTrue(r > 130 && b > 35, "Offscreen tinted UIButton sprite pixels should reflect custom ThemeConfig primary (r=$r, b=$b)")
        app.dispose()
    }

    @Test
    fun standardControlsIgnoreAestheticAndTypographyComponentStyleOverridesWhileStyledHonorsThem() {
        val style = ComponentStyle().apply {
            setBackgroundColor(RgbaColor(255, 0, 0, 255))
            setTextColor(RgbaColor(0, 255, 0, 255))
            setBorderColor(RgbaColor(0, 0, 255, 255))
            setBorderWidth(CssEdgeLengths.all(CssLength.px(4f)))
            setBorderRadius(CssCornerRadii.px(12f))
            setFontFamily(CssFontFamily.named("Monospace"))
            setFontSizePx(22f)
            setFontWeight(FontWeight.BOLD)
            setFontStyle(CssFontStyle.ITALIC)
        }

        val stdBtn = IosButtonImpl()
        stdBtn.applyComponentStyle(style)
        assertNull(stdBtn.nativeWidget.uiView.backgroundColor)
        assertEquals(0.0, stdBtn.nativeWidget.caLayer.cornerRadius)

        val stdContainer = IosContainerImpl()
        stdContainer.applyComponentStyle(style)
        assertNull(stdContainer.nativeWidget.uiView.backgroundColor)
        assertEquals(0.0, stdContainer.nativeWidget.caLayer.cornerRadius)

        val styledBtn = IosStyledButtonImpl()
        styledBtn.applyComponentStyle(style)
        assertNotNull(styledBtn.nativeWidget.uiView.backgroundColor)
        assertEquals(12.0, styledBtn.nativeWidget.caLayer.cornerRadius)
        assertRedGreenBlue(styledBtn.nativeWidget.uiView.backgroundColor!!, 1.0, 0.0, 0.0)

        val styledContainer = IosStyledContainerImpl()
        styledContainer.applyComponentStyle(style)
        assertNotNull(styledContainer.nativeWidget.uiView.backgroundColor)
        assertEquals(12.0, styledContainer.nativeWidget.caLayer.cornerRadius)
        assertRedGreenBlue(styledContainer.nativeWidget.uiView.backgroundColor!!, 1.0, 0.0, 0.0)
    }

    @Test
    fun textAndMarkdownViewHonorTypographyAndBootstrapStyles() {
        val text = IosTextImpl()
        text.setText("hello ios")
        val typoStyle = ComponentStyle().apply {
            setTextColor(RgbaColor(35, 145, 235, 255))
            setFontFamily(CssFontFamily.named("Serif"))
            setFontSizePx(19f)
            setFontWeight(FontWeight.BOLD)
            setFontStyle(CssFontStyle.ITALIC)
            setTextAlign(TextAlignment.CENTER)
            setTextDecoration(CssTextDecoration.UNDERLINE)
            setTextTransform(TextTransform.UPPERCASE)
        }
        text.applyComponentStyle(typoStyle)
        assertEquals("HELLO IOS", text.label.attributedText?.string ?: text.label.text)
        assertEquals(NSTextAlignmentCenter, text.label.textAlignment)
        assertEquals(19.0, text.label.font.pointSize)

        val md = IosMarkdownViewImpl()
        md.setMarkdown("ios markdown")
        md.applyComponentStyle(typoStyle)
        assertEquals("IOS MARKDOWN", md.textView.attributedText.string.trim())
        assertEquals(NSTextAlignmentCenter, md.textView.textAlignment)
        assertFalse(md.textView.editable)
    }

    @Test
    fun tableColumnWeightsAndLazyGridMinColumnWidthAreHonored() {
        val table = IosTableImpl()
        table.setHeaders(listOf("A", "B", "C"))
        table.setColumnWeights(floatArrayOf(1f, 2f, 3f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f), table.getColumnWeights())

        val grid = IosLazyGridImpl()
        grid.setColumns(4)
        grid.setMinColumnWidthPx(150)
        assertEquals(150, grid.getMinColumnWidthPx())
        assertEquals(2, grid.resolveEffectiveColumns(availableWidthPx = 350))
        assertEquals(4, grid.resolveEffectiveColumns(availableWidthPx = 700))
    }

    @Test
    fun textInputSupportsLabelAndFactoryProvidesDensityScale() {
        val factory = IosNativeFactory()
        assertTrue(factory.displayDensityScale > 0f)

        val input = IosTextInputImpl()
        assertNull(input.getLabel())
        input.setLabel("Server Address")
        assertEquals("Server Address", input.getLabel())
        val captionView = input.nativeWidget.uiView.subviews
            .filterIsInstance<UILabel>()
            .firstOrNull { (it.valueForKey("accessibilityIdentifier") as? String) == "textinput-caption-label" }
        assertNotNull(captionView)
        assertEquals("Server Address", captionView.text)
        assertFalse(captionView.hidden)

        input.setLabel(null)
        assertNull(input.getLabel())
        assertTrue(
            input.nativeWidget.uiView.subviews
                .filterIsInstance<UILabel>()
                .none { (it.valueForKey("accessibilityIdentifier") as? String) == "textinput-caption-label" }
        )
    }

    @Test
    fun launcherCreateRootViewControllerReturnsGenericUiRootViewController() {
        val descriptor = GenericUiAppDescriptor(
            appId = "com.nexustools.genericui.iosdemo",
            appName = "IosDemo",
            windowTitle = "iOS Demo"
        )
        val vc = IosLauncher.createRootViewController(descriptor)
        assertTrue(vc is GenericUiRootViewController)
        assertNotNull(vc.view)
        IosLauncher.activeApplication?.dispose()
    }

    private fun assertRedGreenBlue(color: platform.UIKit.UIColor, er: Double, eg: Double, eb: Double) = memScoped {
        val r = alloc<DoubleVar>()
        val g = alloc<DoubleVar>()
        val b = alloc<DoubleVar>()
        val a = alloc<DoubleVar>()
        assertTrue(color.getRed(r.ptr, green = g.ptr, blue = b.ptr, alpha = a.ptr))
        assertTrue(kotlin.math.abs(r.value - er) < 0.02)
        assertTrue(kotlin.math.abs(g.value - eg) < 0.02)
        assertTrue(kotlin.math.abs(b.value - eb) < 0.02)
    }
}
