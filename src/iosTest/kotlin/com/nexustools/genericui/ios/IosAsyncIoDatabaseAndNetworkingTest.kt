@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleDatabaseNativeImpl
import com.nexustools.genericui.apple.AppleFileReadableNativeImpl
import com.nexustools.genericui.apple.AppleFileWritableNativeImpl
import com.nexustools.genericui.apple.AppleHttpClientNativeImpl
import com.nexustools.genericui.apple.AppleHttpConnectionNativeImpl
import com.nexustools.genericui.apple.AppleHttpRequestNativeImpl
import com.nexustools.genericui.apple.AppleHttpResponseNativeImpl
import com.nexustools.genericui.apple.ApplePreferenceStoreNativeImpl
import com.nexustools.genericui.apple.AppleResourceResolverNativeImpl
import com.nexustools.genericui.apple.AppleTcpSocketNativeImpl
import com.nexustools.genericui.apple.AppleUdpSocketNativeImpl
import com.nexustools.genericui.apple.AppleUserDirectoryNativeImpl
import com.nexustools.genericui.apple.AppleWebSocketNativeImpl
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.nativeimpl.io.DatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DatabaseWritableNativeImpl
import com.nexustools.genericui.net.Network
import com.nexustools.genericui.net.http.HttpClient
import com.nexustools.genericui.net.http.HttpResponse
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.util.CacheDirectory
import com.nexustools.genericui.util.ConfigDirectory
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseSortDirection
import com.nexustools.genericui.util.db.DatabaseTableSchema
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.posix.AF_INET
import platform.posix.QOS_CLASS_USER_INITIATED
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close as posixClose
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.memset
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosAsyncIoDatabaseAndNetworkingTest {

    @Test
    fun testIosFileSystemUserDirectoryPreferencesAndResourceStream() = runBlocking {
        val factory = IosNativeFactory()
        assertEquals(BackendTarget.IOS, factory.backendTarget)
        assertEquals("ios", factory.backendName)
        assertFalse(factory.supportsSystemTray)

        val tray = factory.createSystemTray()
        assertTrue(tray is IosSystemTrayImpl)
        assertFalse(tray.isSupported)
        assertFailsWith<UnsupportedOperationException> { tray.setIcon("icon.png") }
        assertFailsWith<UnsupportedOperationException> { tray.setTooltip("tip") }
        assertFailsWith<UnsupportedOperationException> { tray.setBadgeText("1") }
        assertFailsWith<UnsupportedOperationException> { tray.setContextMenu(null) }
        assertFailsWith<UnsupportedOperationException> { tray.setOnTrayIconClicked {} }

        val perms = factory.createPermissionManager()
        assertTrue(perms is IosPermissionManagerImpl)
        assertEquals(PermissionStatus.NOT_APPLICABLE, perms.checkPermission(PermissionType.SYSTEM_TRAY))

        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.activeFactory = factory
            val fs = factory.createFileSystem()
            assertTrue(fs is IosFileSystemImpl)
            assertEquals(FileSystemAvailability.Always, fs.isAvailable())

            val baseDir = NSURL.fileURLWithPath(NSTemporaryDirectory()).path ?: "/tmp"
            val path = fs.resolve(baseDir, "genericui-ios-test", "readme.txt")
            val writeStream = fs.openWritable(path)
            assertTrue(writeStream is AppleFileWritableNativeImpl)
            writeStream.writeText("iOS Apple FileSystem Content")
            writeStream.end()
            assertEquals("iOS Apple FileSystem Content", fs.read(path)?.toString())

            val readStream = fs.openReadable(path)
            assertTrue(readStream is AppleFileReadableNativeImpl)
            assertEquals("iOS Apple FileSystem Content", readStream.read()?.toString())
            readStream.close()

            val configDirImpl = factory.createConfigDirectory("IosTestSuiteApp", "ui")
            val cacheDirImpl = factory.createCacheDirectory("IosTestSuiteApp", "thumbnails")
            assertTrue(configDirImpl is AppleUserDirectoryNativeImpl)
            assertTrue(cacheDirImpl is AppleUserDirectoryNativeImpl)
            assertTrue(configDirImpl.usesFileSystemBasePath)
            assertTrue(cacheDirImpl.usesFileSystemBasePath)

            val configDir = ConfigDirectory(appName = "IosTestSuiteApp", subdirectory = "ui", factory = factory)
            val cacheDir = CacheDirectory(appName = "IosTestSuiteApp", subdirectory = "thumbnails", factory = factory)
            assertTrue(configDir.write("layout.json", ByteBuffer.fromString("{\"dock\":true}")))
            assertEquals("{\"dock\":true}", configDir.read("layout.json")?.toString())

            val prefs = factory.createPreferenceStore("IosTestSuiteApp")
            assertTrue(prefs is ApplePreferenceStoreNativeImpl)
            prefs.dispose()

            val rawResId = ResourceID(0x7f070222, ResourceType.RAW, "ios_config", "raw/ios_config.json")
            Resources.activeTable.registerRawText(rawResId, "{\"bundle\":\"NSBundle.mainBundle\"}")
            val resolver = factory.createResourceResolver()
            assertTrue(resolver is AppleResourceResolverNativeImpl)
            val resStream = Resources.openStream(rawResId)
            assertNotNull(resStream)
            assertEquals("{\"bundle\":\"NSBundle.mainBundle\"}", resStream.read()?.toString())
            resStream.close()

            fs.unlink(path)
            configDir.clear()
            cacheDir.clear()
            configDir.dispose()
            cacheDir.dispose()
            fs.dispose()
        } finally {
            GenericUI.activeFactory = prevFactory
            FileSystem.resetFallback()
        }
    }

    @Test
    fun testIosDatabaseSqlite3AndStreams() = runBlocking {
        val factory = IosNativeFactory()
        val db = factory.createDatabase("IosCatalog", ":memory:", 2)
        assertTrue(db is AppleDatabaseNativeImpl)

        val schema = DatabaseTableSchema(
            tableName = "widgets",
            primaryKeyColumn = "key",
            columns = listOf(
                DatabaseColumnSpec("key", DatabaseColumnType.STRING, primaryKey = true),
                DatabaseColumnSpec("name", DatabaseColumnType.STRING, nullable = false, indexed = true),
                DatabaseColumnSpec("priority", DatabaseColumnType.INT64, nullable = false, indexed = true)
            )
        )
        assertTrue(db.defineTable(schema))
        assertTrue(
            db.putRecord(
                "widgets",
                DatabaseRecord("w1").withString("name", "UINavigationBar").withLong("priority", 10L)
            )
        )
        assertTrue(
            db.putRecord(
                "widgets",
                DatabaseRecord("w2").withString("name", "UITableView").withLong("priority", 20L)
            )
        )

        val query = DatabaseQuery.from("widgets")
            .where(DatabaseFilter.gte("priority", 10L))
            .orderBy("priority", DatabaseSortDirection.DESCENDING)
        val rows = db.query(query)
        assertEquals(2, rows.size)
        assertEquals("w2", rows.first().key)

        val writable = db.openWritable("blobs", "icon")
        writable.write(byteArrayOf(9, 8, 7))
        writable.end()

        val readable = db.openReadable("blobs", "icon")
        assertNotNull(readable)
        assertEquals(listOf<Byte>(9, 8, 7), readable.read()?.toByteArray()?.toList())
        readable.close()
        db.dispose()
    }

    @Test
    fun testIosNetworkingTcpUdpHttpAndWebSocket() = runBlocking {
        val factory = IosNativeFactory()
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.activeFactory = factory

            val (port, sem) = startLoopbackEchoServer()
            dispatch_semaphore_wait(sem, DISPATCH_TIME_FOREVER)

            val tcp = Network.openTcpSocket()
            assertTrue(tcp is AppleTcpSocketNativeImpl)
            assertTrue(tcp.connect("127.0.0.1", port))
            assertTrue(tcp.writeText("ios-tcp"))
            assertEquals("ios-tcp", tcp.read()?.toString())
            tcp.close()
            tcp.dispose()

            val udpRecv = Network.openUdpSocket()
            val udpSend = Network.openUdpSocket()
            assertTrue(udpRecv is AppleUdpSocketNativeImpl)
            assertTrue(udpRecv.bind("127.0.0.1", 0))
            assertTrue(udpSend.bind("127.0.0.1", 0))
            assertEquals(7, udpSend.send("ios-udp".encodeToByteArray(), "127.0.0.1", udpRecv.localPort))
            assertEquals("ios-udp", udpRecv.receive()?.text)
            udpSend.dispose()
            udpRecv.dispose()

            val httpClient = factory.createHttpClient()
            assertTrue(httpClient is AppleHttpClientNativeImpl)
            val http = HttpClient()
            http.registerRoute("https://ios.example.org/status") { req ->
                HttpResponse(statusCode = 200, statusText = "OK", bodyBytes = "urlsession-ok".encodeToByteArray(), url = req.url)
            }
            val conn = http.get("https://ios.example.org/status")
            assertTrue(conn is AppleHttpConnectionNativeImpl)
            assertTrue(conn.request is AppleHttpRequestNativeImpl)
            assertTrue(conn.response is AppleHttpResponseNativeImpl)
            assertEquals(200, conn.statusCode)
            assertEquals("urlsession-ok", conn.bodyText)
            assertEquals("urlsession-ok", conn.read()?.toString())
            conn.close()
            http.dispose()

            val ws = factory.createWebSocket()
            assertTrue(ws is AppleWebSocketNativeImpl)
            ws.dispose()
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }

    private fun startLoopbackEchoServer(): Pair<Int, platform.darwin.dispatch_semaphore_t> = memScoped {
        val serverFd = socket(AF_INET, SOCK_STREAM, 0)
        assertTrue(serverFd >= 0)
        val reuse = alloc<IntVar>()
        reuse.value = 1
        setsockopt(serverFd, SOL_SOCKET, SO_REUSEADDR, reuse.ptr, sizeOf<IntVar>().toUInt())

        val addr = alloc<sockaddr_in>()
        memset(addr.ptr, 0, sizeOf<sockaddr_in>().toULong())
        addr.sin_len = sizeOf<sockaddr_in>().toUByte()
        addr.sin_family = AF_INET.toUByte()
        addr.sin_addr.s_addr = 0x0100007Fu
        addr.sin_port = 0u
        assertEquals(0, bind(serverFd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().toUInt()))
        assertEquals(0, listen(serverFd, 4))

        val bound = alloc<sockaddr_in>()
        val boundLen = alloc<UIntVar>()
        boundLen.value = sizeOf<sockaddr_in>().toUInt()
        assertEquals(0, getsockname(serverFd, bound.ptr.reinterpret(), boundLen.ptr))
        val rawPort = bound.sin_port.toInt() and 0xFFFF
        val port = ((rawPort and 0xFF) shl 8) or ((rawPort ushr 8) and 0xFF)

        val sem = dispatch_semaphore_create(0)!!
        dispatch_async(dispatch_get_global_queue(QOS_CLASS_USER_INITIATED.toLong(), 0uL)) {
            dispatch_semaphore_signal(sem)
            val clientFd = accept(serverFd, null, null)
            if (clientFd >= 0) {
                memScoped {
                    val buf = allocArray<ByteVar>(1024)
                    val readLen = recv(clientFd, buf, 1024uL, 0)
                    if (readLen > 0) {
                        send(clientFd, buf, readLen.toULong(), 0)
                    }
                }
                posixClose(clientFd)
            }
            posixClose(serverFd)
        }
        port to sem
    }
}
