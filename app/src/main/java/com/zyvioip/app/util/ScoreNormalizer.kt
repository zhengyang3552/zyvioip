package com.zyvioip.app.util

import kotlin.math.roundToInt

/** Convert a score expressed as 0..100 risk or 0..1 risk probability into 0..100 purity. */
fun riskToPurity(value: Number?): Int? {
    val v = value?.toDouble() ?: return null
    if (!v.isFinite()) return null
    val risk = if (v in 0.0..1.0) v * 100.0 else v
    return (100.0 - risk).coerceIn(0.0, 100.0).roundToInt()
}

/** Convert a purity score (0..100) directly, clamped. */
fun purityScore(value: Number?): Int? {
    val v = value?.toDouble() ?: return null
    if (!v.isFinite()) return null
    return v.coerceIn(0.0, 100.0).roundToInt()
}

/** Average only successful, non-null purities; null when there is no usable value. */
fun averagePurity(values: List<Int?>): Int? {
    val valid = values.filterNotNull()
    if (valid.isEmpty()) return null
    return (valid.sum().toDouble() / valid.size).roundToInt().coerceIn(0, 100)
}
