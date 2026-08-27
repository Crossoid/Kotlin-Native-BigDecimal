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
