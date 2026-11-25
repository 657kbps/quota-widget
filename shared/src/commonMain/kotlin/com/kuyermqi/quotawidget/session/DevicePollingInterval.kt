package com.kuyermqi.quotawidget.session

/**
 * RFC 8628 device-flow polling interval. Every `slow_down` adds
 * [slowDownIncrementMs] and the widened interval is kept for the rest of the flow.
 */
class DevicePollingInterval(
    initialMs: Long,
    val slowDownIncrementMs: Long = SLOW_DOWN_INCREMENT_MS,
) {
    var currentMs: Long = initialMs
        private set

    fun slowDown() {
        currentMs += slowDownIncrementMs
    }

    companion object {
        const val SLOW_DOWN_INCREMENT_MS = 5_000L
    }
}
