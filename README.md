# `:mobile` — Consolidated Mobile Backends (`Android`, `IOS`, `IPadOS`)

> **Root Repository**: [**GenericUI (`../README.md`)**](../README.md) | **Related Guides**: [Platform Backends (`../docs/backends.md`)](../docs/backends.md) · [iPadOS Backend (`../docs/ipados.md`)](../docs/ipados.md) · [`:apple` (`../docs/apple.md`)](../docs/apple.md) · [`:jvm` (`../docs/jvm.md`)](../docs/jvm.md) · [`:system` (`../system/README.md`)](../system/README.md)

## Overview
The consolidated **`:mobile`** module (`mobile/build.gradle.kts`) provides all three mobile backends for GenericUI—**Android (`Android`)**, **iPhone iOS (`IOS`)**, and **iPad iPadOS (`IPadOS`)**—built atop `:common`, `:system`, [`:jvm`](../docs/jvm.md) (`androidMain`), and [`:apple`](../docs/apple.md) (`iosMain`).

---

## SourceSet Hierarchy inside `:mobile`

```text
:common
  └── :system
        └── :mobile (commonMain / mobileCommon)      — Form factor, gesture, fling, PiP, & lazy viewport pool
              ├── androidMain  (Android + :jvm)     — Android Views, Material, Hardware RenderEffect blur, FGS ServiceWorker
              └── iosMain      (IOS & IPadOS + :apple) — UIKit single-window iPhone (`ios/*`) & multi-window iPadOS (`ipados/*`)
```

| Source Set | Backend Targets | Key Responsibilities | Documentation |
|-----------|-----------------|----------------------|---------------|
| **`commonMain`** (`mobileCommon`) | `android`, `iosArm64`, `iosSimulatorArm64` | Responsive breakpoints (`MobileDeviceFormFactor`, `MobileAdaptiveFragmentCoordinator`, `MobileLayoutFactory`), gesture/fling physics, lifecycle bridge (`MobileLifecycleBridge`), safe-area/posture tracking, and `MobileLazyViewportPool` | [`docs/common.md`](docs/common.md) · [`docs/shared.md`](docs/shared.md) |
| **`androidMain`** | **`Android`** | `AppCompatActivity` per `@MainAppFragment`/`@AppFragment`, `AndroidExtensionRuntimeBridge`, `AndroidHardwareBackdropBlurController`, `GeneratedAndroidServiceWorkerService` / `AndroidServiceWorkerController`, 16KB ELF/ZIP `.so` alignment, `AndroidLazyListImpl`, `AndroidUdpSocketNativeImpl` (`MulticastLock`), `AndroidBaseContainerNativeImpl`, `AndroidViewHandle`, shared `:jvm` stack | [`docs/android.md`](docs/android.md) |
| **`iosMain` (`ios/*` & `ipados/*`)** | **`IOS`** (`UIDeviceFamily = 1`) & **`IPadOS`** (`UIDeviceFamily = 2`) | UIKit controls (`IosBaseNativeImpl`), `UikitBackdropBlurController` (Metal `CABackdropLayer` + `UIVisualEffectView`), single-window `IosLauncher` vs. multi-window Stage Manager `IpadosLauncher`/`IpadosSceneDelegate`/`IpadosWindowImpl`/`UIMenuSystem`, shared `:apple` stack, and `ServiceWorkerExtension.isSupported == false` (zero simulation) | [`docs/ios.md`](docs/ios.md) · [`../docs/ipados.md`](../docs/ipados.md) |

---

## 60+Hz Hardware `backdrop-filter` (`StyledButton` & `StyledContainer`, Excluding `TextBubble`)

- **Android (`AndroidHardwareBackdropBlurController`)**: Hosted by `AndroidFlexContainerView` (`AndroidStyledContainerImpl`) and `AndroidStyledButtonView` (`AndroidStyledButtonImpl`). Walks up to the nearest backdrop root (`android.R.id.content` or outermost host above `DecorView`), draws strictly pre-order preceding branches in `layerZIndex` order, captures live `TextureView` frames via `SurfaceTextureListener` (`onSurfaceTextureUpdated` -> `postInvalidateOnAnimation()`), and applies **API 31+ hardware `RenderNode` / `RenderEffect.createBlurEffect`** + `ColorMatrixColorFilter` at 60–120Hz (falling back cleanly on API 24–30 and Robolectric to downsampled `Bitmap` + `FastBackdropBlurKernel`).
- **iOS & iPadOS (`UikitBackdropBlurController`)**: Shared across `IosStyledContainerImpl` and `IosStyledButtonImpl` on both `IOS` and `IPadOS`. Mounts a Metal-backed `UIVisualEffectView` (`CABackdropLayer`) driven by a paused `UIViewPropertyAnimator` `fractionComplete` radius curve plus `tintOverlayView` while keeping `hostView.backgroundColor = UIColor.clearColor`.
- [`TextBubble`](../extensions/docs/aichat.md) explicitly strips `backdropFilter` via `isTextBubbleComponent()` -> `withoutBackdropFilter()`.

---

## Mobile `ServiceWorker` Policy & Lifecycle Bridges

- **Android (`BackendTarget.Android`)**: Fully supported via [`AndroidExtensionRuntimeBridge`](src/androidMain/kotlin/com/nexustools/genericui/android/AndroidExtensionRuntimeBridge.kt), `GeneratedAndroidServiceWorkerService`, `AndroidServiceWorkerController` (`ServiceCompat.startForeground` ongoing notification with default `"Stop"` + `updateNotificationText` + `extendNotificationActions`/`replaceNotificationActions`), `AndroidServiceWorkerBinder` IPC, and conditional `BOOT_COMPLETED` receiver when `autostart = true`.
- **iOS & iPadOS (`BackendTarget.IOS`, `BackendTarget.IPadOS`)**: `AppleMobileServiceWorkerRuntime` enforces **`ServiceWorkerExtension.isSupported = false`** and `isAutostartSupported = false` with **zero background worker simulation** and **zero `@MainServiceWorker` instantiation**.

---

## Native Mobile Control Rendering (`BootstrapType.NONE`), Tinting (`BootstrapType != NONE`), & Style Restraints

1. **`BootstrapType.NONE` Default — 100% Unmodified Native Controls**: Material/AppCompat views (`MaterialButton`, `AppCompatEditText`, `SwitchMaterial`) on Android and genuine `UIControl`s (`UIButton`, `UITextField`, `UISwitch`, `UISlider`) on UIKit match the active `LIGHT` / `DARK` OS appearance (`ThemeMode.AUTO`).
2. **`BootstrapType != NONE`**: Android tints control drawables via `ViewCompat.setBackgroundTintList` / `AndroidControlTintRenderUtil`; UIKit tints controls via `UIButton.Configuration` / `IosControlTintRenderUtil`, overlaying contrasting labels.
3. **Typography vs. Aesthetic Style Restraints**: Text elements honor typography and text color; standard controls/containers ignore aesthetic CSS skinning (tint via `BootstrapType` only); `StyledContainer` / `StyledButton` / [`:extensions:ui:alien`](../extensions/docs/alien.md) honor full `ComponentStyle`.
4. **Strict User CSS Class Isolation & Optional Navbar Chrome**: `styleSnapshot.customClasses` never mixes with `ComponentBootstrap` enums. `TitleCapabilities` sets `isTitleBarRequired = false`, `titleBarRendersAsNavbar = true`, `menuBarRendersAsNavbar = true`, and `menuBarMergesIntoTitleNavbar = true` (omitting any top bar when neither `TitleBar` nor `MenuBar` is set).

---

## Bare-Metal Native Async `IFileSystem`, `Database`, & `Network`

| Backend | `FileSystem` & `UserDirectory` | Native `Database` (`DatabaseQuerySqlTranslator`) | Async `Network`, `HttpClient`, `WebSocket`, & `IApplication.isOffline` |
| :--- | :--- | :--- | :--- |
| [**`Android`** (`androidMain`)](docs/android.md) | `:jvm` `FileChannel` + SAF `ContentResolver` (`ACTION_OPEN_DOCUMENT*`); `AndroidUserDirectoryNativeImpl` (`filesDir`/`cacheDir`) | **`androidx.sqlite:sqlite-ktx`** (`AndroidDatabaseNativeImpl` + `:jvm` `JvmDatabaseBase`) | `:jvm` TCP (`Socket`/`SSLSocket`), `AndroidUdpSocketNativeImpl` (`WifiManager.MulticastLock` around `JvmUdpSocketNativeImpl`), `JvmHttpUrlConnectionClient`, `JvmRfc6455WebSocketNativeImpl`, `ConnectivityManager` (`NetworkCallback`) |
| [**`IOS` & `IPadOS`** (`iosMain`)](docs/ios.md) | `:apple` `NSFileManager` / `dispatch_io_t` + `UIDocumentPickerViewController`; `AppleUserDirectoryNativeImpl` (`Library/Application Support` & `Caches`) | **`:apple` `libsqlite3`** (`AppleDatabaseNativeImpl`) | `:apple` `Network.framework` (`NWConnection` TCP/TLS & BSD `SOCK_DGRAM`), `NSURLSession`, `NSURLSessionWebSocketTask`, `NWPathMonitor` |
