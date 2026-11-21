# `:mobile` Shared Mobile Documentation Index (`mobileCommon` + `:jvm` / `:apple`)

> **Module Guide**: [**`:mobile` (`../README.md`)**](../README.md)

Mobile shared code spans three complementary layers:

1. **[`common.md`](common.md) (`:mobile` `commonMain` / `mobileCommon`)**: Pure-Kotlin adaptive form-factor breakpoints (`MobileDeviceFormFactor`, `MobileAdaptiveFragmentCoordinator`, `MobileLayoutFactory`), safe-area/posture tracking, touch gesture & fling physics (`MobileGestureRecognizer`, `MobileFlingPhysics`), `MobileLifecycleBridge`, `MobileDocumentPickerBridge`, and `MobileLazyViewportPool`.
2. **[`:jvm`](../../docs/jvm.md) (`jvmSharedMain`, shared with Swing)**: Reused directly by [**`Android`** (`android.md`)](android.md) for async I/O (`JvmFileSystemBase`), SQLite AST (`JvmDatabaseBase`), HTTP/WebSockets (`JvmHttpUrlConnectionClient`, `JvmRfc6455WebSocketNativeImpl`), TCP/UDP sockets (`JvmTcpSocketNativeImpl`, `JvmUdpSocketNativeImpl`), JNI loading (`JniLibraryLoader`), and dispatcher/tint pixel utilities (`JvmUiThreadDispatcherBase`, `JvmControlTintPixelUtil`).
3. **[`:apple`](../../docs/apple.md) (`appleMain`, shared with macOS)**: Reused directly by [**`IOS`** (`ios.md`)](ios.md) and [**`IPadOS`** (`../../docs/ipados.md`)](../../docs/ipados.md) for `NSBundle` resources (`AppleResourceResolverNativeImpl`), CoreGraphics SVG & `ImageIO` codecs, `Network.framework` / `NSURLSession`, `libsqlite3`, `UserNotifications`, binary `.plist` preferences, and GCD dispatching.
