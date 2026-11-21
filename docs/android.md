# Android Native Backend (`:mobile` `androidMain` / `BackendTarget.Android`)

> **Module Guide**: [**`:mobile` (`../README.md`)**](../README.md) | **Related**: [`../../docs/jvm.md`](../../docs/jvm.md) · [`common.md`](common.md) · [`ios.md`](ios.md)

## Overview
The **Android** backend resides in `androidMain` of `:mobile` atop `:common`, `:system`, `:mobile` `commonMain`, and [`:jvm`](../../docs/jvm.md), built on Android SDK `View` / `ViewGroup` classes (`AndroidBaseContainerNativeImpl`, `AndroidViewHandle`), AndroidX `AppCompatActivity`, and Material Components (`MaterialButton`, `MaterialToolbar`, `TabLayout`, `Slider`, `SwitchMaterial`).

---

## 60+Hz Hardware `backdrop-filter` (`AndroidHardwareBackdropBlurController` + `TextureView`)

- Implemented in [`AndroidHardwareBackdropBlurController`](../src/androidMain/kotlin/com/nexustools/genericui/android/AndroidHardwareBackdropBlurController.kt) and attached to `AndroidFlexContainerView` (`AndroidStyledContainerImpl`) and `AndroidStyledButtonView` (`AndroidStyledButtonImpl`), while `TextBubble` strips `backdropFilter` via `withoutBackdropFilter()`.
- **Root Walk & Strict Pre-Order Sampling**: Walks up to the nearest backdrop root (`android.R.id.content` or outermost root view above `DecorView`) and paints strictly pre-order preceding branches in `layerZIndex` order, avoiding feedback self-blur.
- **API 31+ Hardware Path**: Renders captured behind-the-surface content into an offscreen `android.graphics.RenderNode`, attaches `RenderEffect.createBlurEffect(...)` (plus optional `ColorMatrixColorFilter` saturation/brightness/contrast), and composites at 60–120Hz via GPU hardware acceleration.
- **Live `TextureView` Video Observing**: Observes underlying `TextureView` surfaces with `SurfaceTextureListener` (`SurfaceUpdateRelayListener`), copying `TextureView.getBitmap(downsampledBitmap)` and triggering `postInvalidateOnAnimation()` on `onSurfaceTextureUpdated` so glass HUD overlays blur live video continuously.
- **API 24–30 & Robolectric Fallback**: Falls back transparently to downsampled cached `Bitmap` rendering + zero-allocation `FastBackdropBlurKernel.applyBackdropToArgbRegion`.

---

## Runtime View, Virtualized List, Multicast UDP, & Lifecycle Bridges

- **`AndroidViewHandle` & `AndroidBaseContainerNativeImpl`**: Manages native Android `View` attachment, child ordering, layout/measure invalidation, focus/visibility flags, `layerZIndex` sorting, and `ComponentBootstrap` layout parameters inside `AndroidFlexContainerView`.
- **`AndroidLazyListImpl`**: Pairs Android vertical/horizontal scroll containers (`ScrollView` / `HorizontalScrollView`) with `:mobile`'s `MobileLazyViewportPool` to virtualize and recycle large list viewports.
- **`AndroidUdpSocketNativeImpl`**: Wraps `:jvm`'s `JvmUdpSocketNativeImpl` with a reference-counted Android `WifiManager.MulticastLock` tagged `"genericui-udp-multicast"` (automatically acquired on multicast join/broadcast and released on close), enabling reliable device discovery protocols (e.g., SSDP / mDNS / Cast) on physical Android hardware.
- **`AndroidExtensionRuntimeBridge`**: Centralizes `Application.onCreate`, `Activity` create/start/resume/pause/stop/destroy/activityResult/permissions callbacks (`GenericUiAndroidApplication` -> `GenericUiFragmentActivity`), notification channel registration, and foreground service promotion (`promoteToForegroundService`).

---

## Android `ServiceWorker` Architecture (`GeneratedAndroidServiceWorkerService` & Notifications)

1. **Foreground Service & Ongoing Notification (`AndroidServiceWorkerController`)**:
   - `ServiceWorkerExtensionContributor` emits `GeneratedAndroidServiceWorkerService` (extending `GenericUiAndroidService`) plus `<service android:foregroundServiceType="...">` in `AndroidManifest.xml` (mapping `specialUse`, `mediaPlayback`, `dataSync`, `microphone`, `camera`, `location`, etc.).
   - Runs worker logic on a dedicated `"GenericUI-ServiceWorker"` executor thread while calling `ServiceCompat.startForeground` via `AndroidExtensionRuntimeBridge` with a default notification channel (`genericui.serviceworker.<pkg>`), a default `"Stop"` button (`ServiceWorkerNotificationAction.DEFAULT_STOP_ACTION_ID` -> `ACTION_STOP_SERVICE`), dynamic `updateNotificationText` / `setNotificationTitle` / `setNotificationText` / `setNotificationSubText` updates, and `extendNotificationActions` / `replaceNotificationActions` action intents.
2. **In-Process `Binder` IPC & Conditional `BOOT_COMPLETED` Receiver**:
   - `AndroidServiceWorkerBinder` exposes direct in-process client/service binding alongside `ServiceWorkerChannel`.
   - When `ServiceWorkerOptions.autostart == true`, codegen emits `RECEIVE_BOOT_COMPLETED` permission and `GeneratedAndroidServiceWorkerBootReceiver` (`ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`); when `autostart == false`, zero boot receiver is generated.

---

## Consumer `.so` 16KB ELF & ZIP Page Alignment

`GenericUiCompileJniTask` / `GenericUiAndroidToolchain` link consumer C/C++ native libraries (`pageSize16Kb = true` default) with `-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384`, and `GenericUiPackageAndroidApkTask` aligns `STORED` `.so` entries inside the APK to `16384`-byte boundaries and runs `zipalign -f -P 16 4` (with Android 15+ 16KB page compatibility out of the box), paired at runtime with `:jvm` [`JniLibraryLoader`](../../docs/jvm.md).

---

## Native Material Rendering, Material XML Themes, Activities, & Resources

- **`BootstrapType.NONE` vs. `!= NONE` & Style Restraints**: Unmodified Material/AppCompat views by default; `ViewCompat.setBackgroundTintList` + contrasting text when `BootstrapType != NONE`; full `ComponentStyle` honored on `StyledContainer`, `StyledButton`, and `:extensions:ui:alien`; user classes strictly isolated in `styleSnapshot.customClasses`.
- **Generated Material XML & 1:1 Activities**: Generates `genericui_theme_colors.xml`, `values/themes.xml`, `values-night/themes.xml` (`@style/Theme.GenericUI.App`), `AndroidManifest.xml`, `GeneratedAndroidApplication`, and 1:1 `<FragmentName>Activity : GenericUiFragmentActivity` (`AndroidActivityRegistry`, `AndroidLauncher`).
- **Title Chrome & Resources**: `TitleCapabilities` hides top navbar unless `TitleBar` or `MenuBar` is configured (`MaterialToolbar` / `AndroidNavbarImpl`); `AndroidResourceResolverImpl` loads `res/` and assets via `Context.resources`.
