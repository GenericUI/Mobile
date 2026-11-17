package com.nexustools.genericui.mobile.system

/**
 * Unified mobile lifecycle states bridging Android `Activity` (`ON_CREATE` .. `ON_DESTROY`)
 * and iOS `UIScene` / `UIViewController` (`willConnect` .. `didDisconnect`).
 */
enum class MobileLifecycleState {
    CREATED,
    STARTED,
    RESUMED_ACTIVE,
    PAUSED_INACTIVE,
    STOPPED_BACKGROUND,
    DESTROYED;

    val isForeground: Boolean
        get() = this == STARTED || this == RESUMED_ACTIVE
}
