package com.nexustools.genericui.android

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidSystemThemeAndControlTintTest {

    @AfterTest
    fun resetThemeConfig() {
        com.nexustools.genericui.component.shell.Application.current?.setThemeConfig(ThemeConfig.DEFAULT)
        GenericUI.defaultThemeConfig = ThemeConfig.DEFAULT
    }

    @Test
    fun buttonUsesSystemDefaultWhenNoneAndOffscreenSpriteTintWhenTyped() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val btn = AndroidButtonImpl()
        btn.setText("Confirm")
        btn.setType(BootstrapType.NONE)
        assertEquals("systemDefault", btn.nativeWidget.attributes["materialButtonStyle"])
        assertEquals("true", btn.nativeWidget.attributes["useSystemControlAppearance"])
        assertNull(btn.nativeWidget.attributes["offscreenSpriteTinted"])

        val customPrimary = RgbaColor(200, 40, 90, 255)
        val app = AndroidApplicationImpl()
        val customTheme = ThemeConfig(
            lightColors = ThemeColorSet(primary = customPrimary),
            darkColors = ThemeColorSet(primary = customPrimary)
        )
        app.setThemeConfig(customTheme)
        assertEquals(customTheme, app.getThemeConfig())
        assertEquals(customPrimary.toHex(), app.nativeApplication.attributes["themePrimaryColor"])

        btn.setType(BootstrapType.PRIMARY)
        assertEquals("filled-primary", btn.nativeWidget.attributes["materialButtonStyle"])
        assertEquals("true", btn.nativeWidget.attributes["offscreenSpriteTinted"])
        assertEquals(customPrimary.toHex(), btn.nativeWidget.attributes["bootstrapTintColor"])

        val pixels = btn.renderOffscreenButtonPixels(width = 80, height = 32)
        assertEquals(80 * 32, pixels.size)
        val centerArgb = pixels[16 * 80 + 40]
        val r = (centerArgb ushr 16) and 0xFF
        val b = centerArgb and 0xFF
        assertTrue(r > 130 && b > 35, "Offscreen tinted Android button pixels should reflect custom ThemeConfig primary (r=$r, b=$b)")
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

        val stdBtn = AndroidButtonImpl()
        stdBtn.applyComponentStyle(style)
        assertNull(stdBtn.nativeWidget.attributes["backgroundColor"])
        assertNull(stdBtn.nativeWidget.attributes["borderRadiusPx"])
        assertNull(stdBtn.nativeWidget.attributes["fontSizePx"])

        val stdContainer = AndroidContainerImpl()
        stdContainer.applyComponentStyle(style)
        assertNull(stdContainer.nativeWidget.attributes["backgroundColor"])
        assertNull(stdContainer.nativeWidget.attributes["borderRadiusPx"])

        val styledBtn = AndroidStyledButtonImpl()
        styledBtn.applyComponentStyle(style)
        assertEquals(RgbaColor(255, 0, 0, 255).toCssRgbaString(), styledBtn.nativeWidget.attributes["backgroundColor"])
        assertEquals("12.0", styledBtn.nativeWidget.attributes["borderRadiusPx"])

        val styledContainer = AndroidStyledContainerImpl()
        styledContainer.applyComponentStyle(style)
        assertEquals(RgbaColor(255, 0, 0, 255).toCssRgbaString(), styledContainer.nativeWidget.attributes["backgroundColor"])
        assertEquals("12.0", styledContainer.nativeWidget.attributes["borderRadiusPx"])
    }

    @Test
    fun textAndMarkdownViewHonorTypographyAndBootstrapStyles() {
        val text = AndroidTextImpl()
        text.setText("hello android")
        val typoStyle = ComponentStyle().apply {
            setTextColor(RgbaColor(30, 140, 230, 255))
            setFontFamily(CssFontFamily.named("Serif"))
            setFontSizePx(18f)
            setFontWeight(FontWeight.BOLD)
            setFontStyle(CssFontStyle.ITALIC)
            setTextAlign(TextAlignment.CENTER)
            setTextDecoration(CssTextDecoration.UNDERLINE)
            setTextTransform(TextTransform.UPPERCASE)
        }
        text.applyComponentStyle(typoStyle)
        assertEquals("HELLO ANDROID", text.getText())
        assertEquals("18.0", text.nativeWidget.attributes["fontSizePx"])
        assertEquals("center", text.nativeWidget.attributes["textAlign"])

        val md = AndroidMarkdownViewImpl()
        md.setMarkdown("markdown body")
        md.applyComponentStyle(typoStyle)
        assertEquals("MARKDOWN BODY", md.nativeWidget.attributes["renderedText"])
        assertEquals("18.0", md.nativeWidget.attributes["fontSizePx"])
    }

    @Test
    fun tableColumnWeightsAndLazyGridMinColumnWidthAreHonored() {
        val table = AndroidTableImpl()
        table.setHeaders(listOf("ColA", "ColB", "ColC"))
        table.setColumnWeights(floatArrayOf(1f, 2f, 3f))
        assertContentEquals(floatArrayOf(1f, 2f, 3f), table.getColumnWeights())
        assertEquals("1.0,2.0,3.0", table.nativeWidget.attributes["columnWeights"])

        val grid = AndroidLazyGridImpl()
        grid.setColumns(4)
        grid.setMinColumnWidthPx(160)
        assertEquals(160, grid.getMinColumnWidthPx())
        assertEquals(2, grid.resolveEffectiveColumns(availableWidthPx = 350))
        assertEquals(4, grid.resolveEffectiveColumns(availableWidthPx = 700))
    }

    @Test
    fun pillOutlinedButtonRendersRoundedCornersAndTextInputStoresLabel() {
        val factory = AndroidNativeFactory()
        assertTrue(factory.displayDensityScale > 0f)

        val customPrimary = RgbaColor(200, 40, 90, 255)
        val app = AndroidApplicationImpl()
        app.setThemeConfig(
            ThemeConfig(
                lightColors = ThemeColorSet(primary = customPrimary),
                darkColors = ThemeColorSet(primary = customPrimary)
            )
        )

        val pillOutlineBtn = AndroidButtonImpl()
        pillOutlineBtn.setText("Music")
        pillOutlineBtn.setType(BootstrapType.PRIMARY)
        pillOutlineBtn.setOutline(true)
        pillOutlineBtn.setBorderRadius(BorderRadiusStyle.PILL)

        val pixels = pillOutlineBtn.renderOffscreenButtonPixels(width = 80, height = 32)
        assertEquals(80 * 32, pixels.size)

        val cornerAlpha = (pixels[0] ushr 24) and 0xFF
        assertEquals(0, cornerAlpha, "Pill button outer corner pixel must remain transparent")

        val topEdgeArgb = pixels[40]
        val topEdgeAlpha = (topEdgeArgb ushr 24) and 0xFF
        val topEdgeR = (topEdgeArgb ushr 16) and 0xFF
        val topEdgeB = topEdgeArgb and 0xFF
        assertTrue(topEdgeAlpha >= 220, "Pill outline top border pixel should be opaque (alpha=$topEdgeAlpha)")
        assertTrue(topEdgeR > 130 && topEdgeB > 35, "Pill outline border pixel should carry primary tint (r=$topEdgeR, b=$topEdgeB)")

        val centerAlpha = (pixels[16 * 80 + 40] ushr 24) and 0xFF
        assertTrue(centerAlpha in 1..30, "Pill outline interior pixel should remain low-alpha tinted (alpha=$centerAlpha)")

        val input = AndroidTextInputImpl()
        assertNull(input.getLabel())
        input.setLabel("Server Address")
        assertEquals("Server Address", input.getLabel())
        assertEquals("Server Address", input.nativeWidget.attributes["label"])
        input.setLabel("   ")
        assertNull(input.getLabel())
        assertNull(input.nativeWidget.attributes["label"])
    }
}
