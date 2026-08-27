package kendy.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BigIntegerRadixTest {
    @Test
    fun parsesAndFormatsEverySupportedRadix() {
        val values = listOf(
            BigInteger.ZERO,
            BigInteger("123456789012345678901234567890"),
            BigInteger("-123456789012345678901234567890")
        )

        for (radix in Conversion.MIN_RADIX..Conversion.MAX_RADIX) {
            for (value in values) {
                assertEquals(value, BigInteger(value.toString(radix), radix), "radix $radix")
            }
        }
    }

    @Test
    fun acceptsLeadingPlusAndRejectsMissingDigits() {
        assertEquals(BigInteger.valueOf(255), BigInteger("+ff", 16))
        assertFailsWith<NumberFormatException> { BigInteger("", 10) }
        assertFailsWith<NumberFormatException> { BigInteger("-", 10) }
        assertFailsWith<NumberFormatException> { BigInteger("+", 10) }
    }

    @Test
    fun rejectsInvalidRadixAndDigits() {
        assertFailsWith<NumberFormatException> { BigInteger("10", 1) }
        assertFailsWith<NumberFormatException> { BigInteger("10", 37) }
        assertFailsWith<NumberFormatException> { BigInteger("2", 2) }
    }

    @Test
    fun invalidOutputRadixFallsBackToDecimal() {
        val value = BigInteger("12345678901234567890")
        assertEquals(value.toString(), value.toString(1))
        assertEquals(value.toString(), value.toString(37))
    }
}
