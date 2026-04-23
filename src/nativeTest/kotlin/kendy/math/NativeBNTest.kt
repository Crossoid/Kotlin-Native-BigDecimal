package kendy.math

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Smoke tests exercising the BoringSSL backed NativeBN on every native target.
 */
class NativeBNTest {
    @Test
    fun bigIntegerArithmetic() {
        val a = BigInteger("123456789012345678901234567890")
        val b = BigInteger("987654321098765432109876543210")
        assertEquals("1111111110111111111011111111100", a.add(b).toString())
        assertEquals("121932631137021795226185032733622923332237463801111263526900", a.multiply(b).toString())
        assertEquals("8", b.divide(a).toString())
        assertEquals("1267650600228229401496703205376", BigInteger.valueOf(2).pow(100).toString())
    }

    @Test
    fun bigDecimalArithmetic() {
        val a = BigDecimal("1.1")
        val b = BigDecimal("2.2")
        assertEquals("3.3", a.add(b).toString())
        assertEquals("2.42", a.multiply(b).toString())
        assertEquals("0.3333333333", BigDecimal("1").divide(BigDecimal("3"), 10, RoundingMode.HALF_UP).toString())
        assertEquals("1.000000000000000000000000000000", BigDecimal("1.000000000000000").pow(2).toString())
    }
}
