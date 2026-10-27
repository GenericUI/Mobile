# `:mobile:android` — Native Android Backend (`androidMain`)

## Overview
`:mobile:android` is the native Android backend for GenericUI, built on Android `View` / `ViewGroup`, `AppCompatActivity`, and Material Components.

### Key Highlights
- **Genuine Android / Material System Controls (`BootstrapType.NONE` Default)**: When `getBootstrap().type == BootstrapType.NONE` (the default), views render as untinted Android / Material controls with zero Bootstrap CSS styling.
- **Semantic `BootstrapType` Tinting**: Setting `DANGER`, `PRIMARY`, `SUCCESS`, `WARNING`, `INFO`, `SECONDARY`, `LIGHT`, `DARK`, or `LINK` tints the native Android `View` with the semantic color (`outline = true/false`). Unsupported CSS properties and transitions are silently ignored.
- **Strict User CSS Class Isolation**: `getClasses()`, `getClass()`, and `hasClass()` return only user-added `customClasses`.
- **1:1 Generated `AppCompatActivity` Subclasses & `AndroidManifest.xml`**: Automatically generated for every `@MainAppFragment` and `@AppFragment` along with `GeneratedAndroidApplication` (`app.addStyleSheet(...)` startup wiring).
- **Live System Theme Detection (`ThemeMode.AUTO`)**: Tracks `UiModeManager` / `Configuration.UI_MODE_NIGHT_MASK` and `AppCompatDelegate.setDefaultNightMode`.

See [`../docs/android.md`](../docs/android.md) for full documentation.
