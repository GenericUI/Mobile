# `:mobile:android` — Native Android Backend (`androidMain`)

## Overview
`:mobile:android` is the native Android backend for GenericUI, built on standard Android SDK `View` / `ViewGroup` classes, AndroidX `AppCompatActivity`, and Material Components (`MaterialButton`, `MaterialToolbar`, `TabLayout`, `Slider`, `SwitchMaterial`, `DrawerLayout`). It depends on `:common`, `:systemCommon`, and `:mobile:common`.

---

## Native Android Control Rendering (`BootstrapType.NONE`), `ThemeConfig` Drawable Tinting (`BootstrapType != NONE`), & System Style Restraints

1. **`BootstrapType.NONE` Default — 100% Unmodified Android / Material System Views**:
   - Native Android applications **never** use Bootstrap CSS styling.
   - When `component.getBootstrap().type == BootstrapType.NONE` (`BootstrapType.None`, the default), `AndroidBaseNativeImpl` renders the **100% unmodified genuine Android / Material system view** (`MaterialButton`, `AppCompatEditText`, `AppCompatCheckBox`, `SwitchMaterial`, `Slider` with its original `defaultNativeBackground` drawable and `ViewCompat.setBackgroundTintList(view, null)`) for the active `LIGHT` / `DARK` theme.
2. **`BootstrapType != BootstrapType.NONE` — Native Material Drawable Tinting + Contrasting Text**:
   - When `BootstrapType != BootstrapType.NONE` (`PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, `DARK`), `AndroidBaseNativeImpl` tints the native Android `View` drawable (`ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(tintColor))`) using the active `ThemeConfig` / `BootstrapColorPalette.resolveColor(type, darkTheme)` while preserving native Material elevation, ripples, and pressed/disabled states, and renders contrasting text on top (`outline = true/false`).
3. **System Backend Typography vs. Aesthetic Style Restraints**:
   - **Text / Typography Elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`)**: Honor typography and text-color properties (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`, `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`, `wordBreak`).
   - **Buttons, Inputs, Controls, & Standard Containers**: Aesthetic skinning and typography overrides on `ComponentStyle` (`backgroundColor`, `background`, `color`, `accentColor`, `borderColor`, `borderWidth`, `borderStyle`, `borderRadius`, `boxShadow`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`) are **silently ignored** (never replacing a standard control's native background with a custom `GradientDrawable`). Standard components can **ONLY** be tinted via `BootstrapType`.
   - **`StyledContainer` & `StyledButton` (and `:extensions:alien`)**: Honor full `ComponentStyle` aesthetic (`GradientDrawable` background/border/radius) and typography properties.
4. **Strict User CSS Class Isolation**:
   - In `AndroidBaseNativeImpl`, `addClass`, `removeClass`, `hasClass`, `getClasses()`, and `getClass()` return **only** user-added custom classes (`styleSnapshot.customClasses`). `ComponentBootstrap` (`getBootstrap()`) enum utilities are executed directly by the Android `LayoutParams` and view styling engine.

---

## Precompiled `.scss` Stylesheets, Automatic Android Material XML Generation (`@style/Theme.GenericUI.App`), & `IApplication` Theming (`ThemeMode.AUTO` & `ThemeConfig`)

- **Automatic Android Material Theme XML Generation (`@style/Theme.GenericUI.App`)**:
  - From the `genericui { theme { ... } }` block in `build.gradle.kts`, `GenericUiGenerateAndroidActivitiesTask` automatically generates:
    - `res/values/genericui_theme_colors.xml` (`genericui_color_primary`, `genericui_color_secondary`, `genericui_color_accent`, `genericui_color_success`, `genericui_color_danger`, `genericui_color_warning`, `genericui_color_info`, plus light/dark surface/background/text/border colors).
    - `res/values/themes.xml` (`@style/Theme.GenericUI.App` inheriting `Theme.Material3.DayNight.NoActionBar` or `Theme.Material3.Light.NoActionBar` with `colorPrimary`, `colorSecondary`, `colorTertiary`, `colorError`, `android:colorBackground`, `colorSurface`, `colorOnPrimary`, `colorOnSurface`, `android:statusBarColor`, and `android:navigationBarColor`).
    - `res/values-night/themes.xml` (`@style/Theme.GenericUI.App` dark palette overrides).
  - The generated `AndroidManifest.xml` automatically applies `android:theme="@style/Theme.GenericUI.App"` to `<application>`, and `GeneratedAndroidApplication.onCreate()` initializes `app.setThemeConfig(ThemeConfig(...))` at startup.
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
