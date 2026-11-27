@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssCornerRadii
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.GradientStop
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradientShape
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosBackdropAndSplitDropdownTest {

    @Test
    fun uikitBackdropSwitchesBetweenNativeBlurAndCoreImageTenFunctionUnderlay() {
        val parent = UIView(frame = CGRectMake(0.0, 0.0, 200.0, 140.0))
        parent.backgroundColor = platform.UIKit.UIColor.blueColor
        val container = IosStyledContainerImpl(
            frame = CGRectMake(20.0, 20.0, 140.0, 90.0),
            layoutTarget = LayoutBackendTarget.IOS
        )
        parent.addSubview(container.nativeWidget.uiView)

        container.setStyledVisuals(
            StyledVisualSpec(
                backdropBlurPx = 12f,
                borderRadiusPx = 10f
            )
        )
        assertTrue(container.backdropController.isActive)
        assertTrue(
            container.backdropController.isUsingCoreImageUnderlay,
            "Pure blur() must render via CoreImage Metal GPU pipeline instead of UIVisualEffectView"
        )

        container.setStyledVisuals(
            StyledVisualSpec(
                backgroundColor = RgbaColor(255, 255, 255, 48),
                backdropFilter = CssFilter(
                    blur = CssLength.px(8f),
                    grayscale = 0.75f,
                    contrast = 1.15f,
                    hueRotateDeg = 30f,
                    dropShadow = CssShadow.px(2f, 4f, 6f, color = RgbaColor(0, 0, 0, 160))
                ),
                borderRadiusPx = 10f
            )
        )
        assertTrue(container.backdropController.isActive)
        assertTrue(container.backdropController.isUsingCoreImageUnderlay)

        container.setStyledVisuals(StyledVisualSpec(backdropFilter = CssFilter.NONE))
        assertFalse(container.backdropController.isActive)
        assertFalse(container.backdropController.isUsingCoreImageUnderlay)
        container.dispose()
    }

    @Test
    fun styledComponentAndButtonUseSharedVisualPolicyAndSurfaceChrome() {
        val styledComp = IosStyledComponentImpl(frame = CGRectMake(0.0, 0.0, 120.0, 60.0))
        styledComp.setType(BootstrapType.DANGER)
        assertNull(styledComp.resolveCurrentVisuals().backgroundColor)

        val style = ComponentStyle().apply {
            borderRadius = CssCornerRadii.px(16, 4, 12, 8)
            borderColor = RgbaColor(255, 100, 50, 255)
            borderWidth = com.nexustools.genericui.style.css.CssEdgeLengths.all(CssLength.px(3f))
            borderStyle = CssBorderStyle.DASHED
        }
        styledComp.applyComponentStyle(style)
        val compSpec = styledComp.resolveCurrentVisuals()
        assertEquals(CssBorderStyle.DASHED, compSpec.borderStyle)
        assertEquals(3f, compSpec.borderWidthPx)
        assertNotNull(styledComp.nativeWidget.caLayer.sublayers?.firstOrNull())
        styledComp.dispose()

        val styledBtn = IosStyledButtonImpl()
        styledBtn.setType(BootstrapType.PRIMARY)
        assertNotNull(styledBtn.resolveCurrentVisuals().backgroundColor)
        styledBtn.setBaseVisuals(
            StyledVisualSpec(
                backgroundGradient = RadialGradient(
                    shape = RadialGradientShape.CIRCLE,
                    stops = listOf(
                        GradientStop(RgbaColor(255, 0, 128, 255), 0f),
                        GradientStop(RgbaColor(64, 0, 255, 255), 1f)
                    )
                )
            )
        )
        assertNotNull(styledBtn.resolveCurrentVisuals().backgroundGradient)
        styledBtn.dispose()
    }

    @Test
    fun iosDropdownSupportsPrimaryActionForSplitDropdownButton() {
        val dropdown = IosDropdownImpl()
        dropdown.setLabel("Run")
        dropdown.setItems(listOf("debug" to "Debug", "release" to "Release"))
        assertTrue(dropdown.button.showsMenuAsPrimaryAction)

        var primaryClicks = 0
        dropdown.setOnPrimaryAction { primaryClicks++ }
        assertFalse(dropdown.button.showsMenuAsPrimaryAction)
        assertTrue(dropdown.triggerPrimaryAction())
        assertEquals(1, primaryClicks)

        dropdown.setOnPrimaryAction(null)
        assertTrue(dropdown.button.showsMenuAsPrimaryAction)
        assertFalse(dropdown.triggerPrimaryAction())
    }
}
