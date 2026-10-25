# `:mobile:shared` — Shared Mobile Layout, Gesture, & Lifecycle Engine

> **Parent Submodule**: [`:mobile` (`../README.md`)](../README.md) | **Root Repository**: [**GenericUI (`../../README.md`)**](../../README.md)  
> **Root Docs**: [SourceSet Hierarchy](../../docs/sourcesets.md) · [Backends Guide](../../docs/backends.md) · [Extensions Guide](../../docs/extensions.md) · [Gradle Plugin](../../docs/gradle-plugin.md) · [UI Testing](../../docs/testing.md)  
> **Sibling Mobile Backends**: [`:mobile:android`](./android.md) · [`:mobile:ios`](./ios.md)

The **`:mobile:shared`** module (`com.nexustools.genericui.mobile.*`) is the shared Kotlin Multiplatform mobile foundation automatically included by **`:mobile:android`** and **`:mobile:ios`**. It backs the **`mobileCommon`** (`mobileCommonMain` / `mobileCommonTest`) sourceSet tier (`common` $\rightarrow$ `systemCommon` $\rightarrow$ `mobileCommon`).

---

## Core Architecture & Responsibilities

`:mobile:shared` unifies mobile UX, responsive posture adaptation, multi-touch physics, and memory-constrained viewport virtualization across Android and iOS:

1. **Form Factor, Foldable Posture, & Safe-Area Coordination (`com.nexustools.genericui.mobile.layout.*`)**:
   - **`MobileDeviceFormFactor`**: Classifies devices (`PHONE`, `FOLDABLE_UNFOLDED`, `TABLET`) from display width/height in `dp`.
   - **`MobileOrientationPosture`**: Detects `PORTRAIT`, `LANDSCAPE`, `TABLET_SPLIT`, `BOOK_HALF_OPEN` (vertical hinge), and `TABLETOP_HALF_OPEN` (horizontal hinge).
   - **`MobileSafeAreaTracker`**: Tracks `systemBarInsets` (status bar, navigation bar, iOS Home Indicator), `displayCutoutInsets` (camera punch-hole, notch, Dynamic Island), and `imeKeyboardInsets` (software keyboard height), computing `combinedSafeInsets` and notifying listeners in real time.
   - **`MobileAdaptiveFragmentCoordinator`**: Drives `AdaptiveSplitTabFragment` (used by **Episode Caster**) across 3 layout modes:
     - `PHONE_LANDSCAPE_FULLSCREEN` (immersive fullscreen media viewport in phone landscape)
     - `TABLET_DESKTOP_COLUMNS` (side-by-side multi-column split on tablets, unfolded foldables, and book posture)
     - `PORTRAIT_SINGLE_FLOW_TABS` (stacked primary viewport + tabbed secondary slots in portrait)
2. **Multi-Touch Gesture Recognition, Fling Physics, & Haptics (`com.nexustools.genericui.mobile.gesture.*`)**:
   - **`MobileGestureRecognizer`** & **`MobileGestureType`**: Recognizes `TAP`, `DOUBLE_TAP`, `LONG_PRESS`, `PAN`, `FLING`, `PINCH_ZOOM`, `TWO_FINGER_ROTATE`, and `EDGE_SWIPE_BACK` (iOS/Android interactive back-swipe from the screen edge).
   - **`MobileFlingPhysics`**: Computes natural spline fling deceleration distance (`predictFlingDistancePx`), duration (`predictFlingDurationMs`), and 1D critically-damped overscroll spring physics (`stepOverscrollSpring`).
   - **`MobileHapticFeedbackType`**: Standardized tactile feedback semantics (`LIGHT_IMPACT`, `MEDIUM_IMPACT`, `HEAVY_IMPACT`, `SELECTION_TICK`, `SUCCESS`, `WARNING`, `ERROR`).
3. **Lifecycle, Picture-in-Picture, Document Picker, & Lazy Viewport Pool (`com.nexustools.genericui.mobile.system.*`)**:
   - **`MobileLifecycleBridge`** & **`MobileLifecycleState`**: Synchronizes Android `Activity` and iOS `UIViewController`/`UIScene` lifecycle events (`INITIALIZED`, `CREATED`, `STARTED`, `RESUMED`, `PAUSED`, `STOPPED`, `DESTROYED`) and saved state (`FragmentBundle`) with GenericUI `Fragment`s.
   - **`MobilePipController`**: Validates and clamps Picture-in-Picture aspect ratios (`[100/239 .. 239/100]`) and coordinates PiP entry/exit notifications for media fragments.
   - **`MobileDocumentPickerBridge`**: Translates `FileBrowserRequest` and `FileTypeFilter` rules into Android SAF MIME types (`resolveAndroidMimeTypes`) and iOS Uniform Type Identifiers (`resolveIosUniformTypeIdentifiers`, including `"public.folder"` for `FileBrowserMode.SELECT_FOLDER`), and manages pending request callbacks.
   - **`MobileLazyViewportPool`**: Memory-pressure-aware lazy viewport window and scrap-pool recycler for `LazyList`, `LazyGrid`, and `TreeView`, including `onLowMemoryTrim(trimLevel)` to flush scrap views when the OS signals memory pressure.

---

## Key Classes Reference

| Package | Class / Enum | Responsibility |
| :--- | :--- | :--- |
| `com.nexustools.genericui.mobile.layout` | `MobileDeviceFormFactor` | Enum (`PHONE`, `FOLDABLE_UNFOLDED`, `TABLET`) with `fromWidthDp(widthDp, heightDp)` and `isLargeScreen`. |
| `com.nexustools.genericui.mobile.layout` | `MobileOrientationPosture` | Enum (`PORTRAIT`, `LANDSCAPE`, `TABLET_SPLIT`, `BOOK_HALF_OPEN`, `TABLETOP_HALF_OPEN`). |
| `com.nexustools.genericui.mobile.layout` | `MobileSafeAreaTracker` | Aggregates `systemBarInsets`, `displayCutoutInsets`, and `imeKeyboardInsets` into `combinedSafeInsets` (`EdgeInsets`). |
| `com.nexustools.genericui.mobile.layout` | `MobileAdaptiveFragmentCoordinator` | Evaluates `WindowConfiguration` and resolves `AdaptiveSplitTabFragment.LayoutMode`. |
| `com.nexustools.genericui.mobile.gesture` | `MobileGestureType` | Enum (`TAP`, `DOUBLE_TAP`, `LONG_PRESS`, `PAN`, `FLING`, `PINCH_ZOOM`, `TWO_FINGER_ROTATE`, `EDGE_SWIPE_BACK`). |
| `com.nexustools.genericui.mobile.gesture` | `MobileGestureRecognizer` | Multi-pointer touch tracker calculating velocity, pinch scale factor, two-finger rotation delta, and edge-swipe back gestures. |
| `com.nexustools.genericui.mobile.gesture` | `MobileFlingPhysics` | Spline fling distance/duration predictor and critically-damped overscroll spring solver. |
| `com.nexustools.genericui.mobile.gesture` | `MobileHapticFeedbackType` | Cross-mobile haptic feedback enum mapped to `HapticFeedbackConstants` on Android and `UIFeedbackGenerator` on iOS. |
| `com.nexustools.genericui.mobile.system` | `MobileLifecycleBridge` | Drives GenericUI `Fragment` lifecycle transitions (`attachFragment`, `dispatchStart`, `dispatchResume`, `dispatchPause`, `dispatchStop`, `dispatchSaveInstanceState`, `dispatchDestroy`). |
| `com.nexustools.genericui.mobile.system` | `MobilePipController` | Clamps PiP aspect ratios (`clampAspectRatio`) and dispatches PiP mode changes to the active `Fragment`. |
| `com.nexustools.genericui.mobile.system` | `MobileDocumentPickerBridge` | Maps `FileBrowserRequest` filters to Android MIME types and iOS `UTType` identifiers and routes picker completions. |
| `com.nexustools.genericui.mobile.system` | `MobileLazyViewportPool` | Tracks `[firstVisibleIndex .. lastVisibleIndex]`, mounts/unmounts `NativeImpl` items, recycles scrap views, and handles `onLowMemoryTrim`. |

---

## Code Examples

### 1. Adaptive Layout & Safe-Area Coordination (`MobileAdaptiveFragmentCoordinator` & `MobileSafeAreaTracker`)

```kotlin
import com.nexustools.genericui.core.fragment.AdaptiveSplitTabFragment
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.mobile.layout.MobileAdaptiveFragmentCoordinator

fun updateMobileViewportMetrics(
    coordinator: MobileAdaptiveFragmentCoordinator,
    fragment: AdaptiveSplitTabFragment
) {
    // 1. Update system bar, display cutout (Dynamic Island / camera hole), and IME keyboard insets:
    coordinator.safeAreaTracker.updateAllInsets(
        systemBars = EdgeInsets(top = 48f, right = 0f, bottom = 34f, left = 0f),
        displayCutout = EdgeInsets(top = 54f, right = 0f, bottom = 0f, left = 0f),
        imeKeyboard = EdgeInsets.ZERO
    )
    println("Combined Safe Area Insets: ${coordinator.safeAreaTracker.combinedSafeInsets}")

    // 2. Evaluate window configuration (e.g., phone rotated to landscape 2400x1080 @ 3.0x density):
    val windowConfig = coordinator.evaluateConfiguration(
        widthPx = 2400,
        heightPx = 1080,
        densityScale = 3.0f,
        themeMode = ThemeMode.DARK
    )

    // 3. Resolve AdaptiveSplitTabFragment layout mode (returns PHONE_LANDSCAPE_FULLSCREEN if enabled):
    val layoutMode = coordinator.resolveLayoutMode(fragment, windowConfig)
    println("Form factor: ${coordinator.formFactor}, Posture: ${coordinator.posture}, Mode: $layoutMode")
}
```

### 2. Multi-Touch Pinch-Zoom, Rotation, & Fling Physics (`MobileGestureRecognizer` & `MobileFlingPhysics`)

```kotlin
import com.nexustools.genericui.mobile.gesture.MobileFlingPhysics
import com.nexustools.genericui.mobile.gesture.MobileGestureRecognizer
import com.nexustools.genericui.mobile.gesture.MobileGestureType

val gestureRecognizer = MobileGestureRecognizer(
    touchSlopPx = 8f,
    edgeSwipeThresholdPx = 24f
)
val flingPhysics = MobileFlingPhysics()

fun setupCanvasTouchHandling() {
    gestureRecognizer.onGestureDetected = { type, focalPoint, dx, dy, scaleFactor, rotationDeltaRad ->
        when (type) {
            MobileGestureType.PINCH_ZOOM -> {
                println("Pinch zoom at $focalPoint: scale=${scaleFactor}x")
            }
            MobileGestureType.TWO_FINGER_ROTATE -> {
                println("Two-finger rotate at $focalPoint: deltaRad=$rotationDeltaRad")
            }
            MobileGestureType.FLING -> {
                val flingDistX = flingPhysics.predictFlingDistancePx(dx, densityDpi = 420f)
                val flingDurationMs = flingPhysics.predictFlingDurationMs(dx, densityDpi = 420f)
                println("Fling velocity=${dx}px/s -> distance=${flingDistX}px over ${flingDurationMs}ms")
            }
            MobileGestureType.EDGE_SWIPE_BACK -> {
                println("Interactive back-swipe triggered from left screen edge")
            }
            else -> {}
        }
    }
}
```

### 3. Memory-Pressure-Aware Viewport Virtualization (`MobileLazyViewportPool`)

```kotlin
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool

val viewportPool = MobileLazyViewportPool(
    overscanItems = 2,
    maxRecycledScrapCount = 16
)

fun onScrollChanged(scrollOffsetPx: Float, viewportHeightPx: Float) {
    val visibleRange = viewportPool.computeVisibleRange(
        scrollOffsetPx = scrollOffsetPx,
        viewportSizePx = viewportHeightPx,
        itemExtentPx = 72f,
        totalItemCount = 10_000,
        columns = 1
    )
    println("Visible items: $visibleRange (mounted=${viewportPool.mountedCount}, scrap=${viewportPool.scrapCount})")
}

// When Android onTrimMemory(TRIM_MEMORY_RUNNING_CRITICAL) or iOS didReceiveMemoryWarning fires:
fun onSystemMemoryWarning() {
    viewportPool.onLowMemoryTrim(trimLevel = 80)
}
```

---

## Related Documentation

- **Parent Mobile Overview**: [`../README.md`](../README.md)
- **Mobile Platform Backends**:
  - [Android Backend (`:mobile:android`)](./android.md)
  - [iOS Backend (`:mobile:ios`)](./ios.md)
- **Root GenericUI Documentation**:
  - [Root README (`../../README.md`)](../../README.md)
  - [SourceSet Hierarchy (`../../docs/sourcesets.md`)](../../docs/sourcesets.md)
  - [Backends Guide (`../../docs/backends.md`)](../../docs/backends.md)
  - [Extensions Guide (`../../docs/extensions.md`)](../../docs/extensions.md)
  - [Gradle Plugin (`../../docs/gradle-plugin.md`)](../../docs/gradle-plugin.md)
  - [UI Testing (`../../docs/testing.md`)](../../docs/testing.md)
- **Sibling Submodules**: [`:desktop`](../../desktop/README.md) · [`:web`](../../web/README.md) · [`:tests`](../../tests/README.md) · [`:NJS`](../../lib/NJS/README.md)
