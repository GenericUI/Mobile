package com.nexustools.genericui.ios

import com.nexustools.genericui.core.GenericUI
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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
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
        assertEquals("UIButtonTypeSystem", btn.nativeWidget.metadata["uiButtonType"])
        assertEquals("systemDefault", btn.nativeWidget.metadata["uiButtonConfiguration"])
        assertEquals("true", btn.nativeWidget.metadata["useSystemControlAppearance"])
        assertNull(btn.nativeWidget.metadata["offscreenSpriteTinted"])
        assertNull(btn.lastOffscreenTintedPixels)

        val customPrimary = RgbaColor(195, 38, 88, 255)
        val app = IosApplicationImpl()
        val customTheme = ThemeConfig(
            lightColors = ThemeColorSet(primary = customPrimary),
            darkColors = ThemeColorSet(primary = customPrimary)
        )
        app.setThemeConfig(customTheme)
        assertEquals(customTheme, app.getThemeConfig())
        assertEquals(customPrimary.toHex(), app.nativeApplication.metadata["themePrimaryColor"])

        btn.setType(BootstrapType.PRIMARY)
        assertEquals("filled", btn.nativeWidget.metadata["uiButtonConfiguration"])
        assertEquals("true", btn.nativeWidget.metadata["offscreenSpriteTinted"])
        assertEquals(customPrimary.toHex(), btn.nativeWidget.metadata["baseBackgroundColor"])
        val pixels = btn.lastOffscreenTintedPixels
        assertNotNull(pixels)
        val centerArgb = pixels[(pixels.size / 2)]
        val r = (centerArgb ushr 16) and 0xFF
        val b = centerArgb and 0xFF
        assertTrue(r > 130 && b > 35, "Offscreen tinted iOS button pixels should reflect custom ThemeConfig primary (r=$r, b=$b)")
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
        assertNull(stdBtn.nativeWidget.backgroundColor)
        assertEquals(0f, stdBtn.nativeWidget.cornerRadius)
        assertNull(stdBtn.nativeWidget.metadata["fontSizePx"])

        val stdContainer = IosContainerImpl()
        stdContainer.applyComponentStyle(style)
        assertNull(stdContainer.nativeWidget.backgroundColor)
        assertEquals(0f, stdContainer.nativeWidget.cornerRadius)

        val styledBtn = IosStyledButtonImpl()
        styledBtn.applyComponentStyle(style)
        assertEquals(RgbaColor(255, 0, 0, 255), styledBtn.nativeWidget.backgroundColor)
        assertEquals(12f, styledBtn.nativeWidget.cornerRadius)

        val styledContainer = IosStyledContainerImpl()
        styledContainer.applyComponentStyle(style)
        assertEquals(RgbaColor(255, 0, 0, 255), styledContainer.nativeWidget.backgroundColor)
        assertEquals(12f, styledContainer.nativeWidget.cornerRadius)
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
        assertEquals("HELLO IOS", text.getText())
        assertEquals(RgbaColor(35, 145, 235, 255), text.nativeWidget.tintColor)
        assertEquals("NSTextAlignmentCenter", text.nativeWidget.metadata["textAlignment"])
        assertEquals("19.0", text.nativeWidget.metadata["fontSizePx"])

        val md = IosMarkdownViewImpl()
        md.setMarkdown("ios markdown")
        md.applyComponentStyle(typoStyle)
        assertEquals("IOS MARKDOWN", md.nativeWidget.metadata["renderedText"])
        assertEquals("NSTextAlignmentCenter", md.nativeWidget.metadata["textAlignment"])
    }

    @Test
    fun tableColumnWeightsAndLazyGridMinColumnWidthAreHonored() {
        val table = IosTableImpl()
        table.setHeaders(listOf("A", "B", "C"))
        table.setColumnWeights(floatArrayOf(1f, 2f, 3f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f), table.getColumnWeights())
        assertEquals("1.0,2.0,3.0", table.nativeWidget.metadata["columnWeights"])

        val grid = IosLazyGridImpl()
        grid.setColumns(4)
        grid.setMinColumnWidthPx(150)
        assertEquals(150, grid.getMinColumnWidthPx())
        assertEquals(2, grid.resolveEffectiveColumns(availableWidthPx = 350))
        assertEquals(4, grid.resolveEffectiveColumns(availableWidthPx = 700))
    }
}
