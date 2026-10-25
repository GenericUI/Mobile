# `:mobile:ios` — Native Apple iOS UIKit Backend

> **Parent Submodule**: [`:mobile` (`../README.md`)](../README.md) | **Root Repository**: [**GenericUI (`../../README.md`)**](../../README.md)  
> **Root Docs**: [SourceSet Hierarchy](../../docs/sourcesets.md) · [Backends Guide](../../docs/backends.md) · [Extensions Guide](../../docs/extensions.md) · [Gradle Plugin](../../docs/gradle-plugin.md) · [UI Testing](../../docs/testing.md)  
> **Sibling Mobile Modules**: [`:mobile:shared`](./shared.md) · [`:mobile:android`](./android.md)

The **`:mobile:ios`** module (`com.nexustools.genericui.ios.*`) is the native Apple iOS & iPadOS (`iosArm64` & `iosSimulatorArm64`) backend for **GenericUI**. Built directly on **UIKit** (`UIWindow`, `UIScene`, `UIViewController`, `UINavigationBar`, `UITabBar`, `UIView`, `UIDocumentPickerViewController`), **QuartzCore** (`CADisplayLink`, `CAMetalLayer`), **CoreGraphics**, **ImageIO**, **MoltenVK**, and [`:mobile:shared`](./shared.md), it delivers native iOS & iPadOS performance without webviews or emulated widget trees.

---

## Core Architecture & Native Capabilities

1. **UIKit `UIViewController` / `UIScene` Lifecycle & Chrome (`IosNativeFactory`, `IosWindowImpl`, `IosNativeView`)**:
   - Maps GenericUI components directly to native UIKit controls (`UIButton`, `UITextField`, `UISwitch`, `UISlider`, `UIProgressView`, `UIScrollView`, `UISplitViewController`, `UIMenu`).
   - Bridges `UISceneDelegate` and `UIViewController` lifecycle callbacks (`viewDidLoad`, `viewWillAppear`, `viewDidAppear`, `viewWillDisappear`, `didReceiveMemoryWarning`, `viewWillTransitionToSize`) through `:mobile:shared`'s `MobileLifecycleBridge` and `MobileAdaptiveFragmentCoordinator`.
   - Translates `TitleBar` instructions (`window.setTitleBar(titleBar)` or `fragment.setNavBar(titleBar)`) into native **`UINavigationBar`** / `UINavigationItem` titles and `UIBarButtonItem` actions, and maps `BottomNavigationBar` to **`UITabBar`**.
   - Propagates `UIWindow.safeAreaInsets` (Dynamic Island, notch, Home Indicator, and iPad Stage Manager / Split View bounds) into `MobileSafeAreaTracker`.
2. **`CADisplayLink` VBlank-Synchronized Rendering (`registerVBlankRender` with `deltaMs`)**:
   - Synchronizes `AbstractCanvas`, `Canvas2D`, `GLCanvas`, and `VKCanvas` frame callbacks directly to iOS's `CADisplayLink` (including 120Hz ProMotion adaptive frame pacing on supported iPhone Pro and iPad Pro displays), computing exact `deltaMs` intervals between display refreshes.
3. **Native `UIDocumentPickerViewController` File & Folder Browser (`IosFileBrowserDialogImpl`)**:
   - Uses Apple's `UIDocumentPickerViewController` with Uniform Type Identifiers (`UTType` / `UniformTypeIdentifiers.framework`) resolved by `MobileDocumentPickerBridge.resolveIosUniformTypeIdentifiers(request)`:
     - **`FileBrowserMode.OPEN_SINGLE_FILE`**: `UIDocumentPickerViewController(forOpeningContentTypes:)` with `allowsMultipleSelection = false`.
     - **`FileBrowserMode.OPEN_MULTIPLE_FILES`**: `UIDocumentPickerViewController(forOpeningContentTypes:)` with `allowsMultipleSelection = true`.
     - **`FileBrowserMode.SAVE_FILE`**: `UIDocumentPickerViewController(forExporting:asCopy:)`.
     - **`FileBrowserMode.SELECT_FOLDER`**: `UIDocumentPickerViewController(forOpeningContentTypes: [UTType.folder])` (`"public.folder"`), granting security-scoped directory access.
4. **True Lazy Viewport Recycling (`IosLazyListImpl`, `IosLazyGridImpl`, `IosTreeViewImpl`)**:
   - Combines `UICollectionView` / `UITableView` cell reuse patterns with `:mobile:shared`'s `MobileLazyViewportPool` so only visible `[firstVisibleIndex .. lastVisibleIndex]` items are mounted and bound (`onBind`), automatically trimming recycled scrap cells when `UIApplicationDidReceiveMemoryWarningNotification` fires.
5. **Hardware-Accelerated 2D & 3D Canvases (`CoreGraphics` & `MoltenVK` Metal)**:
   - **`Canvas2D` (`:extensions:canvas2d:mobile:ios`)**: Hardware-accelerated **CoreGraphics** (`CGContextRef`) & **CoreText** 2D canvas driven by `CADisplayLink`.
   - **`VKCanvas` (`:extensions:canvas3d:mobile:ios` + `:extensions:gpu`)**: Vulkan 1.2+ SPIR-V pipeline translated directly to **Apple Metal** (`CAMetalLayer`) via **Khronos `MoltenVK`** (`VK_EXT_metal_surface` / `VK_MVK_ios_surface`).
   - **`GLCanvas` (`:extensions:canvas3d:mobile:ios` + `:extensions:gpu`)**: OpenGL ES 3.0 (`EAGLContext` / Metal-backed ANGLE) rendering surface.
6. **Native Apple `ImageIO` Codec (`IosImageIoCodecImpl`)**:
   - Uses `CGImageSource` and `CGImageDestination` (`ImageIO.framework`) to decode and encode `PNG`, `JPEG`, `WEBP`, `HEIC`, `GIF`, and `ICO` directly into `:common`'s `ImageBuffer`.

---

## Key Classes Reference

| Class / Object | Responsibility |
| :--- | :--- |
| `IosNativeFactory` | `NativeBackendFactory` creating UIKit-backed `NativeImpl` peers and registering `IosImageIoCodecImpl`. |
| `IosNativeView` | Wraps `UIView` pointers, `CALayer`/`CAMetalLayer` metadata, safe-area insets, and Retina scale factor. |
| `IosBaseNativeImpl<W>` | Base `NativeImpl<W>` applying Dual-Mode Bootstrap 5 enums and compiled SASS/CSS `ComputedStyle` properties (`CALayer` corner radius, border, shadow, opacity, and `UIView.animate` transitions). |
| `IosBaseContainerNativeImpl<W>` | Container peer managing `UIView` subview hierarchy and Flexbox/AutoLayout geometry. |
| `IosApplicationImpl` | Manages `UIApplication`, `UIScene` configuration, and global theme appearance (`UIUserInterfaceStyle`). |
| `IosWindowImpl` | Wraps `UIWindow`, `UINavigationController`, `UINavigationBar`, and `CADisplayLink` VBlank scheduling. |
| `IosFileBrowserDialogImpl` | Native `UIDocumentPickerViewController` file and folder browser with `UTType` filtering. |
| `IosLazyListImpl`, `IosLazyGridImpl`, `IosTreeViewImpl` | Viewport-windowed lazy list, grid, and tree peers backed by `MobileLazyViewportPool`. |
| `IosImageIoCodecImpl` | Apple `ImageIO` (`CGImageSource` / `CGImageDestination`) implementation of `:common`'s `ImageCodecNativeImpl`. |
| `IosNotificationManagerImpl` | `UNUserNotificationCenter` local & push notification manager. |
| `IosPermissionManagerImpl` | iOS `Info.plist` privacy permission requester (Camera, Microphone, Photo Library, Notifications). |
| `IosNetworkMonitorImpl` | Apple `NWPathMonitor` (`Network.framework`) reachability observer. |

---

## SourceSet Hierarchy (`systemCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `ios` / `iosMain`)

```
commonMain / commonTest
 └── systemCommonMain / systemCommonTest
      └── mobileCommonMain / mobileCommonTest (:mobile:shared)
           ├── androidMain / androidUnitTest (:mobile:android)
           └── iosMain / iosTest (:mobile:ios — iosArm64 & iosSimulatorArm64)
```

---

## Gradle Configuration & Tasks

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.nexustools.genericui")
}

genericui {
    backends {
        ios() // Configures iosArm64 (device) and iosSimulatorArm64 (Apple Silicon Simulator)
    }

    application {
        appName = "Episode Caster"
        appId = "com.nexustools.episodecaster"
        version = "1.0.0"
    }

    graphics {
        // Links MoltenVK so VKCanvas & :extensions:gpu run natively atop Apple Metal:
        bundleMoltenVkOnApple = true
    }

    extensions {
        canvas2d() // CoreGraphics 2D canvas
        canvas3d() // MoltenVK Metal VKCanvas & EAGL/ANGLE GLCanvas
        media()    // AVPlayer / VideoToolbox hardware-accelerated VideoPlayer & AudioPlayer
        webView()  // Uses native WKWebView on iOS (never NJS)
    }
}
```

### iOS Gradle Tasks

| Task Name | Description |
| :--- | :--- |
| `./gradlew iosDevelopment` | Compiles debug iOS frameworks/binaries (`iosSimulatorArm64` and `iosArm64`) and prepares the Xcode simulator bundle. |
| `./gradlew iosProduction` | Compiles release `iosArm64` binaries and stages `MoltenVK` and asset resources for App Store archiving. |
| `./gradlew iosSimulatorArm64Test` | Runs the `JestElectricRunner` UI test suite on iOS via `IosJestElectricRunnerNativeImpl`. |

---

## Code Examples

### 1. `CADisplayLink` VBlank Rendering on iOS (`Canvas2D` & `MoltenVK` `VKCanvas`)

```kotlin
import com.nexustools.genericui.ext.canvas2d.Canvas2D
import com.nexustools.genericui.ext.canvas3d.VKCanvas

// Synchronized to iOS CADisplayLink (up to 120Hz ProMotion) with exact deltaMs:
val iosVectorCanvas = Canvas2D(width = 800, height = 600).apply {
    var phase = 0.0
    registerVBlankRender { deltaMs ->
        phase += deltaMs * 0.001
        clear("#121318")
    }
}

// Vulkan 1.2+ canvas rendered via MoltenVK on CAMetalLayer:
val iosMetalVulkanCanvas = VKCanvas(width = 800, height = 600).apply {
    registerVBlankRender { deltaMs ->
        // Record SPIR-V pipeline commands executed on Apple A-series / M-series GPU
    }
}
```

### 2. Picking Files & Folders with `UIDocumentPickerViewController` (`IosFileBrowserDialogImpl`)

```kotlin
import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserDialog
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode

fun openIosProjectArchive(dialog: FileBrowserDialog) {
    dialog.show(
        request = FileBrowserRequest(
            title = "Open Artwork or Folder",
            mode = FileBrowserMode.OPEN_SINGLE_FILE,
            filters = listOf(
                FileTypeFilter(
                    description = "Images & Vectors",
                    extensions = listOf("png", "jpg", "webp", "svg"),
                    macOsUniformTypeIdentifiers = listOf("public.png", "public.jpeg", "public.svg-image")
                )
            )
        ),
        callback = object : FileBrowserCallback {
            override fun onFilesSelected(files: List<SelectedFile>, activeFilter: FileTypeFilter?) {
                println("Picked iOS security-scoped URL: ${files.first().pathOrUri}")
            }

            override fun onCancelled() {
                println("iOS document picker dismissed.")
            }
        }
    )
}
```

---

## Related Documentation

- **Parent Mobile Overview**: [`../README.md`](../README.md)
- **Sibling Mobile Docs**: [`:mobile:shared`](./shared.md) · [`:mobile:android`](./android.md)
- **iOS UI Testing**: [Platform Test Runners (`IosJestElectricRunnerNativeImpl`)](../../tests/docs/platform-test-runners.md)
- **Root GenericUI Documentation**: [Root README](../../README.md) · [SourceSet Hierarchy](../../docs/sourcesets.md) · [Backends Guide](../../docs/backends.md) · [Extensions Guide](../../docs/extensions.md) · [Gradle Plugin](../../docs/gradle-plugin.md) · [UI Testing](../../docs/testing.md)
- **Sibling Submodules**: [`:desktop`](../../desktop/README.md) · [`:web`](../../web/README.md) · [`:tests`](../../tests/README.md) · [`:NJS`](../../lib/NJS/README.md)
