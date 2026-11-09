# `:mobile:android` — Native Android Backend (`androidMain`)

## Overview
`:mobile:android` is the native Android backend for GenericUI, built on Android `View` / `ViewGroup`, `AppCompatActivity`, and Material Components.

### Key Highlights
- **Genuine Android / Material System Controls (`BootstrapType.NONE` Default)**: When `getBootstrap().type == BootstrapType.NONE` (the default), views render as 100% unmodified Android / Material controls (`defaultNativeBackground` with `ViewCompat.setBackgroundTintList(view, null)`) with zero Bootstrap CSS styling.
- **Native Drawable Tinting via `ThemeConfig` (`BootstrapType != NONE`)**: Setting `PRIMARY`, `SECONDARY`, `SUCCESS`, `DANGER`, `WARNING`, `INFO`, `LIGHT`, or `DARK` tints the native Android `View` drawable (`ViewCompat.setBackgroundTintList`) using the active `ThemeConfig` / `BootstrapColorPalette` while preserving native Material ripples, elevation, and states (`outline = true/false`).
- **System Backend Typography vs. Aesthetic Style Restraints**: Typography styles (`fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `color`, `lineHeight`, `letterSpacing`, `textAlign`, etc.) are honored on text elements (`Label`, `Text`, `Heading`, `Badge`, `Blockquote`, `CodeBlock`, `MarkdownView`). Aesthetic skinning and font overrides on `ComponentStyle` are **silently ignored** on standard controls/containers (which can only be tinted via `BootstrapType`), while `StyledContainer` and `StyledButton` honor full `ComponentStyle` aesthetics.
- **Automatic Android Material XML Generation (`@style/Theme.GenericUI.App`)**: `GenericUiGenerateAndroidActivitiesTask` automatically generates `res/values/genericui_theme_colors.xml`, `res/values/themes.xml`, and `res/values-night/themes.xml` (`@style/Theme.GenericUI.App`) from `genericui { theme { ... } }` and wires `app.setThemeConfig(...)` in `GeneratedAndroidApplication.onCreate()`.
- **Strict User CSS Class Isolation**: `getClasses()`, `getClass()`, and `hasClass()` return only user-added `customClasses`.
- **1:1 Generated `AppCompatActivity` Subclasses & `AndroidManifest.xml`**: Automatically generated for every `@MainAppFragment` and `@AppFragment` along with `GeneratedAndroidApplication` (`app.addStyleSheet(...)` startup wiring).
- **Live System Theme Detection (`ThemeMode.AUTO`)**: Tracks `UiModeManager` / `Configuration.UI_MODE_NIGHT_MASK` and `AppCompatDelegate.setDefaultNightMode`.

---

## Bare-Metal Native Async I/O, `IFileSystem`, `Database`, & Networking

### 1. Async `Readable`, `Writable`, `ByteBuffer`, `IFileSystem`, & `FileSystemAvailability`
- **`FileSystemAvailability`**:
  - **`FileSystem.isAvailable()` returns `FileSystemAvailability.WithPermission`**: External storage and user-selected document paths require Android runtime permissions (`READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` / media permissions via `AndroidPermissionManagerImpl`) or Storage Access Framework (`ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`, `ACTION_OPEN_DOCUMENT_TREE` via `AndroidFileBrowserDialogImpl` and `ContentResolver`), while app-private sandbox paths are always accessible.
  - **`UserDirectory.isAvailable()` (`ConfigDirectory` & `CacheDirectory`) returns `FileSystemAvailability.Always`**: App-private storage requires zero runtime permission prompts.
- **Non-Blocking Android File & SAF Streams (`FileSystem` & `Readable` / `Writable` / `ByteBuffer`)**:
  - `FileSystem` (`AndroidFileSystemNativeImpl` / `java.nio.channels.FileChannel`, `ContentResolver.openInputStream` / `openOutputStream`, and `DocumentFile` on `Dispatchers.IO`) executes all file reads (`read`, `openReadable`), writes (`write`, `openWritable`), directory listings (`readdir`), `stat`, `mkdir`, `unlink`, `rmdir`, `rename`, and `copyFile` 100% asynchronously off the Android main UI thread using `ByteBuffer`, `Readable`, and `Writable` streams.
- **`UserDirectory` (`ConfigDirectory` & `CacheDirectory`)**:
  - `AndroidUserDirectoryNativeImpl` resolves `ConfigDirectory` (`isCache = false`) to `context.filesDir/<appName>[/<subdirectory>]` and `CacheDirectory` (`isCache = true`) to `context.cacheDir/<appName>[/<subdirectory>]`.
  - Both implement `IFileSystem` (`read`, `write`, `openReadable`, `openWritable`, `openDatabase`) with `usesFileSystemBasePath = true`, delegating async `Readable` / `Writable` / `ByteBuffer` streaming directly to `FileSystem`.

### 2. Native `Database` (`androidx.sqlite:sqlite-ktx`)
- Backed by **`androidx.sqlite:sqlite-ktx`** and Android's native `android.database.sqlite.SQLiteDatabase` / `SQLiteStatement` on `Dispatchers.IO` (`AndroidDatabaseNativeImpl`).
- Translates structured `DatabaseTableSchema`, `DatabaseRecord`, `DatabaseQuery`, and `DatabaseFilter` ASTs via `DatabaseQuerySqlTranslator` into parameterized SQLite statements with `?` positional bind values — requiring **zero string query parsing**.
- Defaults to storing the database file inside `ConfigDirectory` (`context.filesDir/<appName>/<databaseName>.db`), or at any custom path via `Database.setFilePath(path)` or `fileSystem.openDatabase(name, version)`.
- Supports async `defineTable`, `putRecord`, `getRecord`, `query`, `count`, `deleteWhere`, and binary/streamed `ByteBuffer`, `Readable`, and `Writable` values (`read`, `write`, `openReadable`, `openWritable`).

### 3. Async `Network` (`TcpClient`, `UdpClient`), `HttpClient`, `WebSocket` / `SocketIoClient`, & `IApplication.isOffline`
- **Raw TCP & UDP Sockets (`Network.supportsTcp() == true`, `Network.supportsUdp() == true`)**:
  - **`TcpClient`** (`AndroidTcpClientNativeImpl`): Backed by non-blocking `java.nio.channels.SocketChannel` / `SSLSocket` on `Dispatchers.IO`, exposing async `Readable` and `Writable` streams.
  - **`UdpClient`** (`AndroidUdpSocketNativeImpl`): Backed by non-blocking `java.nio.channels.DatagramChannel` / `MulticastSocket` on `Dispatchers.IO` with datagram `bind`, `send`, `receive`, broadcast, and multicast group support.
- **Async `HttpClient` (`AndroidHttpClientNativeImpl`)**:
  - Backed by `HttpURLConnection` / `HttpsURLConnection` on `Dispatchers.IO`, supporting async `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, custom headers, `ByteBuffer` payloads, and streaming `Readable` response bodies (`openStream`).
- **Async `WebSocket` & `SocketIoClient` (`AndroidWebSocketNativeImpl`)**:
  - Backed by non-blocking TLS/TCP RFC 6455 WebSocket framing on `Dispatchers.IO`, powering both raw `WebSocket` connections (text & binary frames) and the Engine.IO v4 / Socket.IO v4 `SocketIoClient` (`connect`, `emit`, `emitWithAck`, `on`, `once`, custom namespaces).
- **`IApplication.isOffline` Connectivity Monitoring (`AndroidNetworkMonitorImpl`)**:
  - Uses `android.net.ConnectivityManager` (`registerDefaultNetworkCallback` / `NetworkCapabilities.NET_CAPABILITY_INTERNET`) to update `app.isOffline` / `app.isOnline` in real time and notify listeners registered via `app.addOfflineListener(...)` and `app.setOnOfflineChanged(...)`.

See [`../docs/android.md`](../docs/android.md) for full documentation.
