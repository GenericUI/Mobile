# `:mobile:ios` — Native iOS UIKit Backend (`iosX64`, `iosArm64`, `iosSimulatorArm64`)

## Overview
`:mobile:ios` is the native iOS UIKit backend for GenericUI (`iosX64`, `iosArm64`, `iosSimulatorArm64`).

### Key Highlights
- **Genuine UIKit System Controls (`BootstrapType.NONE` Default)**: When `getBootstrap().type == BootstrapType.NONE` (the default), controls render as untinted native UIKit `UIView` / `UIControl` widgets with zero Bootstrap CSS styling.
- **Semantic `BootstrapType` Tinting**: Setting `DANGER`, `PRIMARY`, `SUCCESS`, `WARNING`, `INFO`, `SECONDARY`, `LIGHT`, `DARK`, or `LINK` tints the native UIKit control with the semantic color (`outline = true/false`). Unsupported CSS properties and transitions are silently ignored.
- **Strict User CSS Class Isolation**: `getClasses()`, `getClass()`, and `hasClass()` return only user-added `customClasses`.
- **Live System Theme Detection (`ThemeMode.AUTO`)**: Tracks `UITraitCollection.currentTraitCollection.userInterfaceStyle` (`UIUserInterfaceStyleDark` vs. `UIUserInterfaceStyleLight`) and updates `UIWindow.overrideUserInterfaceStyle`.

See [`../docs/ios.md`](../docs/ios.md) for full documentation.
