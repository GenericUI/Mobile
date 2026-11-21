# `:mobile` Common Layer (`commonMain` / `mobileCommon`)

> **Module Guide**: [**`:mobile` (`../README.md`)**](../README.md) | **Related**: [`shared.md`](shared.md) · [`android.md`](android.md) · [`ios.md`](ios.md) · [`../../docs/ipados.md`](../../docs/ipados.md)

## Overview
The `commonMain` source set of `:mobile` (`mobileCommon` variant scope) hosts pure-Kotlin mobile posture, adaptive breakpoint, gesture/fling physics, lifecycle, document-picker bridge, and lazy viewport pooling helpers shared across **`Android`**, **`IOS`**, and **`IPadOS`**, sitting directly above `:common` and `:system`.

---

## Key Classes in `mobile/src/commonMain`

| Domain | Classes | Package | Responsibility |
|-------|---------|---------|----------------|
| **Adaptive Layout & Posture** | `MobileDeviceFormFactor`<br/>`MobileAdaptiveFragmentCoordinator`<br/>`MobileLayoutFactory`<br/>`MobileOrientationPosture`<br/>`MobileSafeAreaTracker` | `com.nexustools.genericui.mobile.layout` | Distinguishes phone vs. tablet smallest-width breakpoints (`600dp`), Split View / Slide Over compact widths, foldable posture/hinge regions, orientation, and safe-area edge insets across Android, iOS, and iPadOS |
| **Touch Gestures, Fling, & Haptics** | `MobileGestureRecognizer`<br/>`MobileGestureType`<br/>`MobileFlingPhysics`<br/>`MobileHapticFeedbackType` | `com.nexustools.genericui.mobile.gesture` | Recognizes tap, long-press, pan, swipe, and pinch gestures; computes deceleration fling curves; exposes cross-mobile haptic feedback descriptors |
| **Lifecycle, Pickers, & Viewport Pool** | `MobileLifecycleBridge`<br/>`MobileLifecycleState`<br/>`MobileDocumentPickerBridge`<br/>`MobileLazyViewportPool` | `com.nexustools.genericui.mobile.system` | Bridges foreground/background mobile lifecycle states, document picker specifications, and reusable item view slot pooling (`MobileLazyViewportPool`) backed by `AndroidLazyListImpl` and `IosLazyListImpl` |
