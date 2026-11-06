package kendy.math

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class BigIntegerScopedMutationTest {
    @Test
    fun additionRefreshesCachedBitsAcrossWordBoundaries() {
        assertMutation("4294967295", "4294967296") { it.addInPlace(BigInteger.ONE) }
        assertMutation("-4294967295", "-4294967296") { it.addInPlace(BigInteger.ONE.negate()) }
        assertMutation("4294967296", "4294967295") { it.addInPlace(BigInteger.ONE.negate()) }
    }

    @Test
    fun multiplicationRefreshesCachedBitsAcrossWordBoundaries() {
        assertMutation("2147483648", "4294967296") { it.multiplyByPositiveIntInPlace(2) }
        assertMutation("-2147483648", "-4294967296") { it.multiplyByPositiveIntInPlace(2) }
    }

    @Test
    fun replacementRefreshesCachedBitsSignAndHashCode() {
        for (replacement in listOf("3", "0", "-18446744073709551616")) {
            assertMutation("4294967296", replacement) { it.replaceScopedValue(BigInteger(replacement)) }
        }
    }

    private fun assertMutation(original: String, expectedText: String, mutate: (BigInteger) -> Unit) {
        val expected = BigInteger(expectedText)
        BigInteger(original).withScopedProduct(BigInteger.ONE) { actual ->
            // Cache both the binary representation and values derived from it before mutation.
            actual.testBit(0)
            actual.lowestSetBit
            actual.hashCode()

            mutate(actual)

            assertEquals(expectedText, actual.toString())
            assertEquals(expected.lowestSetBit, actual.lowestSetBit)
            assertEquals(expected.hashCode(), actual.hashCode())
            assertEquals(expected.toString(16), actual.toString(16))
            assertContentEquals(expected.toByteArray(), actual.toByteArray())
        }
    }
}
