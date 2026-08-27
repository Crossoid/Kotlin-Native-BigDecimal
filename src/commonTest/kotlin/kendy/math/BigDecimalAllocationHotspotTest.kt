package kendy.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BigDecimalAllocationHotspotTest {
    @Test
    fun precisionIsExactAroundLargePowersOfTen() {
        val tenTo1000 = BigInteger.TEN.pow(1000)

        assertEquals(1000, BigDecimal(tenTo1000.subtract(BigInteger.ONE)).precision())
        assertEquals(1001, BigDecimal(tenTo1000).precision())
        assertEquals(1001, BigDecimal(tenTo1000.negate()).precision())
        assertEquals(1001, BigDecimal(tenTo1000.add(BigInteger.ONE)).precision())
    }

    @Test
    fun precisionHandlesLargeBinaryValuesAndSigns() {
        val twoTo2048 = BigInteger.ONE.shiftLeft(2048)

        assertEquals(617, BigDecimal(twoTo2048).precision())
        assertEquals(617, BigDecimal(twoTo2048.negate()).precision())
    }

    @Test
    fun precisionIsExactAtCachedPowerBoundary() {
        val tenTo255 = BigInteger.TEN.pow(255)
        val tenTo256 = tenTo255.multiply(BigInteger.TEN)

        assertEquals(256, BigDecimal(tenTo255).precision())
        assertEquals(257, BigDecimal(tenTo256).precision())
    }

    @Test
    fun stripTrailingZerosHandlesLargeChunksAndSigns() {
        val tenTo1000 = BigInteger.TEN.pow(1000)

        assertStripped(BigInteger.ONE, -1000, BigDecimal(tenTo1000).stripTrailingZeros())
        assertStripped(BigInteger.ONE.negate(), -1000, BigDecimal(tenTo1000.negate()).stripTrailingZeros())
    }

    @Test
    fun stripTrailingZerosPreservesScaleAndCanonicalZero() {
        assertStripped(BigInteger("12345"), 2, BigDecimal(BigInteger("123450000"), 6).stripTrailingZeros())
        assertStripped(BigInteger("123456789"), 6, BigDecimal(BigInteger("123456789"), 6).stripTrailingZeros())
        assertStripped(BigInteger.ZERO, 0, BigDecimal(BigInteger.ZERO, 1000).stripTrailingZeros())
    }

    @Test
    fun stripTrailingZerosKeepsSmallValuesAndSignsExact() {
        assertStripped(BigInteger("12345"), 2, BigDecimal.valueOf(123450000L, 6).stripTrailingZeros())
        assertStripped(
            BigInteger.valueOf(-9),
            -11,
            BigDecimal.valueOf(-9_000_000_000_000_000_000L, 7).stripTrailingZeros(),
        )
        assertStripped(BigInteger.ZERO, 0, BigDecimal.valueOf(0L, Int.MIN_VALUE).stripTrailingZeros())
    }

    @Test
    fun scaleAlignedAdditionKeepsTheScopedNativeResultAlive() {
        val fineUnscaled = BigInteger("123456789012345678901234567890")
        val coarseUnscaled = BigInteger("-98765432109876543210")
        val fine = BigDecimal(fineUnscaled, 20)
        val coarse = BigDecimal(coarseUnscaled, 5)
        val expected = BigDecimal(
            fineUnscaled.add(coarseUnscaled.multiply(BigInteger.TEN.pow(15))),
            20,
        )

        assertEquals(expected, fine.add(coarse))
        assertEquals(expected, coarse.add(fine))
        assertEquals(expected.toPlainString(), fine.add(coarse).toPlainString())
    }

    @Test
    fun roundsWithSingleWordRemainderForPositiveAndNegativeValues() {
        val context = MathContext(11, RoundingMode.HALF_EVEN)

        assertRounded(BigInteger("12345678902"), -9, BigInteger("12345678901500000000"), context)
        assertRounded(BigInteger("-12345678902"), -9, BigInteger("-12345678901500000000"), context)
        assertRounded(BigInteger("12345678901"), -9, BigInteger("12345678901234567895"), context)
    }

    @Test
    fun roundingFastPathNormalizesCarryAndHonorsUnnecessary() {
        assertRounded(
            BigInteger("10000000000"),
            -10,
            BigInteger("99999999999500000000"),
            MathContext(11, RoundingMode.HALF_UP),
        )
        assertFailsWith<ArithmeticException> {
            BigDecimal(BigInteger("12345678901234567895")).round(MathContext(11, RoundingMode.UNNECESSARY))
        }
    }

    @Test
    fun scopedDivisionRemainderComparisonHandlesExactHalfAndSigns() {
        assertScopedDivision("5", ZERO_REMAINDER, "10", "2")
        assertScopedDivision("3", -1, "10", "3")
        assertScopedDivision("3", 1, "11", "3")
        assertScopedDivision("2", 0, "10", "4")
        assertScopedDivision("-2", 0, "-10", "4")
        assertScopedDivision("-2", 0, "10", "-4")
        assertScopedDivision("2", 0, "-10", "-4")
    }

    @Test
    fun largeBigDecimalDivisionRoundsScopedRemaindersWithEverySign() {
        val factor = BigInteger.TEN.pow(100)
        val four = BigDecimal(factor.multiply(BigInteger.valueOf(4)))
        val minusFour = four.negate()
        val ten = BigDecimal(factor.multiply(BigInteger.TEN))
        val fourteen = BigDecimal(factor.multiply(BigInteger.valueOf(14)))
        val context = MathContext(1, RoundingMode.HALF_EVEN)

        assertEquals("2", ten.divide(four, context).toPlainString())
        assertEquals("4", fourteen.divide(four, context).toPlainString())
        assertEquals("-2", ten.negate().divide(four, context).toPlainString())
        assertEquals("-2", ten.divide(minusFour, context).toPlainString())
        assertEquals("4", fourteen.negate().divide(minusFour, context).toPlainString())
    }

    @Test
    fun largeRoundingRemainderIsScoped() {
        val context = MathContext(11, RoundingMode.HALF_EVEN)

        assertRounded(
            BigInteger("12345678901"),
            -19,
            BigInteger("123456789010000000000000000000"),
            context,
        )
        assertRounded(
            BigInteger("12345678902"),
            -19,
            BigInteger("123456789015000000000000000000"),
            context,
        )
        assertRounded(
            BigInteger("-12345678902"),
            -19,
            BigInteger("-123456789015000000000000000000"),
            context,
        )
        assertRounded(
            BigInteger("-10000000000"),
            -20,
            BigInteger("-999999999995000000000000000000"),
            MathContext(11, RoundingMode.HALF_UP),
        )
    }

    @Test
    fun scopedMultiplyPromotesOnlyTheReturnedUnscaledValue() {
        val left = BigDecimal(BigInteger.TEN.pow(200).add(BigInteger.valueOf(3)))
        val right = BigDecimal(BigInteger.TEN.pow(180).add(BigInteger.valueOf(7)))

        val exact = left.multiply(right, MathContext(500, RoundingMode.HALF_EVEN))
        assertEquals(left.multiply(right), exact)

        val rounded = left.multiply(right, MathContext(35, RoundingMode.HALF_EVEN))
        assertEquals(left.multiply(right).round(MathContext(35, RoundingMode.HALF_EVEN)), rounded)
    }

    @Test
    fun scopedDivideHandlesExactAndInexactQuotients() {
        val factor = BigInteger.TEN.pow(220)
        val exactDividend = BigDecimal(factor.multiply(BigInteger.valueOf(21)))
        val exactDivisor = BigDecimal(BigInteger.valueOf(7))
        assertEquals(
            factor.multiply(BigInteger.valueOf(3)),
            exactDividend.divide(exactDivisor, MathContext(500, RoundingMode.HALF_EVEN)).unscaledValue(),
        )

        val inexactDividend = BigDecimal(factor.multiply(BigInteger.valueOf(10)))
        val inexactDivisor = BigDecimal(factor.multiply(BigInteger.valueOf(6)))
        assertEquals(
            "1.6666666666666666667",
            inexactDividend.divide(inexactDivisor, MathContext(20, RoundingMode.HALF_EVEN)).toPlainString(),
        )
    }

    private fun assertScopedDivision(
        expectedQuotient: String,
        expectedComparison: Int,
        dividend: String,
        divisor: String,
    ) {
        val division = BigInteger(dividend).divideAndCompareRemainder(BigInteger(divisor))
        assertEquals(BigInteger(expectedQuotient), division.quotient)
        assertEquals(expectedComparison, division.remainderComparison)
    }

    private fun assertStripped(expectedUnscaled: BigInteger, expectedScale: Int, actual: BigDecimal) {
        assertEquals(expectedUnscaled, actual.unscaledValue())
        assertEquals(expectedScale, actual.scale())
    }

    private fun assertRounded(
        expectedUnscaled: BigInteger,
        expectedScale: Int,
        value: BigInteger,
        context: MathContext,
    ) {
        val actual = BigDecimal(value).round(context)
        assertEquals(expectedUnscaled, actual.unscaledValue())
        assertEquals(expectedScale, actual.scale())
    }
}
