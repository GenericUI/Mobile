package com.nexustools.genericui.android

import android.os.Looper
import com.nexustools.genericui.threading.DefaultUiThreadDispatcherNativeImpl
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl].
 *
 * Separates UI rendering/widget mutation onto the Android Main Looper thread ([Looper.getMainLooper] /
 * [AndroidViewHandle.runOnMainThread]) and executes event handlers and application logic on a dedicated
 * `"GenericUI-Android-Logic"` worker thread.
 */
class AndroidUiThreadDispatcherNativeImpl : DefaultUiThreadDispatcherNativeImpl(
    isSplitThreadingEnabled = true
) {

    @Volatile
    private var logicWorkerThread: Thread? = null

    @Volatile
    private var pumpingUiThread: Thread? = null

    private val blockingUiRequests = ConcurrentLinkedQueue<() -> Unit>()

    private val logicExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, LOGIC_THREAD_NAME).apply {
            isDaemon = true
            logicWorkerThread = this
        }
    }

    private fun isAndroidMainLooperThread(): Boolean {
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return true
        val mainThread = runCatching { mainLooper.thread }.getOrNull() ?: return true
        return mainThread === Thread.currentThread()
    }

    override fun isOnLogicThread(): Boolean {
        if (uiBlockingOverrideDepth > 0) return false
        return Thread.currentThread() === logicWorkerThread || super.isOnLogicThread()
    }

    override fun isOnUiThread(): Boolean {
        if (uiBlockingOverrideDepth > 0) return true
        if (Thread.currentThread() === logicWorkerThread || super.isOnLogicThread()) return false
        return isAndroidMainLooperThread() || super.isOnUiThread()
    }

    override fun onUiTaskQueuedFromLogic() {
        if (pumpingUiThread == null && !isAndroidMainLooperThread()) {
            AndroidViewHandle.runOnMainThread {
                drainUiQueue()
            }
        }
    }

    override fun <T> runOnUiBlocking(block: () -> T): T {
        if (Thread.currentThread() === logicWorkerThread) {
            val activePumper = pumpingUiThread
            if (activePumper != null && activePumper !== Thread.currentThread()) {
                var result: Any? = null
                var failure: Throwable? = null
                val latch = CountDownLatch(1)
                blockingUiRequests.add {
                    try {
                        result = super.runOnUiBlocking(block)
                    } catch (t: Throwable) {
                        failure = t
                    } finally {
                        latch.countDown()
                    }
                }
                latch.await()
                failure?.let { throw it }
                @Suppress("UNCHECKED_CAST")
                return result as T
            }
        }
        return super.runOnUiBlocking(block)
    }

    override fun dispatchToLogic(block: () -> Unit) {
        if (Thread.currentThread() === logicWorkerThread) {
            super.dispatchToLogic(block)
            return
        }
        enqueueLogicTask(block)
        val latch = CountDownLatch(1)
        var logicFailure: Throwable? = null
        val previousPumper = pumpingUiThread
        pumpingUiThread = Thread.currentThread()
        try {
            logicExecutor.execute {
                try {
                    drainLogicQueue()
                } catch (t: Throwable) {
                    logicFailure = t
                } finally {
                    latch.countDown()
                }
            }
            while (!latch.await(1, TimeUnit.MILLISECONDS)) {
                while (true) {
                    val req = blockingUiRequests.poll() ?: break
                    req()
                }
            }
            while (true) {
                val req = blockingUiRequests.poll() ?: break
                req()
            }
        } finally {
            pumpingUiThread = previousPumper
        }
        if (logicExecutionDepth == 0 && uiBlockingOverrideDepth == 0) {
            drainUiQueue()
        }
        logicFailure?.let { throw it }
    }

    companion object {
        const val LOGIC_THREAD_NAME: String = "GenericUI-Android-Logic"
    }
}
