package kendy.math

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Code paths that used to cast between primitive types with `as`, which
 * always throws in Kotlin instead of converting like the Java casts did.
 */
class PrimitiveConversionTest {
    @Test
    fun generatesSmallProbablePrimes() {
        val random = Random(42)
        for (bitLength in 2 until 16) {
            val prime = BigInteger(bitLength, 1, random)
            assertEquals(bitLength, prime.bitLength(), "bitLength $bitLength")

            val value = prime.toInt()
            assertTrue((2 until value).none { value % it == 0 }, "$value is not prime")
        }
    }

    @Test
    fun formatsMultiWordBigIntegerWithScale() {
        val value = BigInteger("-123456789012345678901234567890")
        assertEquals("-123456789012345678901234567890", Conversion.toDecimalScaledString(value, 0))
        assertEquals("-1234567890123456789012345.67890", Conversion.toDecimalScaledString(value, 5))
    }
}
