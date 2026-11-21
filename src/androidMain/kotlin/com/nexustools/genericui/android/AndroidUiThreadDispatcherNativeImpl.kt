package com.nexustools.genericui.android

import android.os.Looper
import com.nexustools.genericui.jvm.JvmUiThreadDispatcherBase
import java.util.concurrent.CountDownLatch

/**
 * Android implementation of [com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl]
 * backed by [JvmUiThreadDispatcherBase], dispatching UI work onto Android's Main `Looper` thread
 * and running application/event logic on `"GenericUI-Android-Logic"`.
 */
class AndroidUiThreadDispatcherNativeImpl : JvmUiThreadDispatcherBase(
    logicThreadName = LOGIC_THREAD_NAME
) {
    override fun onLogicThreadStarted() {
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
    }

    private fun mainLooperThread(): Thread? =
        runCatching { Looper.getMainLooper()?.thread }.getOrNull()

    override fun isOnToolkitMainThread(): Boolean {
        val mainThread = mainLooperThread() ?: return true
        return mainThread === Thread.currentThread()
    }

    override fun canPostToToolkitUiThread(): Boolean =
        mainLooperThread() != null

    override fun postToToolkitUiThread(task: () -> Unit) {
        AndroidViewHandle.runOnMainThread(task)
    }

    override fun runOnToolkitUiThreadSync(action: () -> Unit): Boolean {
        val mainThread = mainLooperThread() ?: return false
        if (mainThread === Thread.currentThread()) {
            action()
            return true
        }
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        AndroidViewHandle.runOnMainThread {
            try {
                action()
            } catch (t: Throwable) {
                failure = t
            } finally {
                latch.countDown()
            }
        }
        latch.await()
        failure?.let { throw it }
        return true
    }

    companion object {
        const val LOGIC_THREAD_NAME: String = "GenericUI-Android-Logic"
    }
}
