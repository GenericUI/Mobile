# `:mobile:android` — Native Android Backend (`androidMain`)

## Overview
`:mobile:android` is the native Android backend for GenericUI, built on standard Android SDK `View` / `ViewGroup` classes, AndroidX `AppCompatActivity`, and Material Components (`MaterialButton`, `MaterialToolbar`, `TabLayout`, `Slider`, `SwitchMaterial`, `DrawerLayout`). It depends on `:common`, `:systemCommon`, and `:mobile:common`.

---

## Native Android Control Rendering (`BootstrapType.NONE` Default) & Semantic `BootstrapType` Tinting

1. **`BootstrapType.NONE` Default & Zero Bootstrap CSS Styling**:
   - Native Android applications **never** use Bootstrap CSS styling.
   - When `component.getBootstrap().type == BootstrapType.NONE` (`BootstrapType.None`, the default) and no inline `ComponentStyle` (`component.getStyle()`) background/border is set, `AndroidBaseNativeImpl` renders the **authentic Android / Material system view** (`MaterialButton`, `AppCompatEditText`, `AppCompatCheckBox`, `SwitchMaterial`, `Slider`) for the active `LIGHT` / `DARK` theme.
2. **Semantic `BootstrapType` Tinting**:
   - When a semantic `BootstrapType` (`DANGER`, `PRIMARY`, `SUCCESS`, `WARNING`, `INFO`, `SECONDARY`, `LIGHT`, `DARK`, `LINK`) is set on `component.getBootstrap().type`, `AndroidBaseNativeImpl` emulates `BootstrapType` by **tinting the native Android `View`** (`backgroundTintList`, stroke color, and text color via `BootstrapColorPalette.resolveColor(type, darkTheme)`), supporting `outline = true/false`.
   - `component.getStyle().setAccentColor(color)` (`accent-color`) sets `buttonTintList` / `thumbTintList` / `progressTintList` on interactive Android controls.
   - Unsupported CSS properties and CSS transitions are **silently ignored**.
3. **Strict User CSS Class Isolation**:
   - In `AndroidBaseNativeImpl`, `addClass`, `removeClass`, `hasClass`, `getClasses()`, and `getClass()` return **only** user-added custom classes (`styleSnapshot.customClasses`). `ComponentBootstrap` (`getBootstrap()`) enum utilities are executed directly by the Android `LayoutParams` and view styling engine.

---

## Precompiled `.scss` Stylesheets & `IApplication` Theming (`ThemeMode.AUTO`)

- **Build-Time `.scss` Precompilation**: `.scss` files in `src/commonMain/resources/**` generate `ResourceType.STYLE` (`R.style.<name>`) and precompile via `SassKotlinPrecompiler` into `PrecompiledStyleSheet` objects. When `styles { globalStyleSheet = "styles/main.scss" }` is configured in `build.gradle.kts`, `GenericUiGenerateAndroidActivitiesTask` emits `app.addStyleSheet(...)` inside `GeneratedAndroidApplication.onCreate()`.
- **System Theme Detection (`ThemeMode.AUTO` Default)**:
  - `AndroidApplicationImpl` defaults to `ThemeMode.AUTO`, reading `resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK` (`UiModeManager` / `UI_MODE_NIGHT_YES` vs. `UI_MODE_NIGHT_NO`), listening to `onConfigurationChanged`, and calling `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM | MODE_NIGHT_YES | MODE_NIGHT_NO)`.

---

## Generated `AndroidManifest.xml`, 1:1 `AppCompatActivity` Subclasses, & `AndroidLauncher`

During the build, `GenericUiGenerateLaunchersTask` / `GenericUiGenerateAndroidActivitiesTask` scans `src/commonMain/kotlin` for `@MainAppFragment` and `@AppFragment` annotations and generates:
1. **`AndroidManifest.xml`**: Declares permissions, features, metadata, `GeneratedAndroidApplication`, the `@MainAppFragment` launcher `<activity>`, a separate `<activity>` for every `@AppFragment`, and `<service>` entries from `genericui { android { manifest { ... } services { ... } } }`.
2. **1:1 `AppCompatActivity` Subclasses**: Every `@MainAppFragment` and `@AppFragment` receives its own `<FragmentName>Activity : GenericUiFragmentActivity()`.
3. **`AndroidActivityRegistry` & Intent-Based `launchFragment` Routing**: `GeneratedAndroidApplication` initializes the Android runtime in `onCreate()` and registers every `fragmentId -> Activity::class.java` mapping so `launchFragment(...)` starts the target `Activity` via Android `Intent` with serialized `FragmentBundle` extras.

---

## Window Chrome, `TitleCapabilities`, & `AndroidNavbarImpl` (`MaterialToolbar`)

On Android (`AndroidWindowImpl` & `GenericUiFragmentActivity`), `TitleCapabilities` has `isTitleBarRequired = false`, `titleBarRendersAsNavbar = true`, `menuBarRendersAsNavbar = true`, and `menuBarMergesIntoTitleNavbar = true`:
- **No `TitleBar` and no `MenuBar` set**: No top bar is mounted (`MaterialToolbar` is omitted).
- **`TitleBar` and/or `MenuBar` set**: Mounts a top navbar (`MaterialToolbar` / `AndroidNavbarImpl`) with single-placement custom components (`mountedInTitleBar = true`) and a merged right-aligned `MenuBar` dropdown (`☰`).

---

## Compiled Android `res/` Resources (`AndroidResourceResolverImpl`)

During the Gradle build, `GenericUiCompileResourcesTask` compiles `src/commonMain/resources/` into standard Android `res/` directories, resolved at runtime by `AndroidResourceResolverImpl` via `Context.resources`.
