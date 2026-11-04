# `:mobile:ios` — Native iOS UIKit Backend (`iosX64`, `iosArm64`, `iosSimulatorArm64`)

## Overview
`:mobile:ios` is the native iOS UIKit backend for GenericUI (`iosX64`, `iosArm64`, `iosSimulatorArm64`).

### Key Highlights
- **Genuine UIKit System Controls (`BootstrapType.NONE` Default)**: When `getBootstrap().type == BootstrapType.NONE` (the default), controls render as untinted native UIKit `UIView` / `UIControl` widgets with zero Bootstrap CSS styling.
- **Semantic `BootstrapType` Tinting**: Setting `DANGER`, `PRIMARY`, `SUCCESS`, `WARNING`, `INFO`, `SECONDARY`, `LIGHT`, `DARK`, or `LINK` tints the native UIKit control with the semantic color (`outline = true/false`). Unsupported CSS properties and transitions are silently ignored.
- **Strict User CSS Class Isolation**: `getClasses()`, `getClass()`, and `hasClass()` return only user-added `customClasses`.
- **Live System Theme Detection (`ThemeMode.AUTO`)**: Tracks `UITraitCollection.currentTraitCollection.userInterfaceStyle` (`UIUserInterfaceStyleDark` vs. `UIUserInterfaceStyleLight`) and updates `UIWindow.overrideUserInterfaceStyle`.

---

## Bare-Metal Native Async I/O, `IFileSystem`, `Database`, & Networking

### 1. Async `Readable`, `Writable`, `ByteBuffer`, `IFileSystem`, & `FileSystemAvailability`
- **`FileSystemAvailability`**:
  - **`FileSystem.isAvailable()` returns `FileSystemAvailability.WithPermission`**: External files and directories outside the iOS app container require user selection via `UIDocumentPickerViewController` (`IosFileBrowserDialogImpl`) with security-scoped URL access (`startAccessingSecurityScopedResource` / `stopAccessingSecurityScopedResource`), while app-sandbox paths are always accessible.
  - **`UserDirectory.isAvailable()` (`ConfigDirectory` & `CacheDirectory`) returns `FileSystemAvailability.Always`**: App-sandbox directories require zero user permission prompts.
- **Non-Blocking Foundation / POSIX File Streams (`FileSystem` & `Readable` / `Writable` / `ByteBuffer`)**:
  - `FileSystem` (`IosFileSystemNativeImpl` / Foundation `NSFileManager`, `NSFileHandle`, Grand Central Dispatch `dispatch_io_t`, and POSIX async I/O) executes all file reads (`read`, `openReadable`), writes (`write`, `openWritable`), directory listings (`readdir`), `stat`, `mkdir`, `unlink`, `rmdir`, `rename`, and `copyFile` 100% asynchronously off the UIKit main thread using `ByteBuffer`, `Readable`, and `Writable` streams.
- **`UserDirectory` (`ConfigDirectory` & `CacheDirectory`)**:
  - `IosUserDirectoryNativeImpl` resolves `ConfigDirectory` (`isCache = false`) to `<Home>/Library/Application Support/<appName>[/<subdirectory>]` and `CacheDirectory` (`isCache = true`) to `<Home>/Library/Caches/<appName>[/<subdirectory>]`.
  - Both implement `IFileSystem` (`read`, `write`, `openReadable`, `openWritable`, `openDatabase`) with `usesFileSystemBasePath = true`, delegating async `Readable` / `Writable` / `ByteBuffer` streaming directly to `FileSystem`.

### 2. Native `Database` (`libsqlite3`)
- Backed directly by iOS system **`libsqlite3`** (`sqlite3_open_v2`, `sqlite3_prepare_v2`, `sqlite3_bind_*`, `sqlite3_step`, `sqlite3_finalize`, `sqlite3_close_v2`) in `IosDatabaseNativeImpl`.
- Translates structured `DatabaseTableSchema`, `DatabaseRecord`, `DatabaseQuery`, and `DatabaseFilter` ASTs via `DatabaseQuerySqlTranslator` into parameterized SQLite statements with `?` positional bind values — requiring **zero string query parsing**.
- Defaults to storing the database file inside `ConfigDirectory` (`<Home>/Library/Application Support/<appName>/<databaseName>.db`), or at any custom path via `Database.setFilePath(path)` or `fileSystem.openDatabase(name, version)`.
- Supports async `defineTable`, `putRecord`, `getRecord`, `query`, `count`, `deleteWhere`, and binary/streamed `ByteBuffer`, `Readable`, and `Writable` values (`read`, `write`, `openReadable`, `openWritable`).

### 3. Async `Network` (`TcpClient`, `UdpClient`), `HttpClient`, `WebSocket` / `SocketIoClient`, & `IApplication.isOffline`
- **Raw TCP & UDP Sockets (`Network.supportsTcp() == true`, `Network.supportsUdp() == true`)**:
  - **`TcpClient`** (`IosTcpClientNativeImpl`): Backed by Apple `Network.framework` (`nw_connection_t` / `NWConnection` with TLS parameters when `tls = true`) and non-blocking Darwin BSD sockets, exposing async `Readable` and `Writable` streams.
  - **`UdpClient`** (`IosUdpSocketNativeImpl`): Backed by `Network.framework` / non-blocking Darwin BSD `SOCK_DGRAM` sockets with datagram `bind`, `sendto`, `recvfrom`, broadcast (`SO_BROADCAST`), and multicast group join/leave (`IP_ADD_MEMBERSHIP` / `IP_DROP_MEMBERSHIP`).
- **Async `HttpClient` (`IosHttpClientNativeImpl`)**:
  - Backed by Foundation `NSURLSession`, `NSMutableURLRequest`, and `NSURLSessionDataTask`, supporting async `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`, custom headers, `ByteBuffer` payloads, and streaming `Readable` response bodies (`openStream`).
- **Async `WebSocket` & `SocketIoClient` (`IosWebSocketNativeImpl`)**:
  - Backed by `NSURLSessionWebSocketTask` (`NSURLSessionWebSocketMessage`), powering both raw `WebSocket` connections (text & binary frames) and the Engine.IO v4 / Socket.IO v4 `SocketIoClient` (`connect`, `emit`, `emitWithAck`, `on`, `once`, custom namespaces).
- **`IApplication.isOffline` Connectivity Monitoring (`IosNetworkMonitorImpl`)**:
  - Uses Apple `Network.framework` `NWPathMonitor` (`nw_path_monitor_t` / `nw_path_get_status(path) == nw_path_status_satisfied`) to update `app.isOffline` / `app.isOnline` in real time and notify listeners registered via `app.addOfflineListener(...)` and `app.setOnOfflineChanged(...)`.

See [`../docs/ios.md`](../docs/ios.md) for full documentation.
