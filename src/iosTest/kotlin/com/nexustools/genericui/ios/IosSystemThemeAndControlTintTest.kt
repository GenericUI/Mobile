@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.composite.Composite
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.form.styled.StyledSlider
import com.nexustools.genericui.component.form.styled.StyledTextArea
import com.nexustools.genericui.component.form.styled.StyledTextField
import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.styled.StyledComponent
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapPointerEvents
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssGenericFontFamily
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssFontFamily
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.GradientStop
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.theme.ThemeColorSet
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import platform.CoreGraphics.CGRectMake
import platform.Foundation.valueForKey
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIButtonConfigurationSize
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UILabel
import platform.UIKit.UISegmentedControl
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UITextBorderStyle
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
    fun buttonUsesSystemDefaultWhenNoneAndNativeUiButtonConfigurationWhenTyped() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val btn = IosButtonImpl()
        btn.setText("Done")
        btn.setType(BootstrapType.NONE)
        val uiButton = btn.nativeWidget.uiView as UIButton
        assertEquals("Done", uiButton.titleForState(UIControlStateNormal))
        assertNull(uiButton.configuration)
        assertNull(uiButton.backgroundImageForState(UIControlStateNormal))

        val customPrimary = RgbaColor(195, 38, 88, 255)
        val app = IosApplicationImpl()
        val customTheme = ThemeConfig(
            lightColors = ThemeColorSet(primary = customPrimary),
            darkColors = ThemeColorSet(primary = customPrimary)
        )
        app.setThemeConfig(customTheme)
        assertEquals(customTheme, app.getThemeConfig())

        btn.setType(BootstrapType.PRIMARY)
        val filledConfig = assertNotNull(uiButton.configuration)
        assertEquals("Done", filledConfig.title)
        assertNull(uiButton.backgroundImageForState(UIControlStateNormal))
        assertRedGreenBlue(
            assertNotNull(filledConfig.baseBackgroundColor),
            195.0 / 255.0,
            38.0 / 255.0,
            88.0 / 255.0
        )
        assertNotNull(filledConfig.baseForegroundColor)

        btn.setOutline(true)
        btn.setBorderRadius(BorderRadiusStyle.PILL)
        btn.setSize(BootstrapSize.LARGE)
        val outlineConfig = assertNotNull(uiButton.configuration)
        assertEquals(UIButtonConfigurationCornerStyleCapsule, outlineConfig.cornerStyle)
        assertEquals(UIButtonConfigurationSize.UIButtonConfigurationSizeLarge, outlineConfig.buttonSize)
        assertEquals(1.5, outlineConfig.background.strokeWidth)
        assertRedGreenBlue(
            assertNotNull(outlineConfig.background.strokeColor),
            195.0 / 255.0,
            38.0 / 255.0,
            88.0 / 255.0
        )
        assertRedGreenBlue(
            assertNotNull(outlineConfig.baseForegroundColor),
            195.0 / 255.0,
            38.0 / 255.0,
            88.0 / 255.0
        )

        btn.setType(BootstrapType.LINK)
        btn.setOutline(false)
        btn.setBorderRadius(null)
        val linkConfig = assertNotNull(uiButton.configuration)
        assertRedGreenBlue(
            assertNotNull(linkConfig.baseForegroundColor),
            195.0 / 255.0,
            38.0 / 255.0,
            88.0 / 255.0
        )

        btn.setType(BootstrapType.NONE)
        assertNull(uiButton.configuration)
        assertNull(uiButton.backgroundImageForState(UIControlStateNormal))
        assertEquals("Done", uiButton.titleForState(UIControlStateNormal))
        btn.dispose()
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

    @Test
    fun strictBootstrapTypePolicyPermitsButtonsAndNonControlsOnlyWhileStyledControlsIgnoreBootstrapType() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val prevFactory = GenericUI.activeFactory
        val factory = IosNativeFactory()
        GenericUI.initialize(factory)

        val customPrimary = RgbaColor(195, 38, 88, 255)
        val app = IosApplicationImpl()
        try {
            app.setThemeConfig(
                ThemeConfig(
                    lightColors = ThemeColorSet(primary = customPrimary),
                    darkColors = ThemeColorSet(primary = customPrimary)
                )
            )

            // Button & StyledButton DO reflect BootstrapType
            val btn = IosButtonImpl()
            btn.setType(BootstrapType.PRIMARY)
            assertNotNull(btn.button.configuration)
            assertRedGreenBlue(
                assertNotNull(btn.button.configuration?.baseBackgroundColor),
                195.0 / 255.0,
                38.0 / 255.0,
                88.0 / 255.0
            )
            btn.dispose()

            // Non-control Bootstrap components (Badge, Card) DO reflect BootstrapType
            val badge = IosTextImpl(classPrefix = "badge")
            badge.setType(BootstrapType.DANGER)
            assertNotNull(badge.nativeWidget.uiView.backgroundColor)

            val card = IosStyledContainerImpl(classPrefix = "card", tagName = "genericui:card")
            card.setType(BootstrapType.PRIMARY)
            assertNotNull(card.nativeWidget.uiView.backgroundColor)

            // DropdownButton MUST ignore BootstrapType on iOS
            val dropdown = IosDropdownImpl()
            dropdown.setLabel("Actions")
            dropdown.setType(BootstrapType.PRIMARY)
            assertNull(dropdown.button.backgroundImageForState(UIControlStateNormal))
            assertNull(dropdown.button.configuration)

            val pb = IosProgressBarImpl()
            pb.setProgress(0.5f)
            pb.setType(BootstrapType.DANGER)
            assertNull(pb.progressView.progressTintColor)
            assertNull(pb.nativeWidget.uiView.backgroundColor)

            val slider = IosRangeSliderImpl()
            slider.setType(BootstrapType.DANGER)
            assertNull(slider.slider.minimumTrackTintColor)
            assertNull(slider.nativeWidget.uiView.backgroundColor)

            val tc = IosTabContainerImpl()
            tc.setType(BootstrapType.DANGER)
            tc.setBackgroundType(BootstrapType.WARNING)
            assertNotNull(tc.segmentedControl)
            assertNull(tc.nativeWidget.uiView.backgroundColor)

            val t0 = IosTabImpl().apply { setTitleNative("Home") }
            val t1 = IosTabImpl().apply { setTitleNative("Profile") }
            tc.addTabNative(t0, IosContainerImpl(), -1)
            tc.addTabNative(t1, IosContainerImpl(), -1)
            assertEquals(2, tc.segmentedControl.numberOfSegments.toInt())
            assertEquals("Home", tc.segmentedControl.titleForSegmentAtIndex(0uL))
            assertEquals("Profile", tc.segmentedControl.titleForSegmentAtIndex(1uL))
            assertEquals(0, tc.segmentedControl.selectedSegmentIndex.toInt())

            tc.setSelectedTabIndex(1, fadeTransition = false)
            assertEquals(1, tc.getSelectedTabIndex())
            assertEquals(1, tc.segmentedControl.selectedSegmentIndex.toInt())

            tc.setTabPositionNative(TabPosition.BOTTOM)
            tc.segmentedControl.frame.useContents {
                assertEquals(32.0, size.height)
                assertTrue(origin.y > 0.0)
            }
            tc.setTabPositionNative(TabPosition.TOP)
            tc.segmentedControl.frame.useContents {
                assertEquals(32.0, size.height)
                assertEquals(0.0, origin.y)
            }

            // StyledComponent / StyledContainer / Composite / StyledSlider / Text(non-badge) / Controls MUST ignore BootstrapType
            val composite = object : Composite() {}.apply { type = BootstrapType.DANGER }
            val styledSlider = StyledSlider().apply { type = BootstrapType.DANGER }
            val styledCompComp = StyledComponent().apply { type = BootstrapType.DANGER }
            val host = Column().apply {
                addChild(composite)
                addChild(styledSlider)
                addChild(styledCompComp)
            }
            val win = Window("iOSPolicy").apply { setContent(host) }
            win.show(factory)

            val compositeImpl = composite.ensureNativeImpl() as IosStyledContainerImpl
            assertNull(compositeImpl.nativeWidget.uiView.backgroundColor)

            val sliderImpl = styledSlider.ensureNativeImpl() as IosStyledContainerImpl
            assertNull(sliderImpl.nativeWidget.uiView.backgroundColor)

            val scImpl = styledCompComp.ensureNativeImpl() as IosStyledComponentImpl
            assertNull(scImpl.nativeWidget.uiView.backgroundColor)
            assertNull(scImpl.resolveCurrentVisuals().backgroundColor)
            win.dispose()

            val untinted = listOf<IosBaseNativeImpl>(
                IosDropdownImpl(),
                IosScrollViewImpl(),
                IosCheckboxImpl(),
                IosContainerImpl(),
                IosStyledContainerImpl(),
                IosStyledComponentImpl(),
                IosStyledTextInputImpl(),
                IosTextImpl()
            )
            for (ctrl in untinted) {
                ctrl.setType(BootstrapType.DANGER)
                ctrl.setBackgroundType(BootstrapType.SUCCESS)
                ctrl.setBorderType(BootstrapType.WARNING)
                val bg = ctrl.nativeWidget.uiView.backgroundColor
                assertTrue(
                    bg == null || bg == platform.UIKit.UIColor.clearColor,
                    "${ctrl::class.simpleName} must ignore BootstrapType on iOS (got $bg)"
                )
                assertEquals(0.0, ctrl.nativeWidget.caLayer.borderWidth)
            }
        } finally {
            app.dispose()
            if (prevFactory != null) GenericUI.initialize(prevFactory)
        }
    }

    @Test
    fun iosStyledComponentProximalRelativeWindowAbsoluteSignedTransformZPositionAndPointerEventsPassThrough() {
        val factory = IosNativeFactory()
        val sc = factory.createStyledComponent("styled-component", "genericui:styled-component") as IosStyledComponentImpl
        sc.setStyledVisuals(StyledVisualSpec(backgroundColor = RgbaColor(20, 40, 60, 255)))
        sc.setHoverVisuals(StyledVisualSpec(backgroundColor = RgbaColor(40, 80, 120, 255)))
        sc.setActiveVisuals(StyledVisualSpec(backgroundColor = RgbaColor(10, 15, 20, 255)))
        sc.setDisabledVisuals(StyledVisualSpec(backgroundColor = RgbaColor(128, 128, 128, 255)))
        assertEquals(RgbaColor(20, 40, 60, 255), sc.resolveCurrentVisuals().backgroundColor)
        sc.setHoveredState(true)
        assertEquals(RgbaColor(40, 80, 120, 255), sc.resolveCurrentVisuals().backgroundColor)
        sc.setActivePressedState(true)
        assertEquals(RgbaColor(10, 15, 20, 255), sc.resolveCurrentVisuals().backgroundColor)
        sc.setEnabled(false)
        assertEquals(RgbaColor(128, 128, 128, 255), sc.resolveCurrentVisuals().backgroundColor)
        sc.setEnabled(true)

        // pointer-events: none sets userInteractionEnabled = false and preserves it across setEnabled(true)
        sc.applyComponentBootstrap(ComponentBootstrap().apply { setPointerEvents(BootstrapPointerEvents.NONE) })
        assertFalse(sc.nativeWidget.uiView.userInteractionEnabled)
        sc.setEnabled(true)
        assertFalse(sc.nativeWidget.uiView.userInteractionEnabled, "pointer-events: none must stay false across setEnabled(true)")

        // Proximal position: relative ancestor + signed transform + zPosition + subview ordering
        val relAncestor = IosContainerImpl()
        relAncestor.applyComponentStyle(ComponentStyle().apply {
            setPosition(CssPosition.RELATIVE)
            setWidth(CssLength.px(400f))
            setHeight(CssLength.px(300f))
        })
        relAncestor.nativeWidget.uiView.setFrame(CGRectMake(0.0, 0.0, 400.0, 300.0))

        val staticMid = IosContainerImpl()
        staticMid.setMargin(EdgeInsets(50, 0, 0, 20))
        staticMid.setPreferredSize(Size(300, 200))

        val lowerZ = IosStyledComponentImpl()
        lowerZ.applyComponentStyle(ComponentStyle().apply {
            setPosition(CssPosition.ABSOLUTE)
            setTop(CssLength.px(20f))
            setRight(CssLength.px(10f))
            setZIndex(5)
            setWidth(CssLength.px(80f))
            setHeight(CssLength.px(30f))
        })

        val upperZ = IosStyledComponentImpl()
        upperZ.applyComponentStyle(ComponentStyle().apply {
            setPosition(CssPosition.ABSOLUTE)
            setTop(CssLength.px(20f))
            setRight(CssLength.px(10f))
            setZIndex(25)
            setWidth(CssLength.px(80f))
            setHeight(CssLength.px(30f))
            setTransform(
                com.nexustools.genericui.style.transition.CssTransform2D(
                    translateX = -10f,
                    translateY = -5f,
                    scaleX = 1.25f
                )
            )
        })

        staticMid.addChildNative(upperZ, -1)
        staticMid.addChildNative(lowerZ, -1)
        relAncestor.addChildNative(staticMid, -1)

        relAncestor.nativeWidget.uiView.layoutIfNeeded()
        staticMid.nativeWidget.uiView.layoutIfNeeded()

        assertEquals(25.0, upperZ.nativeWidget.caLayer.zPosition)
        assertEquals(5.0, lowerZ.nativeWidget.caLayer.zPosition)
        // Highest zIndex must be last subview on UIView for hitTest priority
        val managedSubs = staticMid.nativeWidget.uiView.subviews
        assertEquals(upperZ.nativeWidget.uiView, managedSubs.last())

        // Signed transform applied cleanly as CGAffineTransform (tx = -10, ty = -5, a = 1.25)
        upperZ.nativeWidget.uiView.transform.useContents {
            assertEquals(1.25, a, 0.01)
            assertEquals(-10.0, tx, 0.01)
            assertEquals(-5.0, ty, 0.01)
        }
        // Center frame matches proximal relative (right: 10, top: 20 inside 400x300 minus mid origin (20, 50)) -> local origin (290, -30), center (330, -15)
        upperZ.nativeWidget.uiView.center.useContents {
            assertEquals(330.0, x, 0.5)
            assertEquals(-15.0, y, 0.5)
        }

        // Window-level position: absolute anchored to IosWindowImpl contentHostView
        val win = IosWindowImpl()
        win.setWindowSize(Size(800, 600))
        val winAbs = IosStyledComponentImpl()
        winAbs.applyComponentStyle(ComponentStyle().apply {
            setPosition(CssPosition.ABSOLUTE)
            setTop(CssLength.px(20f))
            setLeft(CssLength.percent(50f))
            setWidth(CssLength.px(100f))
            setHeight(CssLength.px(40f))
            setTransform(
                com.nexustools.genericui.style.transition.CssTransform2D(
                    translateX = -50f,
                    translateXIsPercent = true
                )
            )
        })
        win.addChildNative(winAbs, -1)
        val contentHost = winAbs.nativeWidget.uiView.superview!!
        contentHost.layoutIfNeeded()
        winAbs.nativeWidget.uiView.transform.useContents {
            assertEquals(-50.0, tx, 0.01)
        }
        win.dispose()
    }

    @Test
    fun iosStyledTextFieldAndTextAreaSupportVisualSpecsPillGradientStatesAndIgnoreBootstrapType() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val prevFactory = GenericUI.activeFactory
        val factory = IosNativeFactory()
        GenericUI.initialize(factory)

        try {
            val gradient = LinearGradient(
                angleDegrees = 90f,
                stops = listOf(
                    GradientStop(RgbaColor(20, 32, 60, 235), 0f),
                    GradientStop(RgbaColor(55, 85, 145, 235), 1f)
                )
            )
            val styledField = StyledTextField(placeholder = "Search...").apply {
                type = BootstrapType.DANGER
                setBackground(gradient)
                setBorder(widthPx = 2f, color = RgbaColor(90, 175, 255, 255), style = CssBorderStyle.SOLID)
                setPill(true)
                textColor = RgbaColor(245, 248, 255, 255)
                placeholderColor = RgbaColor(155, 180, 215, 255)
                setFont(family = CssFontFamily.generic(CssGenericFontFamily.MONOSPACE), sizePx = 16f, weight = FontWeight.BOLD)
                setPadding(EdgeInsets(top = 6, right = 14, bottom = 6, left = 14))
                setHoverStyle(backgroundColor = RgbaColor(35, 55, 95, 255))
                setFocusStyle(
                    borderColor = RgbaColor(255, 200, 90, 255),
                    focusRingColor = RgbaColor(255, 200, 90, 255),
                    focusRingWidthPx = 2f
                )
                disabledBackgroundColor = RgbaColor(64, 68, 78, 255)
            }
            val styledArea = StyledTextArea(placeholder = "Bio...").apply {
                backgroundColor = RgbaColor(24, 28, 36, 255)
                placeholderColor = RgbaColor(140, 150, 170, 255)
            }

            val host = Column().apply {
                addChild(styledField)
                addChild(styledArea)
            }
            val win = Window("iOS StyledTextField").apply { setContent(host) }
            win.show(factory)

            val fieldImpl = styledField.nativeImpl as? IosStyledTextInputImpl
            val areaImpl = styledArea.nativeImpl as? IosStyledTextInputImpl
            assertNotNull(fieldImpl)
            assertNotNull(areaImpl)
            assertEquals(UITextBorderStyle.UITextBorderStyleNone, fieldImpl.textField.borderStyle)
            assertNotNull(fieldImpl.textField.attributedPlaceholder)
            assertTrue(fieldImpl.nativeWidget.uiView.layer.cornerRadius > 0.0)
            assertNull(fieldImpl.resolveCurrentVisuals().backgroundColor) // base uses gradient, BootstrapType.DANGER ignored
            assertNotNull(fieldImpl.resolveCurrentVisuals().backgroundGradient)

            fieldImpl.setHoveredState(true)
            assertEquals(RgbaColor(35, 55, 95, 255), fieldImpl.resolveCurrentVisuals().backgroundColor)
            fieldImpl.setHoveredState(false)

            fieldImpl.setFocusedState(true)
            assertEquals(RgbaColor(255, 200, 90, 255), fieldImpl.resolveCurrentVisuals().borderColor)
            assertEquals(RgbaColor(255, 200, 90, 255), fieldImpl.resolveCurrentVisuals().focusRingColor)

            styledField.isEnabled = false
            assertEquals(RgbaColor(64, 68, 78, 255), fieldImpl.resolveCurrentVisuals().backgroundColor)

            assertNotNull(areaImpl.nativeWidget.uiView.backgroundColor)
            win.dispose()
        } finally {
            com.nexustools.genericui.component.shell.Application.current?.dispose()
            if (prevFactory != null) GenericUI.initialize(prevFactory) else GenericUI.activeFactory = null
        }
    }

    @Test
    fun iosLabelTargetWiresTapGestureRecognizerAndDelegatesFocus() {
        com.nexustools.genericui.component.shell.Application.current?.dispose()
        val prevFactory = GenericUI.activeFactory
        val factory = IosNativeFactory()
        GenericUI.initialize(factory)

        try {
            val styledField = StyledTextField(placeholder = "Email")
            val plainField = TextField(placeholder = "Notes")
            val checkBox = CheckBox("Subscribe")

            val styledLabel = Label("Email", target = styledField)
            val plainLabel = Label("Notes", target = plainField)
            val cbLabel = Label("Subscribe", target = checkBox)

            val host = Column().apply {
                addChild(styledLabel)
                addChild(styledField)
                addChild(plainLabel)
                addChild(plainField)
                addChild(cbLabel)
                addChild(checkBox)
            }
            val win = Window("iOS Label Target").apply { setContent(host) }
            win.show(factory)

            val styledLabelImpl = styledLabel.nativeImpl as IosTextImpl
            val cbLabelImpl = cbLabel.nativeImpl as IosTextImpl

            assertTrue(styledLabelImpl.label.userInteractionEnabled)
            assertTrue(cbLabelImpl.label.userInteractionEnabled)
            assertTrue(styledLabelImpl.label.gestureRecognizers.orEmpty().any { it is UITapGestureRecognizer })
            assertTrue(cbLabelImpl.label.gestureRecognizers.orEmpty().any { it is UITapGestureRecognizer })
            assertNull(styledLabel.getAttribute("for"))

            styledLabel.triggerClick()
            assertTrue(styledField.isFocused)
            assertTrue((styledField.nativeImpl as IosStyledTextInputImpl).isFocusedState())

            plainLabel.triggerClick()
            assertTrue(plainField.isFocused)
            assertTrue((plainField.nativeImpl as IosTextInputImpl).isFocusedState())

            cbLabel.triggerClick()
            assertTrue(checkBox.isFocused)
            assertTrue((checkBox.nativeImpl as IosCheckboxImpl).isFocusedState())

            styledField.isFocused = false
            styledLabel.target = null
            assertTrue(styledLabelImpl.label.gestureRecognizers.orEmpty().none { it is UITapGestureRecognizer })
            styledLabel.triggerClick()
            assertFalse(styledField.isFocused)

            win.dispose()
        } finally {
            com.nexustools.genericui.component.shell.Application.current?.dispose()
            if (prevFactory != null) GenericUI.initialize(prevFactory) else GenericUI.activeFactory = null
        }
    }
}
