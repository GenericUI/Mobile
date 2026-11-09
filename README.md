# `:mobile` — Mobile Backends (`:mobile:common`, `:mobile:android`, `:mobile:ios`)

## Overview
The `:mobile` module hierarchy provides native mobile UI backends for **Android** (`androidMain` — Android Views + Material Components) and **iOS** (`iosX64`, `iosArm64`, `iosSimulatorArm64` — UIKit), built on top of `:common`, `:systemCommon`, and `:mobile:common`.

---

## Module Hierarchy

```text
:common
  └── :systemCommon
        └── :mobile:common (commonMain: MobileBaseNativeImpl, MobileLazyViewportEngine, MobileWindowNativeImpl, etc.)
              ├── :mobile:android (androidMain: AndroidLauncher, GenericUiFragmentActivity, AndroidBaseNativeImpl)
              └── :mobile:ios     (iosMain: IosLauncher, UIKit UIViewController / UIView, IosBaseNativeImpl)
```

| Submodule | Targets | Documentation |
|-----------|---------|---------------|
| [`:mobile:common`](common/README.md) | `jvm`/`androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64` | [docs/common.md](docs/common.md) |
| [`:mobile:android`](android/README.md) | `androidTarget` (`androidMain`) | [docs/android.md](docs/android.md) |
| [`:mobile:ios`](ios/README.md) | `iosX64`, `iosArm64`, `iosSimulatorArm64` | [docs/ios.md](docs/ios.md) |

---

## Native Mobile Control Rendering (`BootstrapType.NONE`), Native Control Tinting + `ThemeConfig` (`BootstrapType != NONE`), & System Style Restraints

1. **`BootstrapType.NONE` (`BootstrapType.None`) Default — 100% Unmodified Native Mobile Controls**:
   - Native mobile applications **never** apply Bootstrap CSS styling to native controls.
   - When `component.getBootstrap().type == BootstrapType.NONE` (the default), `:mobile:android` and `:mobile:ios` render the **100% unmodified genuine OS system control** (`MaterialButton` / `AppCompatEditText` / `SwitchMaterial` restoring `defaultNativeBackground` on Android; `UIButton` / `UITextField` / `UISwitch` / `UISlider` on iOS) for the active `LIGHT` / `DARK` theme.
2. **`BootstrapType != BootstrapType.NONE` — Native Control Tinting via `ThemeConfig` + Contrasting Text**:
   - When `BootstrapType != BootstrapType.NONE` (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`), `:mobile:android` and `:mobile:ios` tint the native control drawable (`ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(tintColor))` on Android; `UIButton.Configuration` / `tintColor` on iOS) using the resolved `ThemeConfig` / `BootstrapColorPalette` color while preserving native Material elevation, ripples, borders, and hover/pressed/disabled states, and render contrasting text on top (`outline = true/false`).
3. **System Backend Typography vs. Aesthetic Style Restraints**:
   - **Text / Typography Elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`)**: Honor typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`, plus `bootstrap.textColorType`, `bootstrap.fontSize`, `bootstrap.fontWeight`, `bootstrap.textAlignment`).
   - **Buttons, Inputs, Controls, & Standard Containers (`Button`, `IconButton`, `ToggleButton`, `CheckBox`, `RadioButton`, `Switch`, `Select`, `DropdownButton`, `TextField`, `TextArea`, `RangeSlider`, `ProgressBar`, `Spinner`, `TabContainer`, `Tab`, `Container`, `Card`, etc.)**: Aesthetic skinning and typography overrides on `ComponentStyle` (`backgroundColor`, `background`, `color`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `textShadow`, `filter`, `backdropFilter`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored** on mobile backends. Standard components can **ONLY** be tinted via `BootstrapType`.
   - **`StyledContainer` & `StyledButton` (and [`:extensions:alien`](../extensions/docs/alien.md))**: Honor full `ComponentStyle` aesthetic and typography properties across all backends.
4. **Strict User CSS Class Isolation**:
   - In `MobileBaseNativeImpl`, `AndroidBaseNativeImpl`, and `IosBaseNativeImpl`, `addClass`, `removeClass`, `hasClass`, `getClasses()`, and `getClass()` return **only** user-added custom classes (`styleSnapshot.customClasses`). `ComponentBootstrap` (`getBootstrap()`) enum utilities are executed directly by the native Android and iOS layout/rendering engines.

---

## Precompiled `.scss` Stylesheets & `IApplication` Theming (`ThemeMode.AUTO`)

- **Build-Time `.scss` Precompilation**: `.scss` files in `src/commonMain/resources/**` generate `ResourceType.STYLE` (`R.style.<name>` / `Resource.style.<name>`) and precompile via `SassKotlinPrecompiler` into `PrecompiledStyleSheet` (`CompiledStyleRule`) objects attached via `IApplication.addStyleSheet(...)` / `removeStyleSheet(...)` (automatically wired in `GeneratedAndroidApplication` and `GeneratedIosLauncherMain` when `styles { globalStyleSheet = "..." }` is configured).
- **System Theme Detection (`ThemeMode.AUTO` Default)**:
  - **Android (`:mobile:android`)**: Reads `Configuration.UI_MODE_NIGHT_MASK` (`UiModeManager` / `resources.configuration.uiMode`), listens to `onConfigurationChanged`, and applies `AppCompatDelegate.setDefaultNightMode(...)`.
  - **iOS (`:mobile:ios`)**: Reads `UITraitCollection.currentTraitCollection.userInterfaceStyle` (`UIUserInterfaceStyleDark` vs. `UIUserInterfaceStyleLight`), observes `traitCollectionDidChange:`, and updates `UIWindow.overrideUserInterfaceStyle`.

---

## Mobile `TitleCapabilities` & Optional Top Navbar Chrome

Unlike desktop platforms where an OS window always has a titlebar (`isTitleBarRequired = true`), both `:mobile:android` and `:mobile:ios` report `isTitleBarRequired = false`, `titleBarRendersAsNavbar = true`, `menuBarRendersAsNavbar = true`, and `menuBarMergesIntoTitleNavbar = true`:
1. **Neither `TitleBar` nor `MenuBar` set**: **No top navbar is rendered at all**; the root container fills the entire safe-area viewport.
2. **`TitleBar` and/or `MenuBar` set**: Renders a single top navigation bar (`MaterialToolbar` / `AndroidNavbarImpl` on Android; native top navigation bar on iOS) with single-placement custom components (`mountedInTitleBar = true`) and a merged right-aligned `MenuBar` dropdown (`☰`) when a `MenuBar` is present.

---

## Bare-Metal Native Async `IFileSystem`, `Database`, & `Network` Across Mobile Backends

| Backend | `FileSystem` (`FileSystemAvailability.WithPermission`) & `UserDirectory` (`FileSystemAvailability.Always`) | Native `Database` (`DatabaseQuerySqlTranslator`) | Async `Network` (`TcpClient`, `UdpClient`), `HttpClient`, `WebSocket`, & `IApplication.isOffline` |
| :--- | :--- | :--- | :--- |
| [`:mobile:android`](android/README.md) | `FileChannel` + Storage Access Framework (`ContentResolver` / `ACTION_OPEN_DOCUMENT*`); `AndroidUserDirectoryNativeImpl` (`context.filesDir` & `context.cacheDir`) | **`androidx.sqlite:sqlite-ktx`** (`android.database.sqlite.SQLiteDatabase` / `SQLiteStatement` on `Dispatchers.IO`) | `SocketChannel` / `SSLSocket`, `DatagramChannel` / `MulticastSocket`, `HttpURLConnection`, RFC 6455 WebSocket, `ConnectivityManager` (`NetworkCallback`) |
| [`:mobile:ios`](ios/README.md) | `NSFileManager` / `dispatch_io_t` + `UIDocumentPickerViewController` security-scoped URLs; `IosUserDirectoryNativeImpl` (`Library/Application Support` & `Library/Caches`) | **`libsqlite3`** (`sqlite3_open_v2`, `sqlite3_prepare_v2`) | `Network.framework` (`NWConnection` TCP/TLS & BSD `SOCK_DGRAM`), `NSURLSession` (`NSURLSessionDataTask`, `NSURLSessionWebSocketTask`), `NWPathMonitor` |
