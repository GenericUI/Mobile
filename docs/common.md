# `:mobile:common` — Cross-Mobile Infrastructure (`commonMain`)

## Overview
`:mobile:common` provides the common mobile widget hierarchy (`MobileBaseNativeImpl`), `ComponentStyle` and `ComponentBootstrap` execution, strict user CSS class isolation (`customClasses`), semantic `BootstrapType` tinting, virtualized list/grid viewport engine, window/navbar state management, and system service abstractions used by `:mobile:android` and `:mobile:ios`. It depends on `:common` and `:systemCommon`.

---

## Key Components

- **Widget, Styling, & Container Base (`com.nexustools.genericui.mobile`)**:
  - `MobileBaseNativeImpl` (`BootstrapType.NONE` default, semantic `BootstrapType` tinting, strict `customClasses` isolation, `ComponentStyle` & `accentColor`, silent ignore of unsupported CSS properties)
  - `MobileBaseContainerNativeImpl`, `MobileNativeWidget`
  - `MobileWindowNativeImpl`, `MobileApplicationNativeImpl` (`IApplication.addStyleSheet` / `removeStyleSheet`, `ThemeMode.AUTO` / `DARK` / `LIGHT`)
- **Virtualized Containers (`com.nexustools.genericui.mobile.container`)**:
  - `MobileLazyViewportEngine`
  - `MobileLazyListNativeImpl`, `MobileLazyGridNativeImpl`, `MobileTreeViewNativeImpl`
- **Dialogs, Timers, & System Services**:
  - `MobileFileBrowserHelper`, `MobileFileBrowserDialogNativeImpl`, `MobileStandardDialogsNativeImpl`
  - `MobileUiTimerNativeImpl`
  - `MobileNetworkMonitorNativeImpl`, `MobileNotificationManagerNativeImpl`, `MobilePermissionManagerNativeImpl`, `MobileUserDirectoryNativeImpl`
