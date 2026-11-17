package com.nexustools.genericui.mobile.system

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Bridges Android `Activity`/`Fragment` and iOS `UIViewController`/`UIScene` lifecycle events
 * and low-memory trim notifications to GenericUI [Fragment]s.
 */
class MobileLifecycleBridge {
    var currentState: MobileLifecycleState = MobileLifecycleState.CREATED
        private set

    private val attachedFragments = linkedSetOf<Fragment>()
    private val lowMemoryListeners = mutableListOf<(trimLevel: Int) -> Unit>()

    fun attachFragment(fragment: Fragment, savedState: FragmentBundle? = null) {
        if (attachedFragments.add(fragment)) {
            fragment.onCreate(savedState)
            if (savedState != null) {
                fragment.onRestoreInstanceState(savedState)
            }
            if (currentState == MobileLifecycleState.STARTED || currentState == MobileLifecycleState.RESUMED_ACTIVE) {
                fragment.onStart()
            }
            if (currentState == MobileLifecycleState.RESUMED_ACTIVE) {
                fragment.onResume()
            }
        }
    }

    fun detachFragment(fragment: Fragment) {
        if (attachedFragments.remove(fragment)) {
            if (currentState == MobileLifecycleState.RESUMED_ACTIVE) {
                fragment.onPause()
            }
            if (currentState == MobileLifecycleState.RESUMED_ACTIVE || currentState == MobileLifecycleState.STARTED || currentState == MobileLifecycleState.PAUSED_INACTIVE) {
                fragment.onStop()
            }
            fragment.onDestroy()
        }
    }

    fun dispatchCreate(savedState: FragmentBundle? = null) {
        currentState = MobileLifecycleState.CREATED
        for (fragment in attachedFragments.toList()) {
            fragment.onCreate(savedState)
            if (savedState != null) {
                fragment.onRestoreInstanceState(savedState)
            }
        }
    }

    fun dispatchStart() {
        currentState = MobileLifecycleState.STARTED
        for (fragment in attachedFragments.toList()) {
            fragment.onStart()
        }
    }

    fun dispatchResume() {
        currentState = MobileLifecycleState.RESUMED_ACTIVE
        for (fragment in attachedFragments.toList()) {
            fragment.onResume()
        }
    }

    fun dispatchPause() {
        currentState = MobileLifecycleState.PAUSED_INACTIVE
        for (fragment in attachedFragments.toList()) {
            fragment.onPause()
        }
    }

    fun dispatchStop() {
        currentState = MobileLifecycleState.STOPPED_BACKGROUND
        for (fragment in attachedFragments.toList()) {
            fragment.onStop()
        }
    }

    fun dispatchDestroy() {
        currentState = MobileLifecycleState.DESTROYED
        for (fragment in attachedFragments.toList()) {
            fragment.onDestroy()
        }
        attachedFragments.clear()
    }

    fun dispatchSaveInstanceState(outState: FragmentBundle) {
        for (fragment in attachedFragments.toList()) {
            fragment.onSaveInstanceState(outState)
        }
    }

    fun dispatchConfigurationChanged(newConfig: WindowConfiguration) {
        for (fragment in attachedFragments.toList()) {
            fragment.onConfigurationChanged(newConfig)
        }
    }

    fun dispatchBackPressed(): Boolean {
        for (fragment in attachedFragments.toList().asReversed()) {
            if (fragment.onBackPressed()) return true
        }
        return false
    }

    fun dispatchLowMemoryWarning(trimLevel: Int = 80) {
        for (listener in lowMemoryListeners.toList()) {
            listener(trimLevel)
        }
    }

    fun addLowMemoryListener(listener: (trimLevel: Int) -> Unit) {
        lowMemoryListeners.add(listener)
    }

    fun removeLowMemoryListener(listener: (trimLevel: Int) -> Unit) {
        lowMemoryListeners.remove(listener)
    }
}
