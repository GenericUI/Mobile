# GenericUI — Mobile Submodule (`:mobile`)

> **Main Repository**: [**GenericUI/GenericUI**](https://github.com/GenericUI/GenericUI)

This repository is the **Mobile Backend Submodule (`:mobile`)** of the [**GenericUI**](https://github.com/GenericUI/GenericUI) Kotlin Multiplatform Native-First UI framework. It provides the shared mobile layout/gesture/lifecycle engine (`:mobile:shared`) and the native Android (`:mobile:android`) and iOS (`:mobile:ios`) platform backends.

---

## Submodule Structure

| Gradle Module | Directory | Target Platforms | Description |
| :--- | :--- | :--- | :--- |
| **`:mobile:shared`** | `shared/` | `android`, `iosArm64`, `iosSimulatorArm64` | Shared mobile adaptive form-factor coordinator (`MobileDeviceFormFactor`, `MobileOrientationPosture`, `MobileAdaptiveFragmentCoordinator`), safe-area & display cutout inset tracking (`MobileSafeAreaTracker`), multi-touch gesture recognizer & fling physics (`MobileGestureRecognizer`, `MobileFlingPhysics`), Picture-in-Picture controller (`MobilePipController`), document picker bridge (`MobileDocumentPickerBridge`), and memory-pressure-aware lazy viewport recycling (`MobileLazyViewportPool`). |
| **`:mobile:android`** | `android/` | `android` | Native Android implementation (`AndroidNativeFactory`), 1:1 `Fragment`-hosting `GenericUiFragmentActivity` (`AppCompatActivity` + `MaterialToolbar` + `BottomNavigationView`), native DocumentsUI file & folder browser (`Intent.ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`), `RecyclerView`-backed lazy views (`AndroidLazyListImpl`, `AndroidLazyGridImpl`, `AndroidTreeViewImpl`), and Vulkan / EGL OpenGL ES surfaces. |
| **`:mobile:ios`** | `ios/` | `iosArm64`, `iosSimulatorArm64` | Native iOS UIKit implementation (`IosNativeFactory`), `UIDocumentPickerViewController` file & folder browser with `UTType` filtering, `CADisplayLink` VBlank-synchronized `Canvas2D`/`GLCanvas`/`VKCanvas` (MoltenVK) surfaces, and true lazy list/grid/tree view recycling. |

---

## Usage via the GenericUI Gradle Plugin

Enable mobile backends in your project's `build.gradle.kts` via the [GenericUI Gradle Plugin](https://github.com/GenericUI/GenericUI):

```kotlin
plugins {
    id("com.nexustools.genericui")
}

genericui {
    backends {
        allMobile() // enables android and ios
        // or individually:
        // android()
        // ios()
    }
}
```

---

## Related GenericUI Repositories

- **Main Repository**: [https://github.com/GenericUI/GenericUI](https://github.com/GenericUI/GenericUI)
- **Desktop Backends (`:desktop`)**: [https://github.com/GenericUI/Desktop](https://github.com/GenericUI/Desktop)
- **Web Backend (`:web`)**: [https://github.com/GenericUI/Web](https://github.com/GenericUI/Web)
- **Extensions (`:extensions`)**: [https://github.com/GenericUI/Extensions](https://github.com/GenericUI/Extensions)
- **UI Testing Suite (`:tests` / `JestElectricRunner`)**: [https://github.com/GenericUI/Tests](https://github.com/GenericUI/Tests)
