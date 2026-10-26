# `:mobile:common` — Cross-Mobile Infrastructure (`commonMain`)

## Overview
`:mobile:common` provides the common mobile widget hierarchy, virtualized list/grid viewport engine, window/navbar state management, and system service abstractions used by `:mobile:android` and `:mobile:ios`. It depends on `:common` and `:systemCommon`.

---

## Key Components

- **Widget & Container Base (`com.nexustools.genericui.mobile`)**:
  - `MobileBaseNativeImpl`, `MobileBaseContainerNativeImpl`, `MobileNativeWidget`
  - `MobileWindowNativeImpl`, `MobileApplicationNativeImpl`
- **Virtualized Containers (`com.nexustools.genericui.mobile.container`)**:
  - `MobileLazyViewportEngine`
  - `MobileLazyListNativeImpl`, `MobileLazyGridNativeImpl`, `MobileTreeViewNativeImpl`
- **Dialogs, Timers, & System Services**:
  - `MobileFileBrowserHelper`, `MobileFileBrowserDialogNativeImpl`, `MobileStandardDialogsNativeImpl`
  - `MobileUiTimerNativeImpl`
  - `MobileNetworkMonitorNativeImpl`, `MobileNotificationManagerNativeImpl`, `MobilePermissionManagerNativeImpl`, `MobileUserDirectoryNativeImpl`
