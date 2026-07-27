package com.example.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Returns a stable per-day key (e.g. "2026-07-27") used to detect when a daily usage
 * allowance should reset, in the device's local timezone. */
fun todayUsageDateKey(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
