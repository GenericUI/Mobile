package com.nexustools.genericui.mobile.layout

import com.nexustools.genericui.component.layout.AdaptiveCardLayout
import com.nexustools.genericui.component.layout.AdaptiveCardLayoutImpl
import com.nexustools.genericui.component.layout.AdaptiveNavigationScaffoldLayout
import com.nexustools.genericui.component.layout.AdaptiveNavigationScaffoldLayoutImpl
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayout
import com.nexustools.genericui.component.layout.CollapsibleBottomDockLayoutImpl
import com.nexustools.genericui.component.layout.CollapsiblePanelLayout
import com.nexustools.genericui.component.layout.CollapsiblePanelLayoutImpl
import com.nexustools.genericui.component.layout.ColumnLayout
import com.nexustools.genericui.component.layout.ColumnLayoutImpl
import com.nexustools.genericui.component.layout.DefaultAdaptiveNavigationScaffoldLayoutImpl
import com.nexustools.genericui.component.layout.DefaultCollapsiblePanelLayoutImpl
import com.nexustools.genericui.component.layout.DefaultResponsiveBentoGridLayoutImpl
import com.nexustools.genericui.component.layout.DefaultResponsiveMasterDetailLayoutImpl
import com.nexustools.genericui.component.layout.DefaultSplitPaneLayoutImpl
import com.nexustools.genericui.component.layout.HybridColumnLayout
import com.nexustools.genericui.component.layout.HybridColumnLayoutImpl
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayout
import com.nexustools.genericui.component.layout.HybridSplitNavigationLayoutImpl
import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.component.layout.ResponsiveAdaptiveCardLayoutImpl
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayout
import com.nexustools.genericui.component.layout.ResponsiveBentoGridLayoutImpl
import com.nexustools.genericui.component.layout.ResponsiveCollapsibleBottomDockLayoutImpl
import com.nexustools.genericui.component.layout.ResponsiveColumnLayoutImpl
import com.nexustools.genericui.component.layout.ResponsiveHybridColumnLayoutImpl
import com.nexustools.genericui.component.layout.ResponsiveHybridSplitNavigationLayoutImpl
import com.nexustools.genericui.component.layout.ResponsiveMasterDetailLayout
import com.nexustools.genericui.component.layout.ResponsiveMasterDetailLayoutImpl
import com.nexustools.genericui.component.layout.SplitPaneLayout
import com.nexustools.genericui.component.layout.SplitPaneLayoutImpl

/**
 * Mobile (`:mobile:android` and `:mobile:ios`) [LayoutFactory] implementation supporting full dynamic
 * responsive and hybrid layout switching across phone, foldable, and tablet postures.
 */
object MobileLayoutFactory : LayoutFactory {
    override fun createColumnLayout(layout: ColumnLayout): ColumnLayoutImpl =
        ResponsiveColumnLayoutImpl(layout)

    override fun createAdaptiveCardLayout(layout: AdaptiveCardLayout): AdaptiveCardLayoutImpl =
        ResponsiveAdaptiveCardLayoutImpl(layout)

    override fun createHybridColumnLayout(layout: HybridColumnLayout): HybridColumnLayoutImpl =
        ResponsiveHybridColumnLayoutImpl(layout)

    override fun createHybridSplitNavigationLayout(layout: HybridSplitNavigationLayout): HybridSplitNavigationLayoutImpl =
        ResponsiveHybridSplitNavigationLayoutImpl(layout)

    override fun createCollapsibleBottomDockLayout(layout: CollapsibleBottomDockLayout): CollapsibleBottomDockLayoutImpl =
        ResponsiveCollapsibleBottomDockLayoutImpl(layout)

    override fun createResponsiveMasterDetailLayout(layout: ResponsiveMasterDetailLayout): ResponsiveMasterDetailLayoutImpl =
        DefaultResponsiveMasterDetailLayoutImpl(layout)

    override fun createResponsiveBentoGridLayout(layout: ResponsiveBentoGridLayout): ResponsiveBentoGridLayoutImpl =
        DefaultResponsiveBentoGridLayoutImpl(layout)

    override fun createAdaptiveNavigationScaffoldLayout(layout: AdaptiveNavigationScaffoldLayout): AdaptiveNavigationScaffoldLayoutImpl =
        DefaultAdaptiveNavigationScaffoldLayoutImpl(layout)

    override fun createSplitPaneLayout(layout: SplitPaneLayout): SplitPaneLayoutImpl =
        DefaultSplitPaneLayoutImpl(layout)

    override fun createCollapsiblePanelLayout(layout: CollapsiblePanelLayout): CollapsiblePanelLayoutImpl =
        DefaultCollapsiblePanelLayoutImpl(layout)
}
