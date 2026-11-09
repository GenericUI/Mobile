# `:mobile:common` — Cross-Mobile Native Widget & Viewport Foundation

## Overview
`:mobile:common` is the cross-mobile KMP module (`commonMain`) used by both `:mobile:android` and `:mobile:ios`. It depends on `:common` and `:systemCommon`.

---

## Styling, `BootstrapType.NONE`, System Backend Style Restraints, & User Class Isolation in `MobileBaseNativeImpl`

- **`BootstrapType.NONE` Default & Native Control Tinting (`BootstrapType != NONE`)**: When `styleSnapshot.type == BootstrapType.NONE` (the default), mobile controls render as 100% unmodified native Android/iOS system views. When `BootstrapType != BootstrapType.NONE` (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`), `MobileBaseNativeImpl` resolves the semantic color (`ThemeConfig` / `BootstrapColorPalette.resolveColor(type, darkTheme)`) for tinting the native view while preserving native borders/elevation and rendering contrasting text (`outline = true/false`).
- **System Backend Typography vs. Aesthetic Style Restraints**: Typography styles (`fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `color`, `lineHeight`, `letterSpacing`, `textAlign`, etc.) are honored on text elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`). Aesthetic skinning and font overrides on `ComponentStyle` are **silently ignored** on standard controls/containers (which can only be tinted via `BootstrapType`), while `StyledContainer` and `StyledButton` honor full `ComponentStyle` aesthetics.
- **Strict User CSS Class Isolation**: `addClass`, `removeClass`, `hasClass`, `getClasses()`, and `getClass()` return **only** user-added custom classes (`styleSnapshot.customClasses`). `ComponentBootstrap` (`getBootstrap()`) enum utilities are evaluated directly by the mobile layout and rendering engines.

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
