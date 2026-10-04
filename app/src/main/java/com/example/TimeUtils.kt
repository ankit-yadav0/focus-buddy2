package com.example

object TimeUtils {
    private const val DAY_MS = 86_400_000L

    /**
     * Days since 1970-01-01 in the device's LOCAL time zone. Daily quotas must roll over at local
     * midnight - dividing the raw epoch milliseconds by a day length gives the UTC day, which for
     * India would reset every quota at 5:30 AM instead.
     */
    fun localEpochDay(nowMs: Long = TrustedClock.now()): Long {
        val offset = java.util.TimeZone.getDefault().getOffset(nowMs)
        return Math.floorDiv(nowMs + offset, DAY_MS)
    }
}
