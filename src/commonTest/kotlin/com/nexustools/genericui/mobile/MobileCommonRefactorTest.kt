package com.nexustools.genericui.mobile

import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.layout.AdaptiveCardLayout
import com.nexustools.genericui.component.layout.AdaptiveNavigationScaffoldLayout
import com.nexustools.genericui.component.layout.AspectRatioLayout
import com.nexustools.genericui.component.layout.Box
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayout
import com.nexustools.genericui.component.layout.CollapsiblePanelLayout
import com.nexustools.genericui.component.layout.ColumnLayout
import com.nexustools.genericui.component.layout.HybridColumnLayout
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.component.layout.LayeredLayout
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayout
import com.nexustools.genericui.component.layout.ResponsiveMasterDetailLayout
import com.nexustools.genericui.component.layout.SplitPaneLayout
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.mobile.layout.MobileLayoutFactory
import com.nexustools.genericui.mobile.system.MobileDocumentPickerBridge
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MobileCommonRefactorTest {

    @Test
    fun mobileLayoutFactoryCreatesAllCommonLayoutImplementations() {
        val sampleChild = Box()
        assertNotNull(MobileLayoutFactory.createColumnLayout(ColumnLayout()))
        assertNotNull(MobileLayoutFactory.createAdaptiveCardLayout(AdaptiveCardLayout()))
        assertNotNull(MobileLayoutFactory.createHybridColumnLayout(HybridColumnLayout()))
        assertNotNull(MobileLayoutFactory.createHybridSplitNavigationLayout(HybridSplitNavigationLayout()))
        assertNotNull(MobileLayoutFactory.createCollapsibleBottomDockLayout(CollapsibleBottomDockLayout()))
        assertNotNull(MobileLayoutFactory.createResponsiveMasterDetailLayout(ResponsiveMasterDetailLayout()))
        assertNotNull(MobileLayoutFactory.createResponsiveBentoGridLayout(ResponsiveBentoGridLayout()))
        assertNotNull(MobileLayoutFactory.createAdaptiveNavigationScaffoldLayout(AdaptiveNavigationScaffoldLayout()))
        assertNotNull(MobileLayoutFactory.createSplitPaneLayout(SplitPaneLayout().setPanes(sampleChild, Box())))
        assertNotNull(MobileLayoutFactory.createCollapsiblePanelLayout(CollapsiblePanelLayout(title = "Panel")))
        assertNotNull(MobileLayoutFactory.createAspectRatioLayout(AspectRatioLayout()))
        assertNotNull(MobileLayoutFactory.createLayeredLayout(LayeredLayout()))
    }

    @Test
    fun mobileDocumentPickerBridgeMapsWildcardAndExtendedFormatsToUtis() {
        val bridge = MobileDocumentPickerBridge()
        val request = FileBrowserRequest(
            mode = FileBrowserMode.OPEN_SINGLE_FILE,
            filters = listOf(
                FileTypeFilter(
                    description = "Extended Assets",
                    extensions = listOf("*.heic", "csv", "flac", "ttf", "*"),
                    mimeTypes = listOf("image/bmp", "text/html", "*/*")
                )
            )
        )
        val utis = bridge.resolveIosUniformTypeIdentifiers(request)
        assertTrue("public.heic" in utis)
        assertTrue("public.comma-separated-values-text" in utis)
        assertTrue("org.xiph.flac" in utis)
        assertTrue("public.truetype-ttf-font" in utis)
        assertTrue("com.microsoft.bmp" in utis)
        assertTrue("public.html" in utis)
        assertTrue("public.item" in utis)

        val folderUtis = bridge.resolveIosUniformTypeIdentifiers(
            FileBrowserRequest(mode = FileBrowserMode.SELECT_FOLDER)
        )
        assertTrue(folderUtis == listOf("public.folder"))
    }
}
