# `:mobile:android` — Native Android Backend (`androidMain`)

## Overview
`:mobile:android` is the native Android backend for GenericUI, built on standard Android SDK `View` / `ViewGroup` classes, AndroidX `AppCompatActivity`, and Material Components (`MaterialButton`, `MaterialToolbar`, `TabLayout`, `Slider`, `SwitchMaterial`, `DrawerLayout`). It depends on `:common`, `:systemCommon`, and `:mobile:common`.

---

## Generated `AndroidManifest.xml`, 1:1 `AppCompatActivity` Subclasses, & `AndroidLauncher`

Consumer applications write their code strictly in `src/commonMain/kotlin` and `src/commonMain/resources` and configure Android metadata in `build.gradle.kts` via the `genericui` DSL:

```kotlin
genericui {
    applicationId = "com.example.myapp"
    appName = "My App"
    android {
        minSdk = 24
        compileSdk = 34
        targetSdk = 34
        manifest {
            usesPermissions("android.permission.INTERNET", "android.permission.VIBRATE")
            usesFeatures("android.hardware.camera" to false)
            metaData("com.example.API_KEY" to "demo")
        }
        services {
            service(
                name = "com.example.myapp.SyncWorkerService",
                exported = false,
                foregroundServiceType = "dataSync"
            )
        }
    }
}
```

### Build-Time Code & Manifest Generation (`GenericUiGenerateLaunchersTask`)
During the build, `GenericUiGenerateLaunchersTask` scans `src/commonMain/kotlin` for `@MainAppFragment` and `@AppFragment` annotations and generates:
1. **`AndroidManifest.xml`** (`build/generated/genericui/androidManifest/AndroidManifest.xml`):
   - Declares `<uses-permission>`, `<uses-feature>`, and `<meta-data>` entries from `genericui.android.manifest`.
   - Registers `GenericUiGeneratedApplication` (`android:name=".GenericUiGeneratedApplication"`).
   - Declares the `@MainAppFragment`'s generated Activity with `android.intent.action.MAIN` and `android.intent.category.LAUNCHER`.
   - Declares a separate `<activity>` entry for every `@AppFragment`-annotated class.
   - Declares `<service>` entries from `genericui.android.services`.
2. **1:1 `AppCompatActivity` Subclasses** (`build/generated/genericui/launchers/androidMain`):
   - Every `@MainAppFragment` and `@AppFragment` class receives its own concrete `AppCompatActivity` subclass extending `GenericUiFragmentActivity`:
     ```kotlin
     class MainFragmentActivity : GenericUiFragmentActivity() {
         override val fragmentId: String = "main"
         override fun createRootFragment(): Fragment = MainFragment()
     }
     ```
3. **`AndroidActivityRegistry` & Intent-Based `launchFragment` Routing**:
   - `GenericUiGeneratedApplication` extends `GenericUiAndroidApplication` (`com.nexustools.genericui.android.AndroidLauncher`), which initializes the Android runtime in `onCreate()` and registers every `fragmentId -> Activity::class.java` mapping in `AndroidActivityRegistry`.
   - Calling `AppFragmentLauncher.launchFragment(fragmentId, args, windowConfig)` resolves the target `Activity` class from `AndroidActivityRegistry`, serializes `args: FragmentBundle` into JSON via `PreferenceObjectCodec` under `GenericUiFragmentActivity.EXTRA_FRAGMENT_ARGS`, and starts the new Android `Activity` via `Context.startActivity(intent)`.
   - Inside `GenericUiFragmentActivity.onCreate()`, the activity deserializes `EXTRA_FRAGMENT_ARGS`, mounts a `FragmentHostFragment` rooted at `createRootFragment()`, and bridges the Android system Back gesture (`OnBackPressedCallback`) to `FragmentHostFragment.popBackStack()`.

---

## Window Chrome, `TitleCapabilities`, & `AndroidNavbarImpl` (`MaterialToolbar`)

On Android (`AndroidWindowImpl` & `GenericUiFragmentActivity`), `TitleCapabilities` has `isTitleBarRequired = false`, `titleBarRendersAsNavbar = true`, `menuBarRendersAsNavbar = true`, and `menuBarMergesIntoTitleNavbar = true`:
- **No `TitleBar` and no `MenuBar` set**: No top bar is mounted (`MaterialToolbar` is omitted from the root vertical `LinearLayout`), giving the fragment's content view 100% of the activity viewport.
- **`TitleBar` and/or `MenuBar` set**: `GenericUiFragmentActivity` / `AndroidWindowImpl` mounts a top navbar (`MaterialToolbar` / `AndroidNavbarImpl`) at index `0` of the root vertical `LinearLayout`:
  - Renders the `TitleBar` icon, title, subtitle, and custom leading/center/trailing components (marked `mountedInTitleBar = true` so they are never duplicated in the main content view).
  - If a `MenuBar` is also present, merges it into the right side of the same top navbar as a dropdown menu button (`☰`) that opens `PopupMenu` submenus for each top-level `MenuItem`.

---

## Compiled Android `res/` Resources (`AndroidResourceResolverImpl`)

During the Gradle build, `GenericUiCompileResourcesTask` compiles `src/commonMain/resources/` into standard Android `res/` directories (`res/values/strings.xml`, `res/values-<lang>/strings.xml`, `res/values/colors.xml`, `res/values/dimens.xml`, `res/drawable/*`, `res/raw/*`, `res/font/*`).

At runtime, `AndroidResourceResolverImpl` (`com.nexustools.genericui.android.system.AndroidResourceResolverImpl`) resolves resources directly through Android's `Context.resources`:
- `Resources.getIdentifier(name, defType, packageName)`
- `Resources.getString(resId)` (automatically honoring the active Android `Configuration` locale)
- `ContextCompat.getColor(context, resId)` and `Resources.getDimension(resId)`
- `Resources.openRawResource(resId)` and `android.resource://<packageName>/<resId>` URIs.
