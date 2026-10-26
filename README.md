# `:mobile` — Mobile Backends (`:mobile:common`, `:mobile:android`, `:mobile:ios`)

## Overview
The `:mobile` module hierarchy provides native mobile UI backends for **Android** (`androidMain` — Android Views + Material Components) and **iOS** (`iosX64`, `iosArm64`, `iosSimulatorArm64` — UIKit), built on top of `:common`, `:systemCommon`, and `:mobile:common`.

---

## Module Hierarchy

```text
:common
  └── :systemCommon
        └── :mobile:common (commonMain: MobileBaseNativeImpl, MobileLazyViewportEngine, MobileWindowNativeImpl, etc.)
              ├── :mobile:android (androidMain: AndroidLauncher, GenericUiFragmentActivity, AndroidWidgetFactoryImpl)
              └── :mobile:ios     (iosMain: IosLauncher, UIKit UIViewController / UIView implementations)
```

| Submodule | Targets | Documentation |
|-----------|---------|---------------|
| [`:mobile:common`](common/README.md) | `jvm`/`androidTarget`, `iosX64`, `iosArm64`, `iosSimulatorArm64` | [docs/common.md](docs/common.md) |
| `:mobile:android` | `androidTarget` (`androidMain`) | [docs/android.md](docs/android.md) |
| `:mobile:ios` | `iosX64`, `iosArm64`, `iosSimulatorArm64` | [docs/ios.md](docs/ios.md) |

---

## Single-`commonMain` Consumer Architecture (`@MainAppFragment` & `@AppFragment`)

Mobile consumer applications write 100% of their application code in `src/commonMain/kotlin` and `src/commonMain/resources`. No platform source sets (`androidMain`, `iosMain`) or handwritten `AndroidManifest.xml` / `Activity` boilerplate are required:

- **`@MainAppFragment`**: Marks the primary `Fragment` launched on app startup.
- **`@AppFragment(id = "...")`**: Marks secondary launchable `Fragment` classes.
- **Android 1:1 Activity Generation**: `GenericUiGenerateLaunchersTask` generates `AndroidManifest.xml`, `GenericUiGeneratedApplication : GenericUiAndroidApplication()`, and a dedicated `AppCompatActivity` subclass (`<FragmentName>Activity : GenericUiFragmentActivity()`) for every `@MainAppFragment` and `@AppFragment`. Calling `launchFragment("settings", args)` starts `SettingsFragmentActivity` via an Android `Intent` with serialized `FragmentBundle` extras (`GenericUiFragmentActivity.EXTRA_FRAGMENT_ARGS`).
- **iOS `IosLauncher`**: `GenericUiGenerateLaunchersTask` generates the `iosMain` entry point helpers calling `IosLauncher.createRootViewController(...)` and `IosLauncher.launch(...)`.

---

## Mobile `TitleCapabilities` & Optional Top Navbar Chrome

Unlike desktop platforms where an OS window always has a titlebar (`isTitleBarRequired = true`), both `:mobile:android` and `:mobile:ios` report:

```kotlin
TitleCapabilities(
    isTitleBarRequired = false,
    isEmulated = true,
    supportsIcon = true,
    supportsLeadingComponents = true,
    supportsCenterComponents = true,
    supportsTrailingComponents = true,
    supportsCustomComponents = true,
    supportsSubtitle = true,
    supportsMinimizeButton = false,
    supportsMaximizeButton = false,
    supportsCloseButton = false,
    supportsFullscreenButton = false,
    canEmbedComponentsInTitleBar = true,
    titleBarRendersAsNavbar = true,
    menuBarRendersAsNavbar = true,
    menuBarMergesIntoTitleNavbar = true
)
```

### Top Navbar Rendering Rules on Android & iOS
1. **Neither `TitleBar` nor `MenuBar` set**: **No top navbar is rendered at all**; the root container fills the entire safe-area viewport without an empty top bar.
2. **`TitleBar` only**: Renders a top navigation bar (`MaterialToolbar` / `AndroidNavbarImpl` on Android; native top navigation bar on iOS) containing the title, subtitle, icon, and any custom leading, center, and trailing components (marked `mountedInTitleBar = true` so they appear strictly once inside the navbar).
3. **`MenuBar` only**: Renders a top navigation bar with a right-aligned dropdown menu trigger (`☰`) opening the `MenuBar` hierarchies.
4. **Both `TitleBar` and `MenuBar` set**: Merges both into a **single** top navigation bar (`menuBarMergesIntoTitleNavbar = true`) with the `TitleBar` content on the left/center and the `MenuBar` dropdown + trailing components on the right.
