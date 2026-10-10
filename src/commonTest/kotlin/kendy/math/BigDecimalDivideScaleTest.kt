package kendy.math

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Divisions whose scale difference is too large for the primitive long fast
 * path, so that the dividend or the divisor has to be scaled as a BigInteger.
 */
class BigDecimalDivideScaleTest {
    @Test
    fun scalesDividendForLargeResultScale() {
        assertEquals(
            "0.33333333333333333333",
            BigDecimal("1").divide(BigDecimal("3"), 20, RoundingMode.HALF_UP).toString()
        )
    }

    @Test
    fun scalesDivisorForLargeDividendScale() {
        assertEquals(
            "0.25",
            BigDecimal("1.000000000000000000000000000000").divide(BigDecimal("4"), 2, RoundingMode.HALF_UP).toString()
        )
    }
}
