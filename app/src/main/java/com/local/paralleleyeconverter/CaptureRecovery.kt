package com.local.paralleleyeconverter

/** Monotonic-clock health checks; a stalled stream does not imply consent to a new projection. */
class CaptureRecovery {
    private var active = false
    private var visible = true
    private var lastFrameAt = 0L
    private var lastAttemptAt = 0L

    @Synchronized
    fun start(now: Long) {
        active = true
        visible = true
        lastFrameAt = now // Also covers sessions which never produce their first frame.
        lastAttemptAt = now - RETRY_INTERVAL_MS
    }

    @Synchronized
    fun onFrame(now: Long) {
        if (active && visible) lastFrameAt = now
    }

    @Synchronized
    fun setVisible(isVisible: Boolean, now: Long) {
        if (!active || visible == isVisible) return
        visible = isVisible
        if (isVisible) lastFrameAt = now
    }

    @Synchronized
    fun claimRecovery(now: Long): Boolean {
        if (!active || !visible || now - lastFrameAt < STALL_TIMEOUT_MS ||
            now - lastAttemptAt < RETRY_INTERVAL_MS) return false
        lastAttemptAt = now
        return true
    }

    @Synchronized
    fun onRecoveryAttempt(now: Long) {
        if (active) lastAttemptAt = now
    }

    @Synchronized
    fun stop() {
        active = false
    }

    companion object {
        const val STALL_TIMEOUT_MS = 2200L
        const val RETRY_INTERVAL_MS = 3500L

        fun needsSurfaceReplacement(force: Boolean, sameSize: Boolean, hasDisplay: Boolean, hasReader: Boolean): Boolean =
            force || !sameSize || !hasDisplay || !hasReader
    }
}
