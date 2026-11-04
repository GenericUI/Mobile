package com.nexustools.genericui.ios

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.io.FileSystem
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.nativeimpl.io.DatabaseReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.DatabaseWritableNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl
import com.nexustools.genericui.net.Network
import com.nexustools.genericui.net.http.HttpClient
import com.nexustools.genericui.net.http.HttpResponse
import com.nexustools.genericui.net.socketio.SocketIoClient
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.resources.ResourceType
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.util.CacheDirectory
import com.nexustools.genericui.util.ConfigDirectory
import com.nexustools.genericui.util.db.DatabaseColumnSpec
import com.nexustools.genericui.util.db.DatabaseColumnType
import com.nexustools.genericui.util.db.DatabaseFilter
import com.nexustools.genericui.util.db.DatabaseQuery
import com.nexustools.genericui.util.db.DatabaseRecord
import com.nexustools.genericui.util.db.DatabaseSortDirection
import com.nexustools.genericui.util.db.DatabaseTableSchema
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosAsyncIoDatabaseAndNetworkingTest {

    @AfterTest
    fun tearDown() {
        DefaultTcpSocketNativeImpl.clearLoopbackServers()
        DefaultUdpSocketNativeImpl.clearLoopbackEndpoints()
        DefaultHttpClientNativeImpl.clearStaticRoutes()
    }

    @Test
    fun testIosFileSystemUserDirectoryAndResourceStream() = runSuspend {
        val factory = IosNativeFactory()
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.activeFactory = factory
            val fs = factory.createFileSystem()
            assertTrue(fs is IosFileSystemNativeImpl)
            assertEquals(FileSystemAvailability.WithPermission, fs.isAvailable())

            val path = fs.resolve("/var/mobile/Containers/Data/Application/GenericUI/Documents", "notes", "readme.txt")
            val writeStream = fs.openWritable(path)
            assertTrue(writeStream is IosFileWritableNativeImpl)
            assertEquals("Foundation_NSOutputStream_NSFileHandle_GCD", writeStream.nativeApi)
            writeStream.writeText("iOS NSFileHandle Content")
            writeStream.end()
            assertEquals("iOS NSFileHandle Content", fs.read(path)?.toString())

            val readStream = fs.openReadable(path)
            assertTrue(readStream is IosFileReadableNativeImpl)
            assertEquals("Foundation_NSInputStream_NSFileHandle_GCD", readStream.nativeApi)
            assertEquals("iOS NSFileHandle Content", readStream.read()?.toString())
            readStream.close()

            val configDirImpl = factory.createConfigDirectory("IosTestSuiteApp", "ui")
            val cacheDirImpl = factory.createCacheDirectory("IosTestSuiteApp", "thumbnails")
            assertTrue(configDirImpl.usesFileSystemBasePath)
            assertTrue(cacheDirImpl.usesFileSystemBasePath)

            val configDir = ConfigDirectory(appName = "IosTestSuiteApp", subdirectory = "ui", factory = factory)
            val cacheDir = CacheDirectory(appName = "IosTestSuiteApp", subdirectory = "thumbnails", factory = factory)
            assertTrue(configDir.write("layout.json", ByteBuffer.fromString("{\"dock\":true}")))
            assertEquals("{\"dock\":true}", configDir.read("layout.json")?.toString())

            val rawResId = ResourceID(0x7f070222, ResourceType.RAW, "ios_config", "raw/ios_config.json")
            Resources.activeTable.registerRawText(rawResId, "{\"bundle\":\"NSBundle.mainBundle\"}")
            val resolver = factory.createResourceResolver()
            assertTrue(resolver is IosResourceResolverImpl)
            val resStream = Resources.openStream(rawResId)
            assertTrue(resStream is IosFileReadableNativeImpl)
            assertEquals("{\"bundle\":\"NSBundle.mainBundle\"}", resStream.read()?.toString())
            resStream.close()

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
    fun testIosDatabaseSqlite3AndStreams() = runSuspend {
        val factory = IosNativeFactory()
        val db = factory.createDatabase("IosCatalog", "/tmp/ios_catalog.db", 2)
        assertTrue(db is IosDatabaseNativeImpl)
        assertEquals("iOS_libsqlite3_GCD", db.nativeEngine)

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
                DatabaseRecord("w2").withString("name", "MTKView").withLong("priority", 20L)
            )
        )

        val query = DatabaseQuery.from("widgets")
            .where(DatabaseFilter.gte("priority", 10L))
            .orderBy("priority", DatabaseSortDirection.DESCENDING)
        val rows = db.query(query)
        assertEquals(2, rows.size)
        assertEquals("w2", rows.first().key)

        val writable = db.openWritable("blobs", "icon")
        assertTrue(writable is DatabaseWritableNativeImpl)
        writable.write(byteArrayOf(9, 8, 7))
        writable.end()

        val readable = db.openReadable("blobs", "icon")
        assertTrue(readable is DatabaseReadableNativeImpl)
        assertEquals(listOf<Byte>(9, 8, 7), readable.read()?.toByteArray()?.toList())
        readable.close()
        db.dispose()
    }

    @Test
    fun testIosNetworkingTcpUdpHttpAndWebSocket() = runSuspend {
        val factory = IosNativeFactory()
        val prevFactory = GenericUI.activeFactory
        try {
            GenericUI.activeFactory = factory

            DefaultTcpSocketNativeImpl.registerLoopbackServer(9201) { payload -> payload }
            val tcp = Network.openTcpSocket()
            assertTrue(tcp is IosTcpSocketNativeImpl)
            assertTrue(tcp.connect("127.0.0.1", 9201))
            assertTrue(tcp.writeText("ios-tcp"))
            assertEquals("ios-tcp", tcp.read()?.toString())
            tcp.close()
            tcp.dispose()

            val udpRecv = Network.openUdpSocket()
            val udpSend = Network.openUdpSocket()
            assertTrue(udpRecv is IosUdpSocketNativeImpl)
            assertTrue(udpRecv.bind("127.0.0.1", 0))
            assertTrue(udpSend.bind("127.0.0.1", 0))
            assertEquals(7, udpSend.send("ios-udp".encodeToByteArray(), "127.0.0.1", udpRecv.localPort))
            assertEquals("ios-udp", udpRecv.receive()?.text)
            udpSend.dispose()
            udpRecv.dispose()

            val http = HttpClient()
            http.registerRoute("https://ios.example.org/status") { req ->
                HttpResponse(statusCode = 200, statusText = "OK", bodyBytes = "urlsession-ok".encodeToByteArray(), url = req.url)
            }
            val conn = http.get("https://ios.example.org/status")
            assertTrue(conn is IosHttpConnectionNativeImpl)
            assertTrue(conn.request is IosHttpRequestNativeImpl)
            assertTrue(conn.response is IosHttpResponseNativeImpl)
            assertEquals(200, conn.statusCode)
            assertEquals("urlsession-ok", conn.bodyText)
            assertEquals("urlsession-ok", conn.read()?.toString())
            conn.close()
            http.dispose()

            val ws = factory.createWebSocket()
            assertTrue(ws is IosWebSocketNativeImpl)
            ws.dispose()

            val sio = SocketIoClient("http://localhost:4000")
            assertTrue(sio.connect())
            assertTrue(sio.isConnected)
            assertEquals(listOf("ok"), sio.emitWithAck("ping", "ios"))
            sio.disconnect()
            sio.dispose()
        } finally {
            GenericUI.activeFactory = prevFactory
        }
    }

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        var completed = false
        block.startCoroutine(
            Continuation(EmptyCoroutineContext) { result ->
                completed = true
                failure = result.exceptionOrNull()
            }
        )
        failure?.let { throw it }
        assertTrue(completed)
    }
}
