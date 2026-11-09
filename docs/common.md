# `:mobile:common` — Cross-Mobile Infrastructure (`commonMain`)

## Overview
`:mobile:common` provides the common mobile widget hierarchy (`MobileBaseNativeImpl`), `ComponentStyle` and `ComponentBootstrap` execution, strict user CSS class isolation (`customClasses`), semantic `BootstrapType` tinting, virtualized list/grid viewport engine, window/navbar state management, and system service abstractions used by `:mobile:android` and `:mobile:ios`. It depends on `:common` and `:systemCommon`.

---

## Key Components

- **Widget, Styling, & Container Base (`com.nexustools.genericui.mobile`)**:
  - `MobileBaseNativeImpl` (`BootstrapType.NONE` default unmodified native controls, `BootstrapType != NONE` native control tinting via `ThemeConfig`, strict `customClasses` isolation, typography styles honored on text elements, aesthetic `ComponentStyle` overrides silently ignored on standard controls/containers and honored on `StyledContainer` / `StyledButton`)
  - `MobileBaseContainerNativeImpl`, `MobileNativeWidget`
  - `MobileWindowNativeImpl`, `MobileApplicationNativeImpl` (`IApplication.addStyleSheet` / `removeStyleSheet`, `ThemeMode.AUTO` / `DARK` / `LIGHT`, `ThemeConfig`)
- **Virtualized Containers (`com.nexustools.genericui.mobile.container`)**:
  - `MobileLazyViewportEngine`
  - `MobileLazyListNativeImpl`, `MobileLazyGridNativeImpl`, `MobileTreeViewNativeImpl`
- **Dialogs, Timers, & System Services**:
  - `MobileFileBrowserHelper`, `MobileFileBrowserDialogNativeImpl`, `MobileStandardDialogsNativeImpl`
  - `MobileUiTimerNativeImpl`
  - `MobileNetworkMonitorNativeImpl`, `MobileNotificationManagerNativeImpl`, `MobilePermissionManagerNativeImpl`, `MobileUserDirectoryNativeImpl`
