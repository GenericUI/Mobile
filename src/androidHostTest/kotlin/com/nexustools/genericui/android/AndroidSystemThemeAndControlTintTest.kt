package com.nexustools.genericui.android

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapPointerEvents
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
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
    fun buttonUsesSystemDefaultWhenNoneAndNativeTintAttributesWhenTyped() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val btn = AndroidButtonImpl()
        btn.setText("Confirm")
        btn.setType(BootstrapType.NONE)
        assertEquals("systemDefault", btn.nativeWidget.attributes["materialButtonStyle"])
        assertEquals("true", btn.nativeWidget.attributes["useSystemControlAppearance"])
        assertNull(btn.nativeWidget.attributes["nativeButtonTinted"])
        assertNull(btn.nativeWidget.attributes["offscreenSpriteTinted"])
        assertNull(btn.nativeWidget.attributes["bootstrapTintColor"])

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
        assertEquals("false", btn.nativeWidget.attributes["useSystemControlAppearance"])
        assertEquals("true", btn.nativeWidget.attributes["nativeButtonTinted"])
        assertNull(btn.nativeWidget.attributes["offscreenSpriteTinted"])
        assertEquals(customPrimary.toHex(), btn.nativeWidget.attributes["bootstrapTintColor"])
        val expectedFg = BootstrapColorPalette.resolveForegroundOn(
            BootstrapType.PRIMARY,
            AndroidViewHandle.isDarkTheme(),
            customTheme
        )
        assertEquals(expectedFg.toHex(), btn.nativeWidget.attributes["bootstrapForegroundColor"])
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
    fun pillOutlinedButtonConfiguresNativeAttributesAndTextInputStoresLabel() {
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

        assertEquals("outlined-primary", pillOutlineBtn.nativeWidget.attributes["materialButtonStyle"])
        assertEquals("false", pillOutlineBtn.nativeWidget.attributes["useSystemControlAppearance"])
        assertEquals("true", pillOutlineBtn.nativeWidget.attributes["nativeButtonTinted"])
        assertNull(pillOutlineBtn.nativeWidget.attributes["offscreenSpriteTinted"])
        assertEquals(customPrimary.toHex(), pillOutlineBtn.nativeWidget.attributes["bootstrapTintColor"])
        assertEquals(customPrimary.toHex(), pillOutlineBtn.nativeWidget.attributes["bootstrapForegroundColor"])
        assertEquals(BorderRadiusStyle.PILL, pillOutlineBtn.getBorderRadius())
        assertTrue(pillOutlineBtn.nativeWidget.attributes["enumClasses"].orEmpty().contains("rounded-pill"))

        val input = AndroidTextInputImpl()
        assertNull(input.getLabel())
        input.setLabel("Server Address")
        assertEquals("Server Address", input.getLabel())
        assertEquals("Server Address", input.nativeWidget.attributes["label"])
        input.setLabel("   ")
        assertNull(input.getLabel())
        assertNull(input.nativeWidget.attributes["label"])
    }

    @Test
    fun strictBootstrapTypePolicyPermitsButtonsAndNonControlsOnlyWhileStyledControlsIgnoreBootstrapType() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val prevFactory = GenericUI.activeFactory
        val factory = AndroidNativeFactory()
        GenericUI.initialize(factory)

        val customPrimary = RgbaColor(200, 40, 90, 255)
        val app = AndroidApplicationImpl()
        try {
            app.setThemeConfig(
                ThemeConfig(
                    lightColors = ThemeColorSet(primary = customPrimary),
                    darkColors = ThemeColorSet(primary = customPrimary)
                )
            )

            // Button & StyledButton DO reflect BootstrapType
            val button = AndroidButtonImpl()
            button.setType(BootstrapType.PRIMARY)
            assertEquals("filled-primary", button.nativeWidget.attributes["materialButtonStyle"])
            assertEquals("true", button.nativeWidget.attributes["nativeButtonTinted"])
            assertEquals(customPrimary.toHex(), button.nativeWidget.attributes["bootstrapTintColor"])

            val styledBtn = AndroidStyledButtonImpl()
            styledBtn.setType(BootstrapType.PRIMARY)
            assertEquals(customPrimary.toHex(), styledBtn.nativeWidget.attributes["bootstrapTintColor"])

            // Bootstrap non-controls (Badge, Alert, Card) DO reflect BootstrapType
            val badge = AndroidTextImpl("span", "badge").apply {
                setText("New")
                setType(BootstrapType.DANGER)
            }
            assertEquals("false", badge.nativeWidget.attributes["useSystemControlAppearance"])

            val card = AndroidStyledContainerImpl("card", "genericui:card").apply {
                setType(BootstrapType.PRIMARY)
            }
            assertEquals(customPrimary, card.getStyledVisuals().backgroundColor)

            val alert = AndroidContainerImpl("alert").apply {
                setAttribute("data-genericui-tag", "genericui:alert")
                setType(BootstrapType.WARNING)
            }
            assertEquals("false", alert.nativeWidget.attributes["useSystemControlAppearance"])

            // DropdownButton & SplitDropdownButton MUST ignore BootstrapType on Android
            val dropdown = AndroidDropdownImpl()
            dropdown.setLabel("Options")
            dropdown.setType(BootstrapType.PRIMARY)
            assertEquals("systemDefault", dropdown.nativeWidget.attributes["materialButtonStyle"])
            assertEquals("true", dropdown.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(dropdown.nativeWidget.attributes["nativeButtonTinted"])
            assertNull(dropdown.nativeWidget.attributes["offscreenSpriteTinted"])
            assertNull(dropdown.nativeWidget.attributes["bootstrapTintColor"])

            // StyledComponent / StyledContainer / Composite / StyledSlider MUST ignore BootstrapType on Android
            val styledComp = AndroidStyledComponentImpl().apply {
                setType(BootstrapType.DANGER)
                setBackgroundType(BootstrapType.SUCCESS)
            }
            assertEquals("true", styledComp.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(styledComp.resolveCurrentVisuals().backgroundColor)

            val styledContainer = AndroidStyledContainerImpl().apply {
                setType(BootstrapType.DANGER)
                setBackgroundType(BootstrapType.SUCCESS)
            }
            assertEquals("true", styledContainer.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(styledContainer.getStyledVisuals().backgroundColor)

            val composite = object : Composite() {}.apply { type = BootstrapType.DANGER }
            val slider = StyledSlider().apply { type = BootstrapType.DANGER }
            val host = VStack().apply {
                addChild(composite)
                addChild(slider)
            }
            val window = Window("AndroidPolicy").apply { setContent(host) }
            window.show(factory)

            val compositeImpl = composite.ensureNativeImpl() as AndroidStyledContainerImpl
            assertEquals("true", compositeImpl.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(compositeImpl.getStyledVisuals().backgroundColor)

            val sliderImpl = slider.ensureNativeImpl() as AndroidStyledContainerImpl
            assertEquals("true", sliderImpl.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(sliderImpl.getStyledVisuals().backgroundColor)
            window.dispose()

            val nonButtonControls = listOf<AndroidBaseNativeImpl>(
                AndroidDropdownImpl(),
                AndroidProgressBarImpl(),
                AndroidSpinnerImpl(),
                AndroidScrollViewImpl(),
                AndroidTabContainerImpl(),
                AndroidCheckboxImpl(),
                AndroidRangeSliderImpl(),
                AndroidSelectImpl(),
                AndroidTextInputImpl(),
                AndroidStyledTextInputImpl(),
                AndroidContainerImpl(),
                AndroidStyledContainerImpl(),
                AndroidStyledComponentImpl()
            )
            for (ctrl in nonButtonControls) {
                ctrl.setType(BootstrapType.DANGER)
                ctrl.setBackgroundType(BootstrapType.WARNING)
                assertEquals(
                    "true",
                    ctrl.nativeWidget.attributes["useSystemControlAppearance"],
                    "${ctrl::class.simpleName} must keep system control appearance"
                )
                assertNull(
                    ctrl.nativeWidget.attributes["bootstrapTintColor"],
                    "${ctrl::class.simpleName} must not receive bootstrapTintColor"
                )
                assertNull(
                    ctrl.nativeWidget.attributes["bootstrapForegroundColor"],
                    "${ctrl::class.simpleName} must not receive bootstrapForegroundColor"
                )
            }

            // Verify StyledComponent state transitions work cleanly
            val interactiveSc = AndroidStyledComponentImpl()
            interactiveSc.setStyledVisuals(StyledVisualSpec(backgroundColor = RgbaColor(10, 20, 30, 255)))
            interactiveSc.setHoverVisuals(StyledVisualSpec(backgroundColor = RgbaColor(20, 40, 60, 255)))
            interactiveSc.setActiveVisuals(StyledVisualSpec(backgroundColor = RgbaColor(5, 10, 15, 255)))
            interactiveSc.setDisabledVisuals(StyledVisualSpec(backgroundColor = RgbaColor(90, 90, 90, 255)))
            assertEquals(RgbaColor(10, 20, 30, 255), interactiveSc.resolveCurrentVisuals().backgroundColor)
            interactiveSc.setHoveredState(true)
            assertEquals(RgbaColor(20, 40, 60, 255), interactiveSc.resolveCurrentVisuals().backgroundColor)
            interactiveSc.setActivePressedState(true)
            assertEquals(RgbaColor(5, 10, 15, 255), interactiveSc.resolveCurrentVisuals().backgroundColor)
            interactiveSc.setEnabled(false)
            assertEquals(RgbaColor(90, 90, 90, 255), interactiveSc.resolveCurrentVisuals().backgroundColor)
        } finally {
            app.dispose()
            if (prevFactory != null) GenericUI.initialize(prevFactory)
        }
    }
}
