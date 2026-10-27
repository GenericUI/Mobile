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

## Native Mobile Control Rendering (`BootstrapType.NONE` Default), Semantic `BootstrapType` Tinting, & CSS Behavior

1. **`BootstrapType.NONE` (`BootstrapType.None`) Default & Zero Bootstrap CSS Styling on Mobile Apps**:
   - Native mobile applications **never** apply Bootstrap CSS styling to native controls.
   - When `component.getBootstrap().type == BootstrapType.NONE` (the default) and no inline `ComponentStyle` (`component.getStyle()`) background/border is set, `:mobile:android` and `:mobile:ios` render the **authentic OS system control** (`MaterialButton` / `AppCompatEditText` / `SwitchMaterial` on Android; `UIButton` / `UITextField` / `UISwitch` / `UISlider` on iOS) for the active `LIGHT` / `DARK` theme.
2. **Semantic `BootstrapType` Tinting**:
   - When a semantic `BootstrapType` (`DANGER`, `PRIMARY`, `SUCCESS`, `WARNING`, `INFO`, `SECONDARY`, `LIGHT`, `DARK`, `LINK`) is set on `component.getBootstrap().type`, both `:mobile:android` and `:mobile:ios` emulate `BootstrapType` by **tinting the native mobile control** with the semantic color (`BootstrapColorPalette.resolveColor(type, darkTheme)` — red for `DANGER`, blue for `PRIMARY`, green for `SUCCESS`, etc., supporting `outline = true/false`).
   - Standard CSS `accentColor` (`component.getStyle().setAccentColor(color)` / `accent-color`) sets the active indicator fill on switches, checkboxes, radio buttons, sliders, and progress bars.
3. **Silent Ignore of Unsupported CSS Properties**:
   - Unsupported CSS properties and CSS transitions on `:mobile:android` and `:mobile:ios` are **silently ignored** (whereas [`:extensions:alien`](../extensions/docs/alien.md) supports 100% of all CSS properties and transitions).
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
