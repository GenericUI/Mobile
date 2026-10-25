# `:mobile:android` — Native Android Backend

> **Parent Submodule**: [`:mobile` (`../README.md`)](../README.md) | **Root Repository**: [**GenericUI (`../../README.md`)**](../../README.md)  
> **Root Docs**: [SourceSet Hierarchy](../../docs/sourcesets.md) · [Backends Guide](../../docs/backends.md) · [Extensions Guide](../../docs/extensions.md) · [Gradle Plugin](../../docs/gradle-plugin.md) · [UI Testing](../../docs/testing.md)  
> **Sibling Mobile Modules**: [`:mobile:shared`](./shared.md) · [`:mobile:ios`](./ios.md)

The **`:mobile:android`** module (`com.nexustools.genericui.android.*`) is the native Android SDK (API 24+) backend for **GenericUI**. Built directly on native `android.view.View` controls, Material Components (`MaterialToolbar`, `BottomNavigationView`), AndroidX `RecyclerView`, Storage Access Framework (`Intent.ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`), and [`:mobile:shared`](./shared.md), it provides 1:1 compatibility between GenericUI `Fragment`s and Android `Activity`s.

---

## Core Architecture & Native Capabilities

1. **1:1 Android `Activity`-Compatible `GenericUiFragmentActivity`**:
   - `GenericUiFragmentActivity` (`androidx.appcompat.app.AppCompatActivity`) hosts any GenericUI `Fragment` (including `AdaptiveSplitTabFragment` and `DockWorkspaceFragment`) 1:1 as a native Android `Activity`.
   - Wires the complete Android `Activity` lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onSaveInstanceState`, `onDestroy`, `onConfigurationChanged`, `onWindowFocusChanged`, `onPictureInPictureModeChanged`) through `MobileLifecycleBridge` and `MobileAdaptiveFragmentCoordinator`.
   - **Automatic Chrome Synchronization (`syncFragmentChrome`)**:
     - `fragment.setNavBar(titleBar)` $\longrightarrow$ renders and populates a native **`com.google.android.material.appbar.MaterialToolbar`**.
     - `fragment.setBottomNavigationBar(bottomNav)` $\longrightarrow$ renders and populates a native **`com.google.android.material.bottomnavigation.BottomNavigationView`**.
     - `fragment.setFullscreen(true)` $\longrightarrow$ hides system status/navigation bars via `WindowInsetsControllerCompat` (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) and configures `layoutInDisplayCutoutMode` (`SHORT_EDGES`, `NEVER`, `DEFAULT`).
     - `enterFragmentPictureInPicture(PipConfig)` $\longrightarrow$ enters Android Picture-in-Picture mode (`PictureInPictureParams.Builder().setAspectRatio(...)`) via `MobilePipController`.
2. **True Lazy Loading & Unloading with `RecyclerView` + `MobileLazyViewportPool` (`AndroidLazyListImpl`, `AndroidLazyGridImpl`, `AndroidTreeViewImpl`)**:
   - Backed by AndroidX `RecyclerView` (`LinearLayoutManager` / `GridLayoutManager`) synchronized with `:mobile:shared`'s `MobileLazyViewportPool`.
   - Only items inside the visible viewport window `[firstVisibleIndex .. lastVisibleIndex]` are bound (`onBind`), while items scrolling out of view are immediately unbound (`onUnbind`) and recycled into the scrap pool or disposed on `onTrimMemory`.
3. **Storage Access Framework File & Folder Picker (`AndroidFileBrowserDialogImpl`)**:
   - Uses Android's native DocumentsUI / Storage Access Framework (`Intent`) launched from `GenericUiFragmentActivity` and routed via `AndroidFileBrowserDialogImpl.dispatchActivityResult`:
     - **`FileBrowserMode.OPEN_SINGLE_FILE`**: `Intent.ACTION_OPEN_DOCUMENT` (`CATEGORY_OPENABLE` + `EXTRA_MIME_TYPES`).
     - **`FileBrowserMode.OPEN_MULTIPLE_FILES`**: `Intent.ACTION_OPEN_DOCUMENT` + `Intent.EXTRA_ALLOW_MULTIPLE = true`.
     - **`FileBrowserMode.SAVE_FILE`**: `Intent.ACTION_CREATE_DOCUMENT` + `Intent.EXTRA_TITLE` (`defaultFileName`).
     - **`FileBrowserMode.SELECT_FOLDER`**: `Intent.ACTION_OPEN_DOCUMENT_TREE` (grants persistent directory tree access).
4. **Hardware-Accelerated 2D & 3D Canvases (`Android Canvas`, `EGL/GLES3`, `Vulkan`)**:
   - **`Canvas2D` (`:extensions:canvas2d:mobile:android`)**: Hardware-accelerated `android.graphics.Canvas` surface synchronized to `Choreographer.getInstance().postFrameCallback` providing `deltaMs` VBlank callbacks.
   - **`GLCanvas` (`:extensions:canvas3d:mobile:android` + `:extensions:gpu`)**: Native `SurfaceView` / `TextureView` with **EGL 1.5 & OpenGL ES 3.0 / 3.1** contexts.
   - **`VKCanvas` (`:extensions:canvas3d:mobile:android` + `:extensions:gpu`)**: Native **Vulkan 1.1 / 1.2+** (`VK_KHR_android_surface` / `ANativeWindow`) hardware rendering surface.
5. **Native Android Image Codec (`AndroidBitmapImageCodecImpl`)**:
   - Uses `android.graphics.BitmapFactory`, `ImageDecoder`, and `Bitmap.compress` to decode and encode `PNG`, `JPEG`, `WEBP`, and `GIF` directly into `:common`'s `ImageBuffer`.

---

## Key Classes Reference

| Class / Object | Responsibility |
| :--- | :--- |
| `AndroidNativeFactory` | `NativeBackendFactory` creating Android `View` peers and registering `AndroidBitmapImageCodecImpl`. |
| `GenericUiFragmentActivity` | Abstract `AppCompatActivity` hosting a GenericUI `Fragment` 1:1 with `MaterialToolbar`, `BottomNavigationView`, fullscreen cutout control, PiP, and SAF result dispatch. |
| `AndroidViewHandle` | Wraps native `android.view.View` instances, layout parameters, and active `Context`. |
| `AndroidBaseNativeImpl<W>` | Base `NativeImpl<W>` applying Dual-Mode Bootstrap 5 enums and compiled SASS/CSS `ComputedStyle` rules to Android `View` backgrounds (`GradientDrawable`), elevation, padding, and touch listeners. |
| `AndroidBaseContainerNativeImpl<W>` | Manages Android `ViewGroup` child attachment and Flexbox-compatible layout measurement. |
| `AndroidFileBrowserDialogImpl` | Native SAF file/folder picker (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE`). |
| `AndroidLazyListImpl`, `AndroidLazyGridImpl`, `AndroidTreeViewImpl` | True lazy views backed by `RecyclerView` and `MobileLazyViewportPool`. |
| `AndroidBitmapImageCodecImpl` | `BitmapFactory` / `Bitmap` implementation of `:common`'s `ImageCodecNativeImpl`. |
| `AndroidNotificationManagerImpl` | Android `NotificationManagerCompat` + `NotificationChannel` dispatcher. |
| `AndroidPermissionManagerImpl` | Runtime Android permissions (`ContextCompat.checkSelfPermission` / `ActivityCompat.requestPermissions`) manager. |
| `AndroidNetworkMonitorImpl` | `ConnectivityManager.NetworkCallback` observer. |

---

## SourceSet Hierarchy (`systemCommon` $\rightarrow$ `mobileCommon` $\rightarrow$ `android` / `androidMain`)

```
commonMain / commonTest
 └── systemCommonMain / systemCommonTest
      └── mobileCommonMain / mobileCommonTest (:mobile:shared)
           ├── androidMain / androidUnitTest (:mobile:android)
           └── iosMain / iosTest (:mobile:ios)
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
        android()
    }

    application {
        appName = "Episode Caster"
        appId = "com.nexustools.episodecaster"
        version = "1.0.0"
    }

    extensions {
        canvas2d()
        canvas3d()
        media()
        webView() // Uses native android.webkit.WebView (never NJS)
    }
}
```

### Android Gradle Tasks

| Task Name | Description |
| :--- | :--- |
| `./gradlew androidDevelopment` | Compiles the debug Android build (`assembleDebug`) with `:mobile:android` and `:mobile:shared`. |
| `./gradlew androidProduction` | Compiles the release Android APK / AAB (`assembleRelease` / `bundleRelease`). |
| `./gradlew testDebugUnitTest` | Runs `JestElectricRunner` UI tests on the JVM using `AndroidRobolectricRunnerNativeImpl` and `JestElectricRobolectricTestBase`. |

---

## Code Examples

### 1. Hosting a GenericUI `Fragment` in `GenericUiFragmentActivity`

```kotlin
package com.example.episodecaster.android

import com.nexustools.genericui.android.GenericUiFragmentActivity
import com.nexustools.genericui.component.action.ActionItem
import com.nexustools.genericui.component.action.TitleBar
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.helper.AdaptiveSplitTabFragment

class EpisodeCasterActivity : GenericUiFragmentActivity() {
    override fun createFragment(): Fragment = EpisodeCasterMainFragment()
}

class EpisodeCasterMainFragment : AdaptiveSplitTabFragment() {
    override fun onConfigureAdaptiveLayout() {
        // Automatically populates GenericUiFragmentActivity's MaterialToolbar:
        setNavBar(
            TitleBar(
                title = "Episode Caster",
                subtitle = "Subscriptions & Offline Queue",
                actions = listOf(
                    ActionItem(id = "cast.connect", label = "Cast") {},
                    ActionItem(id = "folder.import", label = "Import Folder") {}
                )
            )
        )
        // Automatically switches to fullscreen immersive mode when phone is rotated to landscape:
        phoneLandscapeFullscreen = true
    }
}
```

### 2. Selecting a Directory Tree or Multiple Files via `AndroidFileBrowserDialogImpl` (SAF)

```kotlin
import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserDialog
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode

fun selectAndroidMediaFolder(dialog: FileBrowserDialog) {
    // Launches Intent.ACTION_OPEN_DOCUMENT_TREE on GenericUiFragmentActivity:
    dialog.show(
        request = FileBrowserRequest(
            title = "Select Podcast Downloads Folder",
            mode = FileBrowserMode.SELECT_FOLDER
        ),
        callback = object : FileBrowserCallback {
            override fun onFilesSelected(files: List<SelectedFile>, activeFilter: FileTypeFilter?) {
                val treeUri = files.first().pathOrUri
                println("Granted Android SAF Tree URI: $treeUri")
            }

            override fun onCancelled() {
                println("User cancelled SAF folder picker.")
            }
        }
    )
}
```

### 3. Testing Android UI with `JestElectricRobolectricTestBase`

See [Platform Test Runners (`../../tests/docs/platform-test-runners.md`)](../../tests/docs/platform-test-runners.md) for complete details:

```kotlin
import com.nexustools.genericui.tests.android.JestElectricRobolectricTestBase
import org.junit.Test

class EpisodeCasterAndroidTest : JestElectricRobolectricTestBase() {
    @Test
    fun testFragmentNavBarAndButtons() = describeAndroid("Episode Caster Android UI") {
        it("mounts AdaptiveSplitTabFragment and verifies MaterialToolbar title") {
            val fragController = launchFragment { EpisodeCasterMainFragment() }
            expect(fragController.fragment.getNavBar()?.title).toBe("Episode Caster")
        }
    }
}
```

---

## Related Documentation

- **Parent Mobile Overview**: [`../README.md`](../README.md)
- **Sibling Mobile Docs**: [`:mobile:shared`](./shared.md) · [`:mobile:ios`](./ios.md)
- **Android Robolectric UI Testing**: [Platform Test Runners (`AndroidRobolectricRunnerNativeImpl`)](../../tests/docs/platform-test-runners.md)
- **Root GenericUI Documentation**: [Root README](../../README.md) · [SourceSet Hierarchy](../../docs/sourcesets.md) · [Backends Guide](../../docs/backends.md) · [Extensions Guide](../../docs/extensions.md) · [Gradle Plugin](../../docs/gradle-plugin.md) · [UI Testing](../../docs/testing.md)
- **Sibling Submodules**: [`:desktop`](../../desktop/README.md) · [`:web`](../../web/README.md) · [`:tests`](../../tests/README.md) · [`:NJS`](../../lib/NJS/README.md)
