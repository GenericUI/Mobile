package com.nexustools.genericui.android

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.GenericUI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AndroidRealViewsTest {

    @Test
    fun runOnMainThreadAndViewHandleHierarchySyncInHostTests() {
        var executed = false
        AndroidViewHandle.runOnMainThread {
            executed = true
        }
        assertTrue(executed, "AndroidViewHandle.runOnMainThread should execute synchronously in host unit tests")

        val parent = AndroidViewHandle("android.widget.LinearLayout")
        val child1 = AndroidViewHandle("android.widget.TextView")
        val child2 = AndroidViewHandle("android.widget.Button")

        parent.addChild(child1)
        parent.addChild(child2)
        assertEquals(listOf(child1, child2), parent.children)
        assertSame(parent, child1.parentHandle)
        assertSame(parent, child2.parentHandle)

        parent.clearChildren()
        assertTrue(parent.children.isEmpty())
        assertNull(child1.parentHandle)
        assertNull(child2.parentHandle)

        val appImpl = AndroidApplicationImpl()
        var invokeLaterRan = false
        appImpl.invokeLater {
            invokeLaterRan = true
        }
        assertTrue(invokeLaterRan)
        assertTrue(appImpl.pendingTasks.isNotEmpty())
    }

    @Test
    fun reparentingDetachedContainerRealizesAndAttachesGrandchildren() {
        val factory = AndroidNativeFactory()
        GenericUI.initialize(factory)

        val rootStack = VStack()
        val bodyContainer = VStack()
        val setupView = VStack()
        val chipsContainer = VStack()

        setupView.addChild(chipsContainer)
        bodyContainer.addChild(setupView)
        rootStack.addChild(bodyContainer)

        val app = Application(initialContent = rootStack, title = "Reparent Test")
        app.run(factory)

        val rootHandle = rootStack.nativeImpl?.nativeWidget as? AndroidViewHandle
        val bodyHandle = bodyContainer.nativeImpl?.nativeWidget as? AndroidViewHandle
        val setupHandle = setupView.nativeImpl?.nativeWidget as? AndroidViewHandle
        val chipsHandle = chipsContainer.nativeImpl?.nativeWidget as? AndroidViewHandle

        assertNotNull(rootHandle)
        assertNotNull(bodyHandle)
        assertNotNull(setupHandle)
        assertNotNull(chipsHandle)

        // Simulate background polling teardown & rebuild while detached from rootStack
        rootStack.clearChildren()
        assertNull(bodyContainer.parent)

        bodyContainer.clearChildren()
        assertNull(setupView.parent)

        chipsContainer.clearChildren()
        val newChip = Button("A92")
        val newLabel = Text("Normal Priority")
        chipsContainer.addChild(newChip)
        chipsContainer.addChild(newLabel)

        // While detached from rootStack/Window, new grandchildren are not yet realized
        assertNull(newChip.nativeImpl)
        assertNull(newLabel.nativeImpl)

        bodyContainer.addChild(setupView)
        // Re-attach bodyContainer to rootStack (which is connected to Window)
        rootStack.addChild(bodyContainer)

        // Grandchildren added while detached must now be realized and attached to chipsContainer's native handle
        assertNotNull(newChip.nativeImpl, "Grandchild Button added while detached must be realized upon re-attaching ancestor")
        assertNotNull(newLabel.nativeImpl, "Grandchild Text added while detached must be realized upon re-attaching ancestor")
        assertEquals(2, chipsHandle.children.size)
        assertSame(newChip.nativeImpl?.nativeWidget, chipsHandle.children[0])
        assertSame(newLabel.nativeImpl?.nativeWidget, chipsHandle.children[1])

        app.dispose()
    }
}
