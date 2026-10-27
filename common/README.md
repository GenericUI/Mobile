# `:mobile:common` — Cross-Mobile Native Widget & Viewport Foundation

## Overview
`:mobile:common` is the cross-mobile KMP module (`commonMain`) used by both `:mobile:android` and `:mobile:ios`. It depends on `:common` and `:systemCommon`.

---

## Styling, `BootstrapType.NONE`, & User Class Isolation in `MobileBaseNativeImpl`

- **`BootstrapType.NONE` Default & Semantic `BootstrapType` Tinting**: When `styleSnapshot.type == BootstrapType.NONE` (the default) and no inline `ComponentStyle` background/border is set, mobile controls render as untinted native Android/iOS system views. When a semantic `BootstrapType` (`DANGER`, `PRIMARY`, `SUCCESS`, `WARNING`, `INFO`, `SECONDARY`, `LIGHT`, `DARK`, `LINK`) is set, `MobileBaseNativeImpl` resolves the semantic color (`BootstrapColorPalette.resolveColor(type, darkTheme)`) for tinting the native view (supporting `outline = true/false`).
- **Strict User CSS Class Isolation**: `addClass`, `removeClass`, `hasClass`, `getClasses()`, and `getClass()` return **only** user-added custom classes (`styleSnapshot.customClasses`). `ComponentBootstrap` (`getBootstrap()`) enum utilities are evaluated directly by the mobile layout and rendering engines.
- **`ComponentStyle` & Silent Ignore**: `applyComponentStyle(style: ComponentStyle)` applies supported properties (`backgroundColor`, `color`, `accentColor`, `borderColor`, `borderRadius`, `opacity`, `padding`, `margin`, `visibility`, `display`) and silently ignores unsupported CSS properties and transitions.

---

## Core Classes

| Class | Package | Description |
|-------|---------|-------------|
| `MobileBaseNativeImpl` | `com.nexustools.genericui.mobile` | Base mobile widget implementation handling `ComponentStyle`, `ComponentBootstrap`, strict `customClasses` isolation, semantic `BootstrapType` tinting, layout bounds, visibility, touch/focus state, and single-placement `mountedInTitleBar` tracking |
| `MobileBaseContainerNativeImpl` | `com.nexustools.genericui.mobile` | Base mobile container managing child widget lists and layout passes |
| `MobileNativeWidget` | `com.nexustools.genericui.mobile` | Common interface for mobile widget implementations |
| `MobileWindowNativeImpl` | `com.nexustools.genericui.mobile` | Base mobile window implementation reporting mobile `TitleCapabilities` (`isTitleBarRequired = false`, `titleBarRendersAsNavbar = true`, `menuBarRendersAsNavbar = true`, `menuBarMergesIntoTitleNavbar = true`) |
| `MobileApplicationNativeImpl` | `com.nexustools.genericui.mobile` | Base mobile application lifecycle, `PrecompiledStyleSheet` registry (`addStyleSheet` / `removeStyleSheet`), and `ThemeMode` (`AUTO`, `DARK`, `LIGHT`) manager |
| `MobileLazyViewportEngine` | `com.nexustools.genericui.mobile` | Touch-scroll virtualized viewport range calculation and item recycling engine |
| `MobileLazyListNativeImpl` | `com.nexustools.genericui.mobile.container` | Virtualized `LazyList` implementation for mobile targets |
| `MobileLazyGridNativeImpl` | `com.nexustools.genericui.mobile.container` | Virtualized `LazyGrid` implementation for mobile targets |
| `MobileTreeViewNativeImpl` | `com.nexustools.genericui.mobile.container` | Expandable hierarchical `TreeView` implementation for mobile targets |
| `MobileFileBrowserHelper` | `com.nexustools.genericui.mobile` | Mobile document picker filter and URI helper utilities |
| `MobileFileBrowserDialogNativeImpl` | `com.nexustools.genericui.mobile.dialog` | Base mobile file/document picker dialog implementation |
| `MobileStandardDialogsNativeImpl` | `com.nexustools.genericui.mobile.dialog` | Base mobile alert, confirmation, and prompt dialog implementation |
| `MobileUiTimerNativeImpl` | `com.nexustools.genericui.mobile.timer` | Repeating and one-shot UI timer scheduling for mobile main threads |
| `MobileNetworkMonitorNativeImpl` | `com.nexustools.genericui.mobile.system` | Mobile network connectivity and metered state monitor |
| `MobileNotificationManagerNativeImpl` | `com.nexustools.genericui.mobile.system` | Mobile local notification dispatcher |
| `MobilePermissionManagerNativeImpl` | `com.nexustools.genericui.mobile.system` | Runtime permission request and status manager |
| `MobileUserDirectoryNativeImpl` | `com.nexustools.genericui.mobile.system` | Sandboxed mobile application documents, cache, and temp directory resolver |
