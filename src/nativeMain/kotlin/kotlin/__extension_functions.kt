package kotlin

import java.math.BigDecimal

/**
 * Convenience extension functions for easier use in the cross-platform projects.
 * TODO Please feel free to send PR's if you miss any extension functions here.
 */
inline fun Float.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this.toDouble())

inline fun Double.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)

inline fun Int.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this.toLong())

inline fun Long.toBigDecimal(): BigDecimal = BigDecimal.valueOf(this)