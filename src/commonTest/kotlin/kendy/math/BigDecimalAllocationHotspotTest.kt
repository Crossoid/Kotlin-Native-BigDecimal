package kendy.math

import kotlin.test.Test
import kotlin.test.assertEquals

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

    private fun assertStripped(expectedUnscaled: BigInteger, expectedScale: Int, actual: BigDecimal) {
        assertEquals(expectedUnscaled, actual.unscaledValue())
        assertEquals(expectedScale, actual.scale())
    }
}
