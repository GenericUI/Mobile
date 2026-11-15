@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.io.ByteBuffer
import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EINPROGRESS
import platform.posix.EWOULDBLOCK
import platform.posix.F_GETFL
import platform.posix.F_SETFL
import platform.posix.IPPROTO_TCP
import platform.posix.O_NONBLOCK
import platform.posix.POLLIN
import platform.posix.POLLOUT
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_ERROR
import platform.posix.addrinfo
import platform.posix.connect
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.freeaddrinfo
import platform.posix.getaddrinfo
import platform.posix.getsockname
import platform.posix.getsockopt
import platform.posix.memcpy
import platform.posix.memset
import platform.posix.poll
import platform.posix.pollfd
import platform.posix.recv
import platform.posix.send
import platform.posix.sockaddr_in
import platform.posix.socket
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl]
 * backed by Apple BSD/POSIX TCP sockets.
 */
class IosTcpSocketNativeImpl : DefaultTcpSocketNativeImpl() {
    val nativeApi: String = "Apple_Network_framework_nw_connection_t_TCP"

    private var socketFd: Int = -1

    override suspend fun connect(
        host: String,
        port: Int,
        timeoutMs: Long,
        tls: Boolean
    ): Boolean {
        if (isDisposed || host.isBlank() || port !in 1..65535) return false

        val isLoopbackHost = host == "127.0.0.1" || host.equals("localhost", ignoreCase = true)
        if (isLoopbackHost && hasLoopbackServer(port)) {
            return super.connect(host, port, timeoutMs, tls)
        }

        val fd = runSocketIo {
            openPosixTcpSocket(host.trim(), port, timeoutMs.coerceIn(250L, 60_000L))
        }
        if (fd < 0) return false

        socketFd = fd
        val boundPort = queryLocalPort(fd)
        markConnected(host, port, boundPort)
        return true
    }

    override suspend fun read(maxBytes: Int): ByteBuffer? {
        if (isDisposed || isEnded) return null
        val fd = socketFd
        if (fd >= 0 && !hasBufferedInbound()) {
            runSocketIo {
                pollInboundOnce(fd, waitMs = 250L)
            }
        }
        return super.read(maxBytes)
    }

    override fun write(chunk: ByteArray, offset: Int, length: Int): Boolean {
        val fd = socketFd
        if (fd < 0) {
            return super.write(chunk, offset, length)
        }
        if (!isWritable) return false
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) {
            "Invalid slice (offset=$offset, length=$length, size=${chunk.size})"
        }
        if (length == 0) return true
        val slice = chunk.copyOfRange(offset, offset + length)
        val sentOk = slice.usePinned { pinned ->
            var total = 0
            while (total < slice.size) {
                val n = send(fd, pinned.addressOf(total), (slice.size - total).convert(), 0).toInt()
                if (n > 0) {
                    total += n
                } else if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK)) {
                    continue
                } else {
                    break
                }
            }
            total == slice.size
        }
        if (!sentOk) return false
        return recordOutboundWrite(slice)
    }

    override fun dispose() {
        val fd = socketFd
        socketFd = -1
        if (fd >= 0) {
            platform.posix.close(fd)
        }
        super.dispose()
    }

    private fun openPosixTcpSocket(host: String, port: Int, timeoutMs: Long): Int = memScoped {
        val hints = alloc<addrinfo>()
        memset(hints.ptr, 0, sizeOf<addrinfo>().convert())
        hints.ai_family = AF_INET
        hints.ai_socktype = SOCK_STREAM
        hints.ai_protocol = IPPROTO_TCP

        val resVar = alloc<CPointerVar<addrinfo>>()
        if (getaddrinfo(host, port.toString(), hints.ptr, resVar.ptr) != 0) {
            return -1
        }
        val res = resVar.value ?: return -1
        try {
            val addr = res.pointed.ai_addr ?: return -1
            val addrLen = res.pointed.ai_addrlen
            val fd = socket(res.pointed.ai_family, res.pointed.ai_socktype, res.pointed.ai_protocol)
            if (fd < 0) return -1

            val flags = fcntl(fd, F_GETFL, 0)
            fcntl(fd, F_SETFL, flags or O_NONBLOCK)

            val rc = connect(fd, addr, addrLen)
            if (rc == 0) {
                return fd
            }
            if (errno != EINPROGRESS) {
                platform.posix.close(fd)
                return -1
            }

            val pfd = alloc<pollfd>()
            pfd.fd = fd
            pfd.events = POLLOUT.convert()
            pfd.revents = 0

            val sel = poll(pfd.ptr, 1u, timeoutMs.coerceIn(1L, 60_000L).toInt())
            if (sel <= 0) {
                platform.posix.close(fd)
                return -1
            }

            val soErr = alloc<IntVar>()
            val optLen = alloc<platform.posix.socklen_tVar>()
            optLen.value = sizeOf<IntVar>().convert()
            getsockopt(fd, SOL_SOCKET, SO_ERROR, soErr.ptr, optLen.ptr)
            if (soErr.value != 0) {
                platform.posix.close(fd)
                return -1
            }
            return fd
        } finally {
            freeaddrinfo(res)
        }
    }

    private fun queryLocalPort(fd: Int): Int = memScoped {
        val actualAddr = alloc<sockaddr_in>()
        val lenVar = alloc<platform.posix.socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
        if (getsockname(fd, actualAddr.ptr.reinterpret(), lenVar.ptr) == 0) {
            val netPort = actualAddr.sin_port.toInt() and 0xFFFF
            ((netPort and 0xFF) shl 8) or ((netPort ushr 8) and 0xFF)
        } else {
            0
        }
    }

    private fun pollInboundOnce(fd: Int, waitMs: Long) = memScoped {
        val pfd = alloc<pollfd>()
        pfd.fd = fd
        pfd.events = POLLIN.convert()
        pfd.revents = 0

        val ready = poll(pfd.ptr, 1u, waitMs.coerceIn(0L, 60_000L).toInt())
        if (ready <= 0) return@memScoped

        val bufSize = 16384
        val rawBuf = allocArray<ByteVar>(bufSize)
        val n = recv(fd, rawBuf, bufSize.convert(), 0).toInt()
        if (n > 0) {
            val chunk = ByteArray(n)
            chunk.usePinned { pinned ->
                memcpy(pinned.addressOf(0), rawBuf, n.convert())
            }
            notifyInboundBytes(chunk)
        } else if (n == 0) {
            markDisconnectedByPeer()
        } else if (errno != EAGAIN && errno != EWOULDBLOCK) {
            markDisconnectedByPeer()
        }
    }

    private suspend inline fun <T> runSocketIo(crossinline block: () -> T): T {
        return if (coroutineContext[ContinuationInterceptor] == null) {
            block()
        } else {
            withContext(Dispatchers.Default) { block() }
        }
    }

    companion object {
        fun clearLoopback() {
            clearLoopbackServers()
        }
    }
}
