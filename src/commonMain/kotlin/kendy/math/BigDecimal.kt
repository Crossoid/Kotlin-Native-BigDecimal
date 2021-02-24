/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package kendy.math

import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.jvm.Transient
import kotlin.math.*

/**
 * An immutable arbitrary-precision signed decimal.
 *
 *
 * A value is represented by an arbitrary-precision "unscaled value" and a signed 32-bit "scale",
 * combined thus: `unscaled * 10<sup>-scale</sup>`. See [.unscaledValue] and [.scale].
 *
 *
 * Most operations allow you to supply a [MathContext] to specify a desired rounding mode.
 */
class BigDecimal : Number, Comparable<BigDecimal?> /*, java.io.Serializable*/ {
    /** The `String` representation is cached.  */
    @Transient
    private var toStringImage: String? = null

    /** Cache for the hash code.  */
    @Transient
    private var hashCode = 0

    companion object {
        /**
         * Rounding mode where positive values are rounded towards positive infinity
         * and negative values towards negative infinity.
         *
         * @see RoundingMode.UP
         */
        const val ROUND_UP = 0

        /**
         * Rounding mode where the values are rounded towards zero.
         *
         * @see RoundingMode.DOWN
         */
        const val ROUND_DOWN = 1

        /**
         * Rounding mode to round towards positive infinity. For positive values
         * this rounding mode behaves as [.ROUND_UP], for negative values as
         * [.ROUND_DOWN].
         *
         * @see RoundingMode.CEILING
         */
        const val ROUND_CEILING = 2

        /**
         * Rounding mode to round towards negative infinity. For positive values
         * this rounding mode behaves as [.ROUND_DOWN], for negative values as
         * [.ROUND_UP].
         *
         * @see RoundingMode.FLOOR
         */
        const val ROUND_FLOOR = 3

        /**
         * Rounding mode where values are rounded towards the nearest neighbor.
         * Ties are broken by rounding up.
         *
         * @see RoundingMode.HALF_UP
         */
        const val ROUND_HALF_UP = 4

        /**
         * Rounding mode where values are rounded towards the nearest neighbor.
         * Ties are broken by rounding down.
         *
         * @see RoundingMode.HALF_DOWN
         */
        const val ROUND_HALF_DOWN = 5

        /**
         * Rounding mode where values are rounded towards the nearest neighbor.
         * Ties are broken by rounding to the even neighbor.
         *
         * @see RoundingMode.HALF_EVEN
         */
        const val ROUND_HALF_EVEN = 6

        /**
         * Rounding mode where the rounding operations throws an `ArithmeticException` for the case that rounding is necessary, i.e. for
         * the case that the value cannot be represented exactly.
         *
         * @see RoundingMode.UNNECESSARY
         */
        const val ROUND_UNNECESSARY = 7

        /** This is the serialVersionUID used by the sun implementation.  */
        private const val serialVersionUID = 6108874887143696463L

        /** The double closest to `Log10(2)`.  */
        private const val LOG10_2 = 0.3010299956639812

        /**
         * An array with powers of five that fit in the type `long`
         * (`5^0,5^1,...,5^27`).
         */
        private val FIVE_POW: Array<BigInteger>

        /**
         * An array with powers of ten that fit in the type `long`
         * (`10^0,10^1,...,10^18`).
         */
        private val TEN_POW: Array<BigInteger>
        private val LONG_FIVE_POW = longArrayOf(
            1L,
            5L,
            25L,
            125L,
            625L,
            3125L,
            15625L,
            78125L,
            390625L,
            1953125L,
            9765625L,
            48828125L,
            244140625L,
            1220703125L,
            6103515625L,
            30517578125L,
            152587890625L,
            762939453125L,
            3814697265625L,
            19073486328125L,
            95367431640625L,
            476837158203125L,
            2384185791015625L,
            11920928955078125L,
            59604644775390625L,
            298023223876953125L,
            1490116119384765625L,
            7450580596923828125L
        )
        private val LONG_FIVE_POW_BIT_LENGTH = IntArray(LONG_FIVE_POW.size)
        private val LONG_POWERS_OF_TEN_BIT_LENGTH = IntArray(MathUtils.LONG_POWERS_OF_TEN.size)
        private const val BI_SCALED_BY_ZERO_LENGTH = 11

        /**
         * An array with the first `BigInteger` scaled by zero.
         * (`[0,0],[1,0],...,[10,0]`).
         */
        private val BI_SCALED_BY_ZERO = arrayOfNulls<BigDecimal>(BI_SCALED_BY_ZERO_LENGTH)

        /**
         * An array with the zero number scaled by the first positive scales.
         * (`0*10^0, 0*10^1, ..., 0*10^10`).
         */
        private val ZERO_SCALED_BY = arrayOfNulls<BigDecimal>(11)

        /** An array filled with characters `'0'`.  */
        private val CH_ZEROS = CharArray(100)

        /**
         * The constant zero as a `BigDecimal`.
         */
        @JvmField
        val ZERO: BigDecimal = BigDecimal(0, 0)

        /**
         * The constant one as a `BigDecimal`.
         */
        @JvmField
        val ONE: BigDecimal = BigDecimal(1, 0)

        /**
         * The constant ten as a `BigDecimal`.
         */
        @JvmField
        val TEN: BigDecimal = BigDecimal(10, 0)
        /* Public Methods */
        /**
         * Returns a new `BigDecimal` instance whose value is equal to `unscaledVal * 10<sup>-scale</sup>`). The scale of the result is `scale`, and its unscaled value is `unscaledVal`.
         */
        fun valueOf(unscaledVal: Long, scale: Int): BigDecimal {
            if (scale == 0) {
                return valueOf(unscaledVal)
            }
            return if (unscaledVal == 0L && scale >= 0
                && scale < ZERO_SCALED_BY.size
            ) {
                ZERO_SCALED_BY[scale]!!
            } else BigDecimal(unscaledVal, scale)
        }

        /**
         * Returns a new `BigDecimal` instance whose value is equal to `unscaledVal`. The scale of the result is `0`, and its unscaled
         * value is `unscaledVal`.
         *
         * @param unscaledVal
         * value to be converted to a `BigDecimal`.
         * @return `BigDecimal` instance with the value `unscaledVal`.
         */
        @JvmStatic
        fun valueOf(unscaledVal: Long): BigDecimal {
            return if (unscaledVal >= 0 && unscaledVal < BI_SCALED_BY_ZERO_LENGTH) {
                BI_SCALED_BY_ZERO[unscaledVal.toInt()]!!
            } else BigDecimal(unscaledVal, 0)
        }

        /**
         * Returns a new `BigDecimal` instance whose value is equal to `val`. The new decimal is constructed as if the `BigDecimal(String)`
         * constructor is called with an argument which is equal to `Double.toString(val)`. For example, `valueOf("0.1")` is converted to
         * (unscaled=1, scale=1), although the double `0.1` cannot be
         * represented exactly as a double value. In contrast to that, a new `BigDecimal(0.1)` instance has the value `0.1000000000000000055511151231257827021181583404541015625` with an
         * unscaled value `1000000000000000055511151231257827021181583404541015625`
         * and the scale `55`.
         *
         * @param val
         * double value to be converted to a `BigDecimal`.
         * @return `BigDecimal` instance with the value `val`.
         * @throws NumberFormatException
         * if `val` is infinite or `val` is not a number
         */
        @JvmStatic
        fun valueOf(`val`: Double): BigDecimal {
            if (`val`.isInfinite() || `val`.isNaN()) {
                throw NumberFormatException("Infinity or NaN: $`val`")
            }
            return BigDecimal(`val`.toString())
        }

        private fun addAndMult10(
            thisValue: BigDecimal,
            augend: BigDecimal,
            diffScale: Int
        ): BigDecimal {
            return if (diffScale < MathUtils.LONG_POWERS_OF_TEN.size &&
                max(
                    thisValue.bitLength,
                    augend.bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[diffScale]
                ) + 1 < 64
            ) {
                valueOf(
                    thisValue.smallValue + augend.smallValue * MathUtils.LONG_POWERS_OF_TEN[diffScale],
                    thisValue.scale
                )
            } else {
                val bi: BigInt =
                        kendy.math.Multiplication.multiplyByTenPow(
                                augend.unscaledValue,
                                diffScale.toLong()
                        )
                                .getBigInt()!!
                bi.add(thisValue.unscaledValue!!.getBigInt()!!)
                BigDecimal(BigInteger(bi), thisValue.scale)
            }
        }

        private fun divideBigIntegers(
            scaledDividend: BigInteger,
            scaledDivisor: BigInteger,
            scale: Int,
            roundingMode: RoundingMode
        ): BigDecimal {
            val quotAndRem =
                scaledDividend.divideAndRemainder(scaledDivisor) // quotient and remainder
            // If after division there is a remainder...
            var quotient = quotAndRem!![0]
            val remainder = quotAndRem[1]
            if (remainder.signum() == 0) {
                return BigDecimal(quotient, scale)
            }
            val sign = scaledDividend.signum() * scaledDivisor.signum()
            var compRem: Int // 'compare to remainder'
            if (scaledDivisor.bitLength() < 63) { // 63 in order to avoid out of long after *2
                val rem = remainder.toLong()
                val divisor = scaledDivisor.toLong()
                compRem = compareForRounding(rem, divisor)
                // To look if there is a carry
                compRem = roundingBehavior(
                    if (quotient.testBit(0)) 1 else 0,
                    sign * (5 + compRem), roundingMode
                )
            } else {
                // Checking if:  remainder * 2 >= scaledDivisor
                compRem = remainder.abs().shiftLeftOneBit().compareTo(scaledDivisor.abs())
                compRem = roundingBehavior(
                    if (quotient.testBit(0)) 1 else 0,
                    sign * (5 + compRem), roundingMode
                )
            }
            if (compRem != 0) {
                if (quotient.bitLength() < 63) {
                    return valueOf(quotient.toLong() + compRem, scale)
                }
                quotient = quotient.add(BigInteger.valueOf(compRem.toLong()))
                return BigDecimal(quotient, scale)
            }
            // Constructing the result with the appropriate unscaled value
            return BigDecimal(quotient, scale)
        }

        private fun dividePrimitiveLongs(
            scaledDividend: Long,
            scaledDivisor: Long,
            scale: Int,
            roundingMode: RoundingMode
        ): BigDecimal {
            var quotient = scaledDividend / scaledDivisor
            val remainder = scaledDividend % scaledDivisor
            val sign: Int = scaledDividend.sign * scaledDivisor.sign
            if (remainder != 0L) {
                // Checking if:  remainder * 2 >= scaledDivisor
                val compRem = compareForRounding(remainder, scaledDivisor) // 'compare to remainder'
                // To look if there is a carry
                quotient += roundingBehavior(
                    quotient.toInt() and 1,
                    sign * (5 + compRem),
                    roundingMode
                ).toLong()
            }
            // Constructing the result with the appropriate unscaled value
            return valueOf(quotient, scale)
        }

        /**
         * Returns -1, 0, and 1 if `value1 < value2`, `value1 == value2`,
         * and `value1 > value2`, respectively, when comparing without regard
         * to the values' sign.
         *
         *
         * Note that this implementation deals correctly with Long.MIN_VALUE,
         * whose absolute magnitude is larger than any other `long` value.
         */
        private fun compareAbsoluteValues(value1: Long, value2: Long): Int {
            // Map long values to the range -1 .. Long.MAX_VALUE so that comparison
            // of absolute magnitude can be done using regular long arithmetics.
            // This deals correctly with Long.MIN_VALUE, whose absolute magnitude
            // is larger than any other long value, and which is mapped to
            // Long.MAX_VALUE here.
            // Values that only differ by sign get mapped to the same value, for
            // example both +3 and -3 get mapped to +2.
            var value1 = value1
            var value2 = value2
            value1 = abs(value1) - 1
            value2 = abs(value2) - 1
            // Unlike Long.compare(), we guarantee to return specifically -1 and +1
            return if (value1 > value2) 1 else if (value1 < value2) -1 else 0
        }

        /**
         * Compares `n` against `0.5 * d` in absolute terms (ignoring sign)
         * and with arithmetics that are safe against overflow or loss of precision.
         * Returns -1 if `n` is less than `0.5 * d`, 0 if `n == 0.5 * d`,
         * or +1 if `n > 0.5 * d` when comparing the absolute values under such
         * arithmetics.
         */
        private fun compareForRounding(n: Long, d: Long): Int {
            val halfD = d / 2 //  rounds towards 0
            return if (n == halfD || n == -halfD) {
                // In absolute terms: Because n == halfD, we know that 2 * n + lsb == d
                // for some lsb value 0 or 1. This means that n == d/2 (result 0) if
                // lsb is 0, or n < d/2 (result -1) if lsb is 1. In either case, the
                // result is -lsb.
                // Since we're calculating in absolute terms, we need the absolute lsb
                // (d & 1) as opposed to the signed lsb (d % 2) which would be -1 for
                // negative odd values of d.
                val lsb = d.toInt() and 1
                -lsb // returns 0 or -1
            } else {
                // In absolute terms, either 2 * n + 1 < d (in the case of n < halfD),
                // or 2 * n > d (in the case of n > halfD).
                // In either case, comparing n against halfD gets the right result
                // -1 or +1, respectively.
                compareAbsoluteValues(n, halfD)
            }
        }

        /**
         * Return an increment that can be -1,0 or 1, depending of
         * `roundingMode`.
         *
         * @param parityBit
         * can be 0 or 1, it's only used in the case
         * `HALF_EVEN`
         * @param fraction
         * the mantissa to be analyzed
         * @param roundingMode
         * the type of rounding
         * @return the carry propagated after rounding
         */
        private fun roundingBehavior(
            parityBit: Int,
            fraction: Int,
            roundingMode: RoundingMode
        ): Int {
            var increment = 0 // the carry after rounding
            when (roundingMode) {
                RoundingMode.UNNECESSARY -> if (fraction != 0) {
                    throw ArithmeticException("Rounding necessary")
                }
                RoundingMode.UP -> increment = fraction.sign
                RoundingMode.DOWN -> {
                }
                RoundingMode.CEILING -> increment = max(fraction.sign, 0)
                RoundingMode.FLOOR -> increment = min(fraction.sign, 0)
                RoundingMode.HALF_UP -> if (abs(fraction) >= 5) {
                    increment = fraction.sign
                }
                RoundingMode.HALF_DOWN -> if (abs(fraction) > 5) {
                    increment = fraction.sign
                }
                RoundingMode.HALF_EVEN -> if (abs(fraction) + parityBit > 5) {
                    increment = fraction.sign
                }
            }
            return increment
        }

        private fun safeLongToInt(longValue: Long): Int {
            if (longValue < Int.MIN_VALUE || longValue > Int.MAX_VALUE) {
                throw ArithmeticException("Out of int range: $longValue")
            }
            return longValue.toInt()
        }

        /**
         * It returns the value 0 with the most approximated scale of type
         * `int`. if `longScale > Integer.MAX_VALUE` the
         * scale will be `Integer.MAX_VALUE`; if
         * `longScale < Integer.MIN_VALUE` the scale will be
         * `Integer.MIN_VALUE`; otherwise `longScale` is
         * casted to the type `int`.
         *
         * @param longScale
         * the scale to which the value 0 will be scaled.
         * @return the value 0 scaled by the closer scale of type `int`.
         * @see .scale
         */
        private fun zeroScaledBy(longScale: Long): BigDecimal {
            if (longScale == (longScale.toInt()).toLong()){
                return valueOf(0, longScale.toInt())
            }
            return if (longScale >= 0) {
                BigDecimal(0, Int.MAX_VALUE)
            } else BigDecimal(0, Int.MIN_VALUE)
        }

        private fun bitLength(smallValue: Long): Int {
            var smallValue = smallValue
            if (smallValue < 0) {
                smallValue = smallValue.inv()
            }
            return 64 - smallValue.countLeadingZeroBits()
        }

        private fun bitLength(smallValue: Int): Int {
            var smallValue = smallValue
            if (smallValue < 0) {
                smallValue = smallValue.inv()
            }
            return 32 - smallValue.countLeadingZeroBits()
        }

        init {
            CH_ZEROS.fill('0')
            for (i in ZERO_SCALED_BY.indices) {
                BI_SCALED_BY_ZERO[i] = BigDecimal(i, 0)
                ZERO_SCALED_BY[i] = BigDecimal(0, i)
            }
            for (i in LONG_FIVE_POW_BIT_LENGTH.indices) {
                LONG_FIVE_POW_BIT_LENGTH[i] = bitLength(
                    LONG_FIVE_POW[i]
                )
            }
            for (i in LONG_POWERS_OF_TEN_BIT_LENGTH.indices) {
                LONG_POWERS_OF_TEN_BIT_LENGTH[i] = bitLength(
                    MathUtils.LONG_POWERS_OF_TEN[i]
                )
            }

            // Taking the references of useful powers.
            TEN_POW = kendy.math.Multiplication.bigTenPows
            FIVE_POW = kendy.math.Multiplication.bigFivePows
        }
    }

    /**
     * The arbitrary precision integer (unscaled value) in the internal
     * representation of `BigDecimal`.
     */
    private var intVal: BigInteger? = null

    @Transient
    private var bitLength = 0

    @Transient
    private var smallValue: Long = 0

    /**
     * The 32-bit integer scale in the internal representation of `BigDecimal`.
     */
    private var scale = 0

    /**
     * Represent the number of decimal digits in the unscaled value. This
     * precision is calculated the first time, and used in the following calls
     * of method `precision()`. Note that some call to the private
     * method `inplaceRound()` could update this field.
     *
     * @see .precision
     * @see .inplaceRound
     */
    @Transient
    private var precision = 0

    private constructor(smallValue: Long, scale: Int) {
        this.smallValue = smallValue
        this.scale = scale
        bitLength = bitLength(smallValue)
    }

    private constructor(smallValue: Int, scale: Int) {
        this.smallValue = smallValue.toLong()
        this.scale = scale
        bitLength = bitLength(smallValue)
    }
    /**
     * Constructs a new `BigDecimal` instance from a string representation
     * given as a character array.
     *
     * @param in
     * array of characters containing the string representation of
     * this `BigDecimal`.
     * @param offset
     * first index to be copied.
     * @param len
     * number of characters to be used.
     * @throws NumberFormatException
     * if `offset < 0 || len <= 0 || offset+len-1 < 0 ||
     * offset+len-1 >= in.length`, or if `in` does not
     * contain a valid string representation of a big decimal.
     */
    /**
     * Constructs a new `BigDecimal` instance from a string representation
     * given as a character array.
     *
     * @param in
     * array of characters containing the string representation of
     * this `BigDecimal`.
     * @throws NumberFormatException
     * if `in` does not contain a valid string representation
     * of a big decimal.
     */
    @JvmOverloads
    constructor(`in`: CharArray?, offset: Int = 0, len: Int = `in`!!.size) {
        var offset = offset
        var begin = offset // first index to be copied
        val last = offset + (len - 1) // last index to be copied
        val scaleString: String // buffer for scale
        val unscaledBuffer: StringBuilder // buffer for unscaled value
        val newScale: Long // the new scale
        if (`in` == null) {
            throw NullPointerException("in == null")
        }
        if (last >= `in`.size || offset < 0 || len <= 0 || last < 0) {
            throw NumberFormatException(
                "Bad offset/length: offset=" + offset +
                        " len=" + len + " in.length=" + `in`.size
            )
        }
        unscaledBuffer = StringBuilder(len)
        var bufLength = 0
        // To skip a possible '+' symbol
        if (offset <= last && `in`[offset] == '+') {
            offset++
            begin++
        }
        var counter = 0
        var wasNonZero = false
        // Accumulating all digits until a possible decimal point
        while (offset <= last && `in`[offset] != '.' && `in`[offset] != 'e' && `in`[offset] != 'E') {
            if (!wasNonZero) {
                if (`in`[offset] == '0') {
                    counter++
                } else {
                    wasNonZero = true
                }
            }
            offset++
        }
        unscaledBuffer.appendRange(`in`, begin, offset)
        bufLength += offset - begin
        // A decimal point was found
        if (offset <= last && `in`[offset] == '.') {
            offset++
            // Accumulating all digits until a possible exponent
            begin = offset
            while (offset <= last && `in`[offset] != 'e'
                && `in`[offset] != 'E'
            ) {
                if (!wasNonZero) {
                    if (`in`[offset] == '0') {
                        counter++
                    } else {
                        wasNonZero = true
                    }
                }
                offset++
            }
            scale = offset - begin
            bufLength += scale
            unscaledBuffer.appendRange(`in`, begin, offset)
        } else {
            scale = 0
        }
        // An exponent was found
        if (offset <= last && (`in`[offset] == 'e' || `in`[offset] == 'E')) {
            offset++
            // Checking for a possible sign of scale
            begin = offset
            if (offset <= last && `in`[offset] == '+') {
                offset++
                if (offset <= last && `in`[offset] != '-') {
                    begin++
                }
            }
            // Accumulating all remaining digits
            scaleString = String(`in`, begin, last + 1 - begin)
            // Checking if the scale is defined
            newScale = scale.toLong() - scaleString.toInt()
            scale = newScale.toInt()
            if (newScale != scale.toLong()) {
                throw NumberFormatException("Scale out of range")
            }
        }
        // Parsing the unscaled value
        if (bufLength < 19) {
            smallValue = unscaledBuffer.toString().toLong()
            bitLength = bitLength(smallValue)
        } else {
            unscaledValue = BigInteger(unscaledBuffer.toString())
        }
    }

    /**
     * Constructs a new `BigDecimal` instance from a string representation
     * given as a character array.
     *
     * @param in
     * array of characters containing the string representation of
     * this `BigDecimal`.
     * @param offset
     * first index to be copied.
     * @param len
     * number of characters to be used.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws NumberFormatException
     * if `offset < 0 || len <= 0 || offset+len-1 < 0 ||
     * offset+len-1 >= in.length`, or if `in` does not
     * contain a valid string representation of a big decimal.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`in`: CharArray?, offset: Int, len: Int, mc: kendy.math.MathContext) : this(
        `in`,
        offset,
        len
    ) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from a string representation
     * given as a character array. The result is rounded according to the
     * specified math context.
     *
     * @param in
     * array of characters containing the string representation of
     * this `BigDecimal`.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws NumberFormatException
     * if `in` does not contain a valid string representation
     * of a big decimal.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`in`: CharArray, mc: kendy.math.MathContext) : this(`in`, 0, `in`.size) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from a string
     * representation.
     *
     * @throws NumberFormatException
     * if `val` does not contain a valid string representation
     * of a big decimal.
     */
    constructor(`val`: String) : this(`val`.toCharArray(), 0, `val`.length) {}

    /**
     * Constructs a new `BigDecimal` instance from a string
     * representation. The result is rounded according to the specified math
     * context.
     *
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws NumberFormatException
     * if `val` does not contain a valid string representation
     * of a big decimal.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`val`: String, mc: kendy.math.MathContext) : this(
        `val`.toCharArray(),
        0,
        `val`.length
    ) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from the 64bit double
     * `val`. The constructed big decimal is equivalent to the given
     * double. For example, `new BigDecimal(0.1)` is equal to `0.1000000000000000055511151231257827021181583404541015625`. This happens
     * as `0.1` cannot be represented exactly in binary.
     *
     *
     * To generate a big decimal instance which is equivalent to `0.1` use
     * the `BigDecimal(String)` constructor.
     *
     * @param val
     * double value to be converted to a `BigDecimal` instance.
     * @throws NumberFormatException
     * if `val` is infinity or not a number.
     */
    constructor(`val`: Double) {
        if (`val`.isInfinite() || `val`.isNaN()) {
            throw NumberFormatException("Infinity or NaN: $`val`")
        }
        val bits: Long = `val`.toRawBits() // IEEE-754
        var mantissa: Long
        val trailingZeros: Int
        // Extracting the exponent, note that the bias is 1023
        scale = 1075 - (bits shr 52 and 0x7FFL).toInt()
        // Extracting the 52 bits of the mantissa.
        mantissa =
            if (scale == 1075) bits and 0xFFFFFFFFFFFFFL shl 1 else bits and 0xFFFFFFFFFFFFFL or 0x10000000000000L
        if (mantissa == 0L) {
            scale = 0
            precision = 1
        }
        // To simplify all factors '2' in the mantissa
        if (scale > 0) {
            trailingZeros =
                min(scale, mantissa.countTrailingZeroBits())
            mantissa = mantissa ushr trailingZeros
            scale -= trailingZeros
        }
        // Calculating the new unscaled value and the new scale
        if (bits shr 63 != 0L) {
            mantissa = -mantissa
        }
        val mantissaBits = bitLength(mantissa)
        if (scale < 0) {
            bitLength = if (mantissaBits == 0) 0 else mantissaBits - scale
            if (bitLength < 64) {
                smallValue = mantissa shl -scale
            } else {
                val bi = BigInt()
                bi.putLongInt(mantissa)
                bi.shift(-scale)
                intVal = BigInteger(bi)
            }
            scale = 0
        } else if (scale > 0) {
            // m * 2^e =  (m * 5^(-e)) * 10^e
            if (scale < LONG_FIVE_POW.size
                && mantissaBits + LONG_FIVE_POW_BIT_LENGTH[scale] < 64
            ) {
                smallValue = mantissa * LONG_FIVE_POW[scale]
                bitLength = bitLength(smallValue)
            } else {
                unscaledValue =
                    kendy.math.Multiplication.multiplyByFivePow(BigInteger.valueOf(mantissa), scale)
            }
        } else { // scale == 0
            smallValue = mantissa
            bitLength = mantissaBits
        }
    }

    /**
     * Constructs a new `BigDecimal` instance from the 64bit double
     * `val`. The constructed big decimal is equivalent to the given
     * double. For example, `new BigDecimal(0.1)` is equal to `0.1000000000000000055511151231257827021181583404541015625`. This happens
     * as `0.1` cannot be represented exactly in binary.
     *
     *
     * To generate a big decimal instance which is equivalent to `0.1` use
     * the `BigDecimal(String)` constructor.
     *
     * @param val
     * double value to be converted to a `BigDecimal` instance.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws NumberFormatException
     * if `val` is infinity or not a number.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`val`: Double, mc: kendy.math.MathContext) : this(`val`) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from the given big integer
     * `val`. The scale of the result is `0`.
     */
    constructor(`val`: BigInteger?) : this(`val`, 0) {}

    /**
     * Constructs a new `BigDecimal` instance from the given big integer
     * `val`. The scale of the result is `0`.
     *
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`val`: BigInteger?, mc: kendy.math.MathContext) : this(`val`) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from a given unscaled value
     * `unscaledVal` and a given scale. The value of this instance is
     * `unscaledVal * 10<sup>-scale</sup>`).
     *
     * @throws NullPointerException
     * if `unscaledVal == null`.
     */
    constructor(unscaledVal: BigInteger?, scale: Int) {
        if (unscaledVal == null) {
            throw NullPointerException("unscaledVal == null")
        }
        this.scale = scale
        unscaledValue = unscaledVal
    }

    /**
     * Constructs a new `BigDecimal` instance from a given unscaled value
     * `unscaledVal` and a given scale. The value of this instance is
     * `unscaledVal * 10<sup>-scale</sup>). The result is rounded according
     * to the specified math context.
     *
     * mc
     * rounding mode and precision for the result of this operation.
     * ArithmeticException
     * if { mc.precision > 0} and { mc.roundingMode ==
     * UNNECESSARY} and the new big decimal cannot be represented
     * within the given precision without rounding.
     * NullPointerException
     * if { unscaledVal == null}.`
     */
    constructor(
        unscaledVal: BigInteger?,
        scale: Int,
        mc: kendy.math.MathContext
    ) : this(unscaledVal, scale) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from the given int
     * `val`. The scale of the result is 0.
     *
     * @param val
     * int value to be converted to a `BigDecimal` instance.
     */
    constructor(`val`: Int) : this(`val`, 0) {}

    /**
     * Constructs a new `BigDecimal` instance from the given int `val`. The scale of the result is `0`. The result is rounded
     * according to the specified math context.
     *
     * @param val
     * int value to be converted to a `BigDecimal` instance.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `c.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`val`: Int, mc: kendy.math.MathContext) : this(`val`, 0) {
        inplaceRound(mc)
    }

    /**
     * Constructs a new `BigDecimal` instance from the given long `val`. The scale of the result is `0`.
     *
     * @param val
     * long value to be converted to a `BigDecimal` instance.
     */
    constructor(`val`: Long) : this(`val`, 0) {}

    /**
     * Constructs a new `BigDecimal` instance from the given long `val`. The scale of the result is `0`. The result is rounded
     * according to the specified math context.
     *
     * @param val
     * long value to be converted to a `BigDecimal` instance.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and the new big decimal cannot be represented
     * within the given precision without rounding.
     */
    constructor(`val`: Long, mc: kendy.math.MathContext) : this(`val`) {
        inplaceRound(mc)
    }

    /**
     * Returns a new `BigDecimal` whose value is `this + augend`.
     * The scale of the result is the maximum of the scales of the two
     * arguments.
     *
     * @param augend
     * value to be added to `this`.
     * @return `this + augend`.
     * @throws NullPointerException
     * if `augend == null`.
     */
    fun add(augend: BigDecimal?): BigDecimal {
        val diffScale = scale - augend!!.scale
        // Fast return when some operand is zero
        if (isZero) {
            if (diffScale <= 0) {
                return augend
            }
            if (augend.isZero) {
                return this
            }
        } else if (augend.isZero) {
            if (diffScale >= 0) {
                return this
            }
        }
        // Let be:  this = [u1,s1]  and  augend = [u2,s2]
        return if (diffScale == 0) {
            // case s1 == s2: [u1 + u2 , s1]
            if (max(bitLength, augend.bitLength) + 1 < 64) {
                valueOf(
                    smallValue + augend.smallValue,
                    scale
                )
            } else BigDecimal(
                unscaledValue!!.add(augend.unscaledValue!!),
                scale
            )
        } else if (diffScale > 0) {
            // case s1 > s2 : [(u1 + u2) * 10 ^ (s1 - s2) , s1]
            addAndMult10(this, augend, diffScale)
        } else { // case s2 > s1 : [(u2 + u1) * 10 ^ (s2 - s1) , s2]
            addAndMult10(augend, this, -diffScale)
        }
    }

    /**
     * Returns a new `BigDecimal` whose value is `this + augend`.
     * The result is rounded according to the passed context `mc`.
     *
     * @param augend
     * value to be added to `this`.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `this + augend`.
     * @throws NullPointerException
     * if `augend == null` or `mc == null`.
     */
    fun add(augend: BigDecimal, mc: MathContext?): BigDecimal {
        var larger: BigDecimal // operand with the largest unscaled value
        val smaller: BigDecimal // operand with the smallest unscaled value
        var tempBI: BigInteger
        val diffScale = scale.toLong() - augend.scale
        val largerSignum: Int
        // Some operand is zero or the precision is infinity
        if (augend.isZero || isZero
            || mc!!.precision == 0
        ) {
            return add(augend)!!.round(mc)
        }
        // Cases where there is room for optimizations
        if (approxPrecision() < diffScale - 1) {
            larger = augend
            smaller = this
        } else if (augend.approxPrecision() < -diffScale - 1) {
            larger = this
            smaller = augend
        } else { // No optimization is done
            return add(augend)!!.round(mc)
        }
        if (mc.precision >= larger.approxPrecision()) {
            // No optimization is done
            return add(augend)!!.round(mc)
        }
        // Cases where it's unnecessary to add two numbers with very different scales
        largerSignum = larger.signum()
        if (largerSignum == smaller.signum()) {
            tempBI = kendy.math.Multiplication.multiplyByPositiveInt(larger.unscaledValue, 10)
                .add(BigInteger.valueOf(largerSignum.toLong()))
        } else {
            tempBI = larger.unscaledValue!!.subtract(
                BigInteger.valueOf(largerSignum.toLong())
            )
            tempBI = kendy.math.Multiplication.multiplyByPositiveInt(tempBI, 10)
                .add(BigInteger.valueOf((largerSignum * 9).toLong()))
        }
        // Rounding the improved adding
        larger = BigDecimal(tempBI, larger.scale + 1)
        return larger.round(mc)
    }

    /**
     * Returns a new `BigDecimal` whose value is `this - subtrahend`.
     * The scale of the result is the maximum of the scales of the two arguments.
     *
     * @param subtrahend
     * value to be subtracted from `this`.
     * @return `this - subtrahend`.
     * @throws NullPointerException
     * if `subtrahend == null`.
     */
    fun subtract(subtrahend: BigDecimal?): BigDecimal {
        var diffScale = scale - subtrahend!!.scale
        // Fast return when some operand is zero
        if (isZero) {
            if (diffScale <= 0) {
                return subtrahend.negate()
            }
            if (subtrahend.isZero) {
                return this
            }
        } else if (subtrahend.isZero) {
            if (diffScale >= 0) {
                return this
            }
        }
        // Let be: this = [u1,s1] and subtrahend = [u2,s2] so:
        return if (diffScale == 0) {
            // case s1 = s2 : [u1 - u2 , s1]
            if (max(bitLength, subtrahend.bitLength) + 1 < 64) {
                valueOf(smallValue - subtrahend.smallValue, scale)
            } else BigDecimal(unscaledValue!!.subtract(subtrahend.unscaledValue!!), scale)
        } else if (diffScale > 0) {
            // case s1 > s2 : [ u1 - u2 * 10 ^ (s1 - s2) , s1 ]
            if (diffScale < MathUtils.LONG_POWERS_OF_TEN.size &&
                max(
                    bitLength,
                    subtrahend.bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[diffScale]
                ) + 1 < 64
            ) {
                valueOf(
                    smallValue - subtrahend.smallValue * MathUtils.LONG_POWERS_OF_TEN[diffScale],
                    scale
                )
            } else BigDecimal(
                unscaledValue!!.subtract(
                    kendy.math.Multiplication.multiplyByTenPow(
                        subtrahend.unscaledValue,
                        diffScale.toLong()
                    )
                ), scale
            )
        } else { // case s2 > s1 : [ u1 * 10 ^ (s2 - s1) - u2 , s2 ]
            diffScale = -diffScale
            if (diffScale < MathUtils.LONG_POWERS_OF_TEN.size &&
                max(
                    bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[diffScale],
                    subtrahend.bitLength
                ) + 1 < 64
            ) {
                valueOf(
                    smallValue * MathUtils.LONG_POWERS_OF_TEN[diffScale] - subtrahend.smallValue,
                    subtrahend.scale
                )
            } else BigDecimal(
                kendy.math.Multiplication.multiplyByTenPow(unscaledValue, diffScale.toLong())
                    .subtract(subtrahend.unscaledValue), subtrahend.scale
            )
        }
    }

    /**
     * Returns a new `BigDecimal` whose value is `this - subtrahend`.
     * The result is rounded according to the passed context `mc`.
     *
     * @param subtrahend
     * value to be subtracted from `this`.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `this - subtrahend`.
     * @throws NullPointerException
     * if `subtrahend == null` or `mc == null`.
     */
    fun subtract(subtrahend: BigDecimal, mc: MathContext?): BigDecimal {
        val diffScale = subtrahend.scale - scale.toLong()
        val thisSignum: Int
        val leftOperand: BigDecimal // it will be only the left operand (this)
        var tempBI: BigInteger
        // Some operand is zero or the precision is infinity
        if (subtrahend.isZero || isZero
            || mc!!.precision == 0
        ) {
            return subtract(subtrahend)!!.round(mc)
        }
        // Now:   this != 0   and   subtrahend != 0
        if (subtrahend.approxPrecision() < diffScale - 1) {
            // Cases where it is unnecessary to subtract two numbers with very different scales
            if (mc.precision < approxPrecision()) {
                thisSignum = signum()
                if (thisSignum != subtrahend.signum()) {
                    tempBI = kendy.math.Multiplication.multiplyByPositiveInt(unscaledValue, 10)
                        .add(BigInteger.valueOf(thisSignum.toLong()))
                } else {
                    tempBI = unscaledValue!!.subtract(BigInteger.valueOf(thisSignum.toLong()))
                    tempBI = kendy.math.Multiplication.multiplyByPositiveInt(tempBI, 10)
                        .add(BigInteger.valueOf((thisSignum * 9).toLong()))
                }
                // Rounding the improved subtracting
                leftOperand = BigDecimal(tempBI, scale + 1)
                return leftOperand.round(mc)
            }
        }
        // No optimization is done
        return subtract(subtrahend)!!.round(mc)
    }

    /**
     * Returns a new `BigDecimal` whose value is `this *
     * multiplicand`. The scale of the result is the sum of the scales of the
     * two arguments.
     *
     * @param multiplicand
     * value to be multiplied with `this`.
     * @return `this * multiplicand`.
     * @throws NullPointerException
     * if `multiplicand == null`.
     */
    fun multiply(multiplicand: BigDecimal?): BigDecimal {
        val newScale = scale.toLong() + multiplicand!!.scale
        if (isZero || multiplicand.isZero) {
            return zeroScaledBy(newScale)
        }
        /* Let be: this = [u1,s1] and multiplicand = [u2,s2] so:
         * this x multiplicand = [ s1 * s2 , s1 + s2 ] */if (bitLength + multiplicand.bitLength < 64) {
            val unscaledValue = smallValue * multiplicand.smallValue
            // b/19185440 Case where result should be +2^63 but unscaledValue overflowed to -2^63
            val longMultiplicationOverflowed = unscaledValue == Long.MIN_VALUE &&
                    smallValue.toFloat().sign * multiplicand.smallValue.toFloat().sign > 0
            if (!longMultiplicationOverflowed) {
                return valueOf(unscaledValue, safeLongToInt(newScale))
            }
        }
        return BigDecimal(
            unscaledValue!!.multiply(
                multiplicand.unscaledValue!!
            ), safeLongToInt(newScale)
        )
    }

    /**
     * Returns a new `BigDecimal` whose value is `this *
     * multiplicand`. The result is rounded according to the passed context
     * `mc`.
     *
     * @param multiplicand
     * value to be multiplied with `this`.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `this * multiplicand`.
     * @throws NullPointerException
     * if `multiplicand == null` or `mc == null`.
     */
    fun multiply(multiplicand: BigDecimal?, mc: MathContext?): BigDecimal {
        val result = multiply(multiplicand)
        result!!.inplaceRound(mc!!)
        return result
    }

    /**
     * Returns a new `BigDecimal` whose value is `this / divisor`.
     * As scale of the result the parameter `scale` is used. If rounding
     * is required to meet the specified scale, then the specified rounding mode
     * `roundingMode` is applied.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param scale
     * the scale of the result returned.
     * @param roundingMode
     * rounding mode to be used to round the result.
     * @return `this / divisor` rounded according to the given rounding
     * mode.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws IllegalArgumentException
     * if `roundingMode` is not a valid rounding mode.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `roundingMode == ROUND_UNNECESSARY` and rounding is
     * necessary according to the given scale.
     */
    fun divide(divisor: BigDecimal, scale: Int, roundingMode: Int): BigDecimal? {
        return divide(divisor, scale, RoundingMode.valueOf(roundingMode))
    }

    /**
     * Returns a new `BigDecimal` whose value is `this / divisor`.
     * As scale of the result the parameter `scale` is used. If rounding
     * is required to meet the specified scale, then the specified rounding mode
     * `roundingMode` is applied.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param scale
     * the scale of the result returned.
     * @param roundingMode
     * rounding mode to be used to round the result.
     * @return `this / divisor` rounded according to the given rounding
     * mode.
     * @throws NullPointerException
     * if `divisor == null` or `roundingMode == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `roundingMode == RoundingMode.UNNECESSAR`Y and
     * rounding is necessary according to the given scale and given
     * precision.
     */
    fun divide(divisor: BigDecimal, scale: Int, roundingMode: RoundingMode?): BigDecimal? {
        // Let be: this = [u1,s1]  and  divisor = [u2,s2]
        if (roundingMode == null) {
            throw NullPointerException("roundingMode == null")
        }
        if (divisor.isZero) {
            throw ArithmeticException("Division by zero")
        }
        val diffScale = this.scale.toLong() - divisor.scale - scale

        // Check whether the diffScale will fit into an int. See http://b/17393664.
        if (bitLength(diffScale) > 32) {
            throw ArithmeticException(
                "Unable to perform divisor / dividend scaling: the difference in scale is too" +
                        " big (" + diffScale + ")"
            )
        }
        if (bitLength < 64 && divisor.bitLength < 64) {
            if (diffScale == 0L) {
                // http://b/26105053 - corner case: Long.MIN_VALUE / (-1) overflows a long
                if (smallValue != Long.MIN_VALUE || divisor.smallValue != -1L) {
                    return dividePrimitiveLongs(
                        smallValue,
                        divisor.smallValue,
                        scale,
                        roundingMode
                    )
                }
            } else if (diffScale > 0) {
                if (diffScale < MathUtils.LONG_POWERS_OF_TEN.size &&
                    divisor.bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[diffScale.toInt()] < 64
                ) {
                    return dividePrimitiveLongs(
                        smallValue,
                        divisor.smallValue * MathUtils.LONG_POWERS_OF_TEN[diffScale.toInt()],
                        scale,
                        roundingMode
                    )
                }
            } else { // diffScale < 0
                if (-diffScale < MathUtils.LONG_POWERS_OF_TEN.size &&
                    bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[(-diffScale).toInt()] < 64
                ) {
                    return dividePrimitiveLongs(
                        smallValue * MathUtils.LONG_POWERS_OF_TEN[(-diffScale).toInt()],
                        divisor.smallValue,
                        scale,
                        roundingMode
                    )
                }
            }
        }
        var scaledDividend = unscaledValue!!
        var scaledDivisor = divisor.unscaledValue!! // for scaling of 'u2'
        if (diffScale > 0) {
            // Multiply 'u2'  by:  10^((s1 - s2) - scale)
            scaledDivisor =
                kendy.math.Multiplication.multiplyByTenPow(scaledDivisor, (diffScale as Int).toLong())
        } else if (diffScale < 0) {
            // Multiply 'u1'  by:  10^(scale - (s1 - s2))
            scaledDividend = kendy.math.Multiplication.multiplyByTenPow(
                scaledDividend, (-diffScale as Int).toLong()
            )
        }
        return divideBigIntegers(scaledDividend, scaledDivisor, scale, roundingMode)
    }

    /**
     * Returns a new `BigDecimal` whose value is `this / divisor`.
     * The scale of the result is the scale of `this`. If rounding is
     * required to meet the specified scale, then the specified rounding mode
     * `roundingMode` is applied.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param roundingMode
     * rounding mode to be used to round the result.
     * @return `this / divisor` rounded according to the given rounding
     * mode.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws IllegalArgumentException
     * if `roundingMode` is not a valid rounding mode.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `roundingMode == ROUND_UNNECESSARY` and rounding is
     * necessary according to the scale of this.
     */
    fun divide(divisor: BigDecimal, roundingMode: Int): BigDecimal? {
        return divide(divisor, scale, RoundingMode.valueOf(roundingMode))
    }

    /**
     * Returns a new `BigDecimal` whose value is `this / divisor`.
     * The scale of the result is the scale of `this`. If rounding is
     * required to meet the specified scale, then the specified rounding mode
     * `roundingMode` is applied.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param roundingMode
     * rounding mode to be used to round the result.
     * @return `this / divisor` rounded according to the given rounding
     * mode.
     * @throws NullPointerException
     * if `divisor == null` or `roundingMode == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `roundingMode == RoundingMode.UNNECESSARY` and
     * rounding is necessary according to the scale of this.
     */
    fun divide(divisor: BigDecimal, roundingMode: RoundingMode?): BigDecimal? {
        return divide(divisor, scale, roundingMode)
    }

    /**
     * Returns a new `BigDecimal` whose value is `this / divisor`.
     * The scale of the result is the difference of the scales of `this`
     * and `divisor`. If the exact result requires more digits, then the
     * scale is adjusted accordingly. For example, `1/128 = 0.0078125`
     * which has a scale of `7` and precision `5`.
     *
     * @param divisor
     * value by which `this` is divided.
     * @return `this / divisor`.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if the result cannot be represented exactly.
     */
    fun divide(divisor: BigDecimal?): BigDecimal {
        var p = unscaledValue!!
        var q = divisor!!.unscaledValue!!
        val gcd: BigInteger // greatest common divisor between 'p' and 'q'
        var quotAndRem: Array<BigInteger>
        val diffScale = scale.toLong() - divisor.scale
        val newScale: Int // the new scale for final quotient
        val k: Int // number of factors "2" in 'q'
        var l = 0 // number of factors "5" in 'q'
        var i = 1
        val lastPow = FIVE_POW.size - 1
        if (divisor.isZero) {
            throw ArithmeticException("Division by zero")
        }
        if (p.signum() == 0) {
            return zeroScaledBy(diffScale)
        }
        // To divide both by the GCD
        gcd = p.gcd(q)
        p = p.divide(gcd)
        q = q.divide(gcd)
        // To simplify all "2" factors of q, dividing by 2^k
        k = q.lowestSetBit
        q = q.shiftRight(k)
        // To simplify all "5" factors of q, dividing by 5^l
        do {
            quotAndRem = q.divideAndRemainder(FIVE_POW[i]!!)
            if (quotAndRem!![1].signum() == 0) {
                l += i
                if (i < lastPow) {
                    i++
                }
                q = quotAndRem[0]
            } else {
                if (i == 1) {
                    break
                }
                i = 1
            }
        } while (true)
        // If  abs(q) != 1  then the quotient is periodic
        if (!q.abs().equals(BigInteger.ONE)) {
            throw ArithmeticException("Non-terminating decimal expansion; no exact representable decimal result")
        }
        // The sign of the is fixed and the quotient will be saved in 'p'
        if (q.signum() < 0) {
            p = p.negate()
        }
        // Checking if the new scale is out of range
        newScale = safeLongToInt(diffScale + max(k, l))
        // k >= 0  and  l >= 0  implies that  k - l  is in the 32-bit range
        i = k - l
        p = if (i > 0) kendy.math.Multiplication.multiplyByFivePow(p, i) else p.shiftLeft(-i)
        return BigDecimal(p, newScale)
    }

    /**
     * Returns a new `BigDecimal` whose value is `this / divisor`.
     * The result is rounded according to the passed context `mc`. If the
     * passed math context specifies precision `0`, then this call is
     * equivalent to `this.divide(divisor)`.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `this / divisor`.
     * @throws NullPointerException
     * if `divisor == null` or `mc == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `mc.roundingMode() == UNNECESSARY` and rounding
     * is necessary according `mc.precision`.
     */
    fun divide(divisor: BigDecimal?, mc: kendy.math.MathContext?): BigDecimal {
        /* Calculating how many zeros must be append to 'dividend'
         * to obtain a  quotient with at least 'mc.precision()' digits */
        val trailingZeros: Long = (mc!!.precision + 2L
                + divisor!!.approxPrecision()) - approxPrecision()
        val diffScale = scale.toLong() - divisor.scale
        var newScale = diffScale // scale of the final quotient
        val compRem: Int // to compare the remainder
        var i = 1 // index
        val lastPow = TEN_POW.size - 1 // last power of ten
        var integerQuot: BigInteger // for temporal results
        var quotAndRem: Array<BigInteger> = arrayOf(
            unscaledValue
        )
        // In special cases it reduces the problem to call the dual method
        if (mc!!.precision == 0 || isZero
            || divisor.isZero
        ) {
            return this.divide(divisor)
        }
        if (trailingZeros > 0) {
            // To append trailing zeros at end of dividend
            quotAndRem!![0] =
                unscaledValue!!.multiply(kendy.math.Multiplication.powerOf10(trailingZeros))
            newScale += trailingZeros
        }
        quotAndRem = quotAndRem!![0].divideAndRemainder(divisor.unscaledValue!!)
        integerQuot = quotAndRem!![0]
        // Calculating the exact quotient with at least 'mc.precision()' digits
        if (quotAndRem[1].signum() != 0) {
            // Checking if:   2 * remainder >= divisor ?
            compRem = quotAndRem[1].shiftLeftOneBit().compareTo(divisor.unscaledValue)
            // quot := quot * 10 + r;     with 'r' in {-6,-5,-4, 0,+4,+5,+6}
            integerQuot = integerQuot.multiply(BigInteger.TEN)
                .add(BigInteger.valueOf((quotAndRem[0].signum() * (5 + compRem)).toLong()))
            newScale++
        } else {
            // To strip trailing zeros until the preferred scale is reached
            while (!integerQuot.testBit(0)) {
                quotAndRem = integerQuot.divideAndRemainder(TEN_POW[i]!!)
                if (quotAndRem!![1].signum() == 0
                    && newScale - i >= diffScale
                ) {
                    newScale -= i.toLong()
                    if (i < lastPow) {
                        i++
                    }
                    integerQuot = quotAndRem[0]
                } else {
                    if (i == 1) {
                        break
                    }
                    i = 1
                }
            }
        }
        // To perform rounding
        return BigDecimal(integerQuot, safeLongToInt(newScale), mc)
    }

    /**
     * Returns a new `BigDecimal` whose value is the integral part of
     * `this / divisor`. The quotient is rounded down towards zero to the
     * next integer. For example, `0.5/0.2 = 2`.
     *
     * @param divisor
     * value by which `this` is divided.
     * @return integral part of `this / divisor`.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     */
    fun divideToIntegralValue(divisor: BigDecimal): BigDecimal {
        var integralValue: BigInteger // the integer of result
        val powerOfTen: BigInteger // some power of ten
        var newScale = scale.toLong() - divisor.scale
        var tempScale: Long = 0
        var i = 1
        val lastPow = TEN_POW.size - 1
        if (divisor.isZero) {
            throw ArithmeticException("Division by zero")
        }
        if (divisor.approxPrecision() + newScale > approxPrecision() + 1L
            || isZero
        ) {
            /* If the divisor's integer part is greater than this's integer part,
             * the result must be zero with the appropriate scale */
            integralValue = BigInteger.ZERO
        } else if (newScale == 0L) {
            integralValue = unscaledValue!!.divide(divisor.unscaledValue!!)
        } else if (newScale > 0) {
            powerOfTen = kendy.math.Multiplication.powerOf10(newScale)!!
            integralValue = unscaledValue!!.divide(divisor.unscaledValue!!.multiply(powerOfTen))
            integralValue = integralValue.multiply(powerOfTen)
        } else { // (newScale < 0)
            powerOfTen = kendy.math.Multiplication.powerOf10(-newScale)
            integralValue = unscaledValue!!.multiply(powerOfTen).divide(divisor.unscaledValue!!)
            // To strip trailing zeros approximating to the preferred scale
            while (!integralValue.testBit(0)) {
                val quotAndRem = integralValue.divideAndRemainder(TEN_POW[i]!!)
                if (quotAndRem!![1].signum() == 0
                    && tempScale - i >= newScale
                ) {
                    tempScale -= i.toLong()
                    if (i < lastPow) {
                        i++
                    }
                    integralValue = quotAndRem[0]
                } else {
                    if (i == 1) {
                        break
                    }
                    i = 1
                }
            }
            newScale = tempScale
        }
        return if (integralValue.signum() == 0) zeroScaledBy(newScale) else BigDecimal(
            integralValue,
            safeLongToInt(newScale)
        )
    }

    /**
     * Returns a new `BigDecimal` whose value is the integral part of
     * `this / divisor`. The quotient is rounded down towards zero to the
     * next integer. The rounding mode passed with the parameter `mc` is
     * not considered. But if the precision of `mc > 0` and the integral
     * part requires more digits, then an `ArithmeticException` is thrown.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param mc
     * math context which determines the maximal precision of the
     * result.
     * @return integral part of `this / divisor`.
     * @throws NullPointerException
     * if `divisor == null` or `mc == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `mc.precision > 0` and the result requires more
     * digits to be represented.
     */
    fun divideToIntegralValue(divisor: BigDecimal?, mc: MathContext?): BigDecimal {
        val mcPrecision: Int = mc!!.precision
        val diffPrecision = precision() - divisor!!.precision()
        val lastPow = TEN_POW.size - 1
        val diffScale = scale.toLong() - divisor.scale
        var newScale = diffScale
        val quotPrecision = diffPrecision - diffScale + 1
        var quotAndRem = Array<BigInteger>(2) { BigInteger.valueOf(0) }
        // In special cases it call the dual method
        if (mcPrecision == 0 || isZero || divisor.isZero) {
            return this.divideToIntegralValue(divisor)
        }
        // Let be:   this = [u1,s1]   and   divisor = [u2,s2]
        if (quotPrecision <= 0) {
            quotAndRem[0] = BigInteger.ZERO
        } else if (diffScale == 0L) {
            // CASE s1 == s2:  to calculate   u1 / u2
            quotAndRem[0] = unscaledValue.divide(divisor.unscaledValue)
        } else if (diffScale > 0) {
            // CASE s1 >= s2:  to calculate   u1 / (u2 * 10^(s1-s2)
            quotAndRem[0] = unscaledValue.divide(
                divisor.unscaledValue.multiply(kendy.math.Multiplication.powerOf10(diffScale))
            )
            // To chose  10^newScale  to get a quotient with at least 'mc.precision()' digits
            newScale = min(
                diffScale,
                max(mcPrecision - quotPrecision + 1, 0)
            )
            // To calculate: (u1 / (u2 * 10^(s1-s2)) * 10^newScale
            quotAndRem[0] = quotAndRem[0].multiply(kendy.math.Multiplication.powerOf10(newScale))
        } else { // CASE s2 > s1:
            /* To calculate the minimum power of ten, such that the quotient
             *   (u1 * 10^exp) / u2   has at least 'mc.precision()' digits. */
            var exp: Long = min(
                -diffScale,
                max(mcPrecision.toLong() - diffPrecision, 0)
            )
            var compRemDiv: Long
            // Let be:   (u1 * 10^exp) / u2 = [q,r]
            quotAndRem = unscaledValue.multiply(kendy.math.Multiplication.powerOf10(exp))
                .divideAndRemainder(
                    divisor.unscaledValue
                )
            newScale += exp // To fix the scale
            exp = -newScale // The remaining power of ten
            // If after division there is a remainder...
            if (quotAndRem!![1].signum() != 0 && exp > 0) {
                // Log10(r) + ((s2 - s1) - exp) > mc.precision ?
                compRemDiv = BigDecimal(quotAndRem[1]).precision().toLong()
                +exp - divisor.precision()
                if (compRemDiv == 0L) {
                    // To calculate:  (r * 10^exp2) / u2
                    quotAndRem[1] =
                        quotAndRem[1].multiply(kendy.math.Multiplication.powerOf10(exp)).divide(
                            divisor.unscaledValue!!
                        )
                    compRemDiv = abs(quotAndRem[1].signum()).toLong()
                }
                if (compRemDiv > 0) {
                    throw ArithmeticException("Division impossible")
                }
            }
        }
        // Fast return if the quotient is zero
        if (quotAndRem[0]!!.signum() == 0) {
            return zeroScaledBy(diffScale)
        }
        var strippedBI = quotAndRem[0]
        val integralValue = BigDecimal(quotAndRem[0])
        var resultPrecision = integralValue.precision().toLong()
        var i = 1
        // To strip trailing zeros until the specified precision is reached
        while (!strippedBI!!.testBit(0)) {
            quotAndRem = strippedBI.divideAndRemainder(TEN_POW[i])
            if (quotAndRem!![1].signum() == 0 &&
                (resultPrecision - i >= mcPrecision
                        || newScale - i >= diffScale)
            ) {
                resultPrecision -= i.toLong()
                newScale -= i.toLong()
                if (i < lastPow) {
                    i++
                }
                strippedBI = quotAndRem[0]
            } else {
                if (i == 1) {
                    break
                }
                i = 1
            }
        }
        // To check if the result fit in 'mc.precision()' digits
        if (resultPrecision > mcPrecision) {
            throw ArithmeticException("Division impossible")
        }
        integralValue.scale = safeLongToInt(newScale)
        integralValue.unscaledValue = strippedBI
        return integralValue
    }

    /**
     * Returns a new `BigDecimal` whose value is `this % divisor`.
     *
     *
     * The remainder is defined as `this -
     * this.divideToIntegralValue(divisor) * divisor`.
     *
     * @param divisor
     * value by which `this` is divided.
     * @return `this % divisor`.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     */
    fun remainder(divisor: BigDecimal?): BigDecimal {
        return divideAndRemainder(divisor!!)[1]
    }

    /**
     * Returns a new `BigDecimal` whose value is `this % divisor`.
     *
     *
     * The remainder is defined as `this -
     * this.divideToIntegralValue(divisor) * divisor`.
     *
     *
     * The specified rounding mode `mc` is used for the division only.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param mc
     * rounding mode and precision to be used.
     * @return `this % divisor`.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @throws ArithmeticException
     * if `mc.precision > 0` and the result of `this.divideToIntegralValue(divisor, mc)` requires more digits
     * to be represented.
     */
    fun remainder(divisor: BigDecimal?, mc: kendy.math.MathContext?): BigDecimal {
        return divideAndRemainder(divisor, mc)[1]
    }

    /**
     * Returns a `BigDecimal` array which contains the integral part of
     * `this / divisor` at index 0 and the remainder `this %
     * divisor` at index 1. The quotient is rounded down towards zero to the
     * next integer.
     *
     * @param divisor
     * value by which `this` is divided.
     * @return `[this.divideToIntegralValue(divisor),
     * this.remainder(divisor)]`.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @see .divideToIntegralValue
     *
     * @see .remainder
     */
    fun divideAndRemainder(divisor: BigDecimal): Array<BigDecimal> {
        val q: BigDecimal = this.divideToIntegralValue(divisor)
        val r: BigDecimal = this.subtract(q!!.multiply(divisor))

        val quotAndRem = arrayOf(q, r)

        return quotAndRem
    }

    /**
     * Returns a `BigDecimal` array which contains the integral part of
     * `this / divisor` at index 0 and the remainder `this %
     * divisor` at index 1. The quotient is rounded down towards zero to the
     * next integer. The rounding mode passed with the parameter `mc` is
     * not considered. But if the precision of `mc > 0` and the integral
     * part requires more digits, then an `ArithmeticException` is thrown.
     *
     * @param divisor
     * value by which `this` is divided.
     * @param mc
     * math context which determines the maximal precision of the
     * result.
     * @return `[this.divideToIntegralValue(divisor),
     * this.remainder(divisor)]`.
     * @throws NullPointerException
     * if `divisor == null`.
     * @throws ArithmeticException
     * if `divisor == 0`.
     * @see .divideToIntegralValue
     *
     * @see .remainder
     */
    fun divideAndRemainder(divisor: BigDecimal?, mc: kendy.math.MathContext?): Array<BigDecimal> {
        val q: BigDecimal = this.divideToIntegralValue(divisor, mc)
        val r: BigDecimal = this.subtract(q.multiply(divisor))

        val quotAndRem = arrayOf(q, r)

        return quotAndRem
    }

    /**
     * Returns a new `BigDecimal` whose value is `this<sup>n</sup>`. The
     * scale of the result is `n * this.scale()`.
     *
     *
     * `x.pow(0)` returns `1`, even if `x == 0`.
     *
     *
     * Implementation Note: The implementation is based on the ANSI standard
     * X3.274-1996 algorithm.
     *
     * @throws ArithmeticException
     * if `n < 0` or `n > 999999999`.
     */
    fun pow(n: Int): BigDecimal {
        if (n == 0) {
            return ONE
        }
        if (n < 0 || n > 999999999) {
            throw ArithmeticException("Invalid operation")
        }
        val newScale = scale * n.toLong()
        // Let be: this = [u,s]   so:  this^n = [u^n, s*n]
        return if (isZero) zeroScaledBy(newScale) else BigDecimal(
            unscaledValue!!.pow(n), safeLongToInt(newScale)
        )
    }

    /**
     * Returns a new `BigDecimal` whose value is `this<sup>n</sup>`. The
     * result is rounded according to the passed context `mc`.
     *
     *
     * Implementation Note: The implementation is based on the ANSI standard
     * X3.274-1996 algorithm.
     *
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @throws ArithmeticException
     * if `n < 0` or `n > 999999999`.
     */
    fun pow(n: Int, mc: kendy.math.MathContext): BigDecimal? {
        // The ANSI standard X3.274-1996 algorithm
        val m: Int = abs(n)
        val mcPrecision: Int = mc.precision
        val elength = log10(m.toDouble()).toInt() + 1 // decimal digits in 'n'
        var oneBitMask: Int // mask of bits
        var accum: BigDecimal? // the single accumulator
        var newPrecision: kendy.math.MathContext = mc // MathContext by default

        // In particular cases, it reduces the problem to call the other 'pow()'
        if (n == 0 || isZero && n > 0) {
            return pow(n)
        }
        if (m > 999999999 || mcPrecision == 0 && n < 0
            || mcPrecision > 0 && elength > mcPrecision
        ) {
            throw ArithmeticException("Invalid operation")
        }
        if (mcPrecision > 0) {
            newPrecision = kendy.math.MathContext(
                mcPrecision + elength + 1,
                mc.roundingMode
            )
        }
        // The result is calculated as if 'n' were positive
        accum = round(newPrecision)
        oneBitMask = m.takeHighestOneBit() shr 1
        while (oneBitMask > 0) {
            accum = accum!!.multiply(accum, newPrecision)
            if (m and oneBitMask == oneBitMask) {
                accum = accum!!.multiply(this, newPrecision)
            }
            oneBitMask = oneBitMask shr 1
        }
        // If 'n' is negative, the value is divided into 'ONE'
        if (n < 0) {
            accum = ONE.divide(accum, newPrecision)
        }
        // The final value is rounded to the destination precision
        accum!!.inplaceRound(mc)
        return accum
    }

    /**
     * Returns a `BigDecimal` whose value is the absolute value of
     * `this`. The scale of the result is the same as the scale of this.
     */
    fun abs(): BigDecimal {
        return if (signum() < 0) negate() else this
    }

    /**
     * Returns a `BigDecimal` whose value is the absolute value of
     * `this`. The result is rounded according to the passed context
     * `mc`.
     */
    fun abs(mc: kendy.math.MathContext): BigDecimal {
        val result = if (signum() < 0) negate() else BigDecimal(
            unscaledValue, scale
        )
        result!!.inplaceRound(mc)
        return result
    }

    /**
     * Returns a new `BigDecimal` whose value is the `-this`. The
     * scale of the result is the same as the scale of this.
     *
     * @return `-this`
     */
    fun negate(): BigDecimal {
        return if (bitLength < 63 || bitLength == 63 && smallValue != Long.MIN_VALUE) {
            valueOf(-smallValue, scale)
        } else BigDecimal(unscaledValue!!.negate(), scale)
    }

    /**
     * Returns a new `BigDecimal` whose value is the `-this`. The
     * result is rounded according to the passed context `mc`.
     *
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `-this`
     */
    fun negate(mc: kendy.math.MathContext): BigDecimal {
        val result = negate()
        result!!.inplaceRound(mc)
        return result
    }

    /**
     * Returns a new `BigDecimal` whose value is `+this`. The scale
     * of the result is the same as the scale of this.
     *
     * @return `this`
     */
    fun plus(): BigDecimal {
        return this
    }

    /**
     * Returns a new `BigDecimal` whose value is `+this`. The result
     * is rounded according to the passed context `mc`.
     *
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `this`, rounded
     */
    operator fun plus(mc: kendy.math.MathContext): BigDecimal {
        return round(mc)
    }

    /**
     * Returns the sign of this `BigDecimal`.
     *
     * @return `-1` if `this < 0`,
     * `0` if `this == 0`,
     * `1` if `this > 0`.
     */
    fun signum(): Int {
        return if (bitLength < 64) {
            smallValue.sign
        } else unscaledValue!!.signum()
    }

    //Watch out: -1 has a bitLength=0
    private val isZero: Boolean
        private get() =//Watch out: -1 has a bitLength=0
            bitLength == 0 && smallValue != -1L

    /**
     * Returns the scale of this `BigDecimal`. The scale is the number of
     * digits behind the decimal point. The value of this `BigDecimal` is
     * the `unsignedValue * 10<sup>-scale</sup>`. If the scale is negative,
     * then this `BigDecimal` represents a big integer.
     *
     * @return the scale of this `BigDecimal`.
     */
    fun scale(): Int {
        return scale
    }

    /**
     * Returns the precision of this `BigDecimal`. The precision is the
     * number of decimal digits used to represent this decimal. It is equivalent
     * to the number of digits of the unscaled value. The precision of `0`
     * is `1` (independent of the scale).
     *
     * @return the precision of this `BigDecimal`.
     */
    fun precision(): Int {
        // Return the cached value if we have one.
        if (precision != 0) {
            return precision
        }
        precision = if (bitLength == 0) {
            1
        } else if (bitLength < 64) {
            decimalDigitsInLong(smallValue)
        } else {
            var decimalDigits = 1 + ((bitLength - 1) * LOG10_2).toInt()
            // If after division the number isn't zero, there exists an additional digit
            if (unscaledValue!!.divide(kendy.math.Multiplication.powerOf10(decimalDigits.toLong()))
                    .signum() != 0
            ) {
                decimalDigits++
            }
            decimalDigits
        }
        return precision
    }

    private fun decimalDigitsInLong(value: Long): Int {
        if (value == Long.MIN_VALUE)
            return 19 // special case required because abs(MIN_VALUE) == MIN_VALUE

        var digits = 0
        var v = abs(value)
        while (v >= 1000L) {
            v /= 1000L
            digits += 3
        }
        while (v >= 10L) {
            v /= 10L
            digits++
        }
        if (v > 0)
            digits++

        return digits
    }

    /**
     * Returns the unscaled value (mantissa) of this `BigDecimal` instance
     * as a `BigInteger`. The unscaled value can be computed as
     * `this * 10<sup>scale</sup>`.
     */
    fun unscaledValue(): BigInteger {
        return unscaledValue
    }

    /**
     * Returns a new `BigDecimal` whose value is `this`, rounded
     * according to the passed context `mc`.
     *
     *
     * If `mc.precision = 0`, then no rounding is performed.
     *
     *
     * If `mc.precision > 0` and `mc.roundingMode == UNNECESSARY`,
     * then an `ArithmeticException` is thrown if the result cannot be
     * represented exactly within the given precision.
     *
     * @param mc
     * rounding mode and precision for the result of this operation.
     * @return `this` rounded according to the passed context.
     * @throws ArithmeticException
     * if `mc.precision > 0` and `mc.roundingMode ==
     * UNNECESSARY` and this cannot be represented within the given
     * precision.
     */
    fun round(mc: kendy.math.MathContext?): BigDecimal {
        val thisBD: BigDecimal = BigDecimal(unscaledValue, scale)
        thisBD.inplaceRound(mc!!)
        return thisBD
    }

    /**
     * Returns a new `BigDecimal` instance with the specified scale.
     *
     *
     * If the new scale is greater than the old scale, then additional zeros are
     * added to the unscaled value. In this case no rounding is necessary.
     *
     *
     * If the new scale is smaller than the old scale, then trailing digits are
     * removed. If these trailing digits are not zero, then the remaining
     * unscaled value has to be rounded. For this rounding operation the
     * specified rounding mode is used.
     *
     * @param newScale
     * scale of the result returned.
     * @param roundingMode
     * rounding mode to be used to round the result.
     * @return a new `BigDecimal` instance with the specified scale.
     * @throws NullPointerException
     * if `roundingMode == null`.
     * @throws ArithmeticException
     * if `roundingMode == ROUND_UNNECESSARY` and rounding is
     * necessary according to the given scale.
     */
    fun setScale(newScale: Int, roundingMode: RoundingMode?): BigDecimal {
        if (roundingMode == null) {
            throw NullPointerException("roundingMode == null")
        }
        val diffScale = newScale - scale.toLong()
        // Let be:  'this' = [u,s]
        if (diffScale == 0L) {
            return this
        }
        if (diffScale > 0) {
            // return  [u * 10^(s2 - s), newScale]
            return if (diffScale < MathUtils.LONG_POWERS_OF_TEN.size &&
                bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[diffScale.toInt()] < 64
            ) {
                valueOf(smallValue * MathUtils.LONG_POWERS_OF_TEN[diffScale.toInt()], newScale)
            } else BigDecimal(
                kendy.math.Multiplication.multiplyByTenPow(
                    unscaledValue,
                    diffScale
                ), newScale
            )
        }
        // diffScale < 0
        // return  [u,s] / [1,newScale]  with the appropriate scale and rounding
        return if (bitLength < 64 && -diffScale < MathUtils.LONG_POWERS_OF_TEN.size) {
            dividePrimitiveLongs(
                smallValue,
                MathUtils.LONG_POWERS_OF_TEN[(-diffScale).toInt()],
                newScale,
                roundingMode
            )
        } else divideBigIntegers(
            unscaledValue!!,
            kendy.math.Multiplication.powerOf10(-diffScale),
            newScale,
            roundingMode
        )
    }

    /**
     * Returns a new `BigDecimal` instance with the specified scale.
     *
     *
     * If the new scale is greater than the old scale, then additional zeros are
     * added to the unscaled value. In this case no rounding is necessary.
     *
     *
     * If the new scale is smaller than the old scale, then trailing digits are
     * removed. If these trailing digits are not zero, then the remaining
     * unscaled value has to be rounded. For this rounding operation the
     * specified rounding mode is used.
     *
     * @param newScale
     * scale of the result returned.
     * @param roundingMode
     * rounding mode to be used to round the result.
     * @return a new `BigDecimal` instance with the specified scale.
     * @throws IllegalArgumentException
     * if `roundingMode` is not a valid rounding mode.
     * @throws ArithmeticException
     * if `roundingMode == ROUND_UNNECESSARY` and rounding is
     * necessary according to the given scale.
     */
    fun setScale(newScale: Int, roundingMode: Int): BigDecimal? {
        return setScale(newScale, RoundingMode.valueOf(roundingMode))
    }

    /**
     * Returns a new `BigDecimal` instance with the specified scale. If
     * the new scale is greater than the old scale, then additional zeros are
     * added to the unscaled value. If the new scale is smaller than the old
     * scale, then trailing zeros are removed. If the trailing digits are not
     * zeros then an ArithmeticException is thrown.
     *
     *
     * If no exception is thrown, then the following equation holds: `x.setScale(s).compareTo(x) == 0`.
     *
     * @param newScale
     * scale of the result returned.
     * @return a new `BigDecimal` instance with the specified scale.
     * @throws ArithmeticException
     * if rounding would be necessary.
     */
    fun setScale(newScale: Int): BigDecimal? {
        return setScale(newScale, RoundingMode.UNNECESSARY)
    }

    /**
     * Returns a new `BigDecimal` instance where the decimal point has
     * been moved `n` places to the left. If `n < 0` then the
     * decimal point is moved `-n` places to the right.
     *
     *
     * The result is obtained by changing its scale. If the scale of the result
     * becomes negative, then its precision is increased such that the scale is
     * zero.
     *
     *
     * Note, that `movePointLeft(0)` returns a result which is
     * mathematically equivalent, but which has `scale >= 0`.
     */
    fun movePointLeft(n: Int): BigDecimal? {
        return movePoint(scale + n.toLong())
    }

    private fun movePoint(newScale: Long): BigDecimal? {
        if (isZero) {
            return zeroScaledBy(max(newScale, 0))
        }
        /*
         * When: 'n'== Integer.MIN_VALUE isn't possible to call to
         * movePointRight(-n) since -Integer.MIN_VALUE == Integer.MIN_VALUE
         */if (newScale >= 0) {
            return if (bitLength < 64) {
                valueOf(smallValue, safeLongToInt(newScale))
            } else BigDecimal(unscaledValue, safeLongToInt(newScale))
        }
        return if (-newScale < MathUtils.LONG_POWERS_OF_TEN.size &&
            bitLength + LONG_POWERS_OF_TEN_BIT_LENGTH[(-newScale).toInt()] < 64
        ) {
            valueOf(smallValue * MathUtils.LONG_POWERS_OF_TEN[(-newScale).toInt()], 0)
        } else BigDecimal(
            kendy.math.Multiplication.multiplyByTenPow(
                unscaledValue, safeLongToInt(-newScale).toLong()
            ), 0
        )
    }

    /**
     * Returns a new `BigDecimal` instance where the decimal point has
     * been moved `n` places to the right. If `n < 0` then the
     * decimal point is moved `-n` places to the left.
     *
     *
     * The result is obtained by changing its scale. If the scale of the result
     * becomes negative, then its precision is increased such that the scale is
     * zero.
     *
     *
     * Note, that `movePointRight(0)` returns a result which is
     * mathematically equivalent, but which has scale >= 0.
     */
    fun movePointRight(n: Int): BigDecimal? {
        return movePoint(scale - n.toLong())
    }

    /**
     * Returns a new `BigDecimal` whose value is `this * 10<sup>n</sup>`.
     * The scale of the result is `this.scale()` - `n`.
     * The precision of the result is the precision of `this`.
     *
     *
     * This method has the same effect as [.movePointRight], except that
     * the precision is not changed.
     */
    fun scaleByPowerOfTen(n: Int): BigDecimal {
        val newScale = scale - n.toLong()
        return if (bitLength < 64) {
            //Taking care when a 0 is to be scaled
            if (smallValue == 0L) {
                zeroScaledBy(newScale)
            } else valueOf(
                smallValue,
                safeLongToInt(newScale)
            )
        } else BigDecimal(
            unscaledValue,
            safeLongToInt(newScale)
        )
    }

    /**
     * Returns a new `BigDecimal` instance with the same value as `this` but with a unscaled value where the trailing zeros have been
     * removed. If the unscaled value of `this` has n trailing zeros, then
     * the scale and the precision of the result has been reduced by n.
     *
     * @return a new `BigDecimal` instance equivalent to this where the
     * trailing zeros of the unscaled value have been removed.
     */
    fun stripTrailingZeros(): BigDecimal {
        var i = 1 // 1 <= i <= 18
        val lastPow = TEN_POW.size - 1
        var newScale = scale.toLong()
        if (isZero) {
            return BigDecimal(BigInteger.ZERO, 0)
        }
        var strippedBI = unscaledValue!!
        var quotAndRem: Array<BigInteger>?

        // while the number is even...
        while (!strippedBI.testBit(0)) {
            // To divide by 10^i
            quotAndRem = strippedBI.divideAndRemainder(TEN_POW[i])
            // To look the remainder
            if (quotAndRem!![1].signum() == 0) {
                // To adjust the scale
                newScale -= i.toLong()
                if (i < lastPow) {
                    // To set to the next power
                    i++
                }
                strippedBI = quotAndRem[0]
            } else {
                if (i == 1) {
                    // 'this' has no more trailing zeros
                    break
                }
                // To set to the smallest power of ten
                i = 1
            }
        }
        return BigDecimal(strippedBI, safeLongToInt(newScale))
    }

    /**
     * Compares this `BigDecimal` with `val`. Returns one of the
     * three values `1`, `0`, or `-1`. The method behaves as
     * if `this.subtract(val)` is computed. If this difference is > 0 then
     * 1 is returned, if the difference is < 0 then -1 is returned, and if the
     * difference is 0 then 0 is returned. This means, that if two decimal
     * instances are compared which are equal in value but differ in scale, then
     * these two instances are considered as equal.
     *
     * @param val
     * value to be compared with `this`.
     * @return `1` if `this > val`, `-1` if `this < val`,
     * `0` if `this == val`.
     * @throws NullPointerException
     * if `val == null`.
     */
    override operator fun compareTo(`val`: BigDecimal?): Int {
        val thisSign = signum()
        val valueSign = `val`!!.signum()
        return if (thisSign == valueSign) {
            if (scale == `val`.scale && bitLength < 64 && `val`.bitLength < 64) {
                return if (smallValue < `val`.smallValue) -1 else if (smallValue > `val`.smallValue) 1 else 0
            }
            val diffScale = scale.toLong() - `val`.scale
            val diffPrecision = approxPrecision() - `val`.approxPrecision()
            if (diffPrecision > diffScale + 1) {
                thisSign
            } else if (diffPrecision < diffScale - 1) {
                -thisSign
            } else { // thisSign == val.signum()  and  diffPrecision is aprox. diffScale
                var thisUnscaled = unscaledValue
                var valUnscaled = `val`.unscaledValue
                // If any of both precision is bigger, append zeros to the shorter one
                if (diffScale < 0) {
                    thisUnscaled =
                        thisUnscaled.multiply(kendy.math.Multiplication.powerOf10(-diffScale))
                } else if (diffScale > 0) {
                    valUnscaled =
                        valUnscaled.multiply(kendy.math.Multiplication.powerOf10(diffScale))
                }
                thisUnscaled.compareTo(valUnscaled)
            }
        } else if (thisSign < valueSign) {
            -1
        } else {
            1
        }
    }

    /**
     * Returns `true` if `x` is a `BigDecimal` instance and if
     * this instance is equal to this big decimal. Two big decimals are equal if
     * their unscaled value and their scale is equal. For example, 1.0
     * (10*10<sup>-1</sup>) is not equal to 1.00 (100*10<sup>-2</sup>). Similarly, zero
     * instances are not equal if their scale differs.
     */
    override fun equals(x: Any?): Boolean {
        if (this === x) {
            return true
        }
        if (x is BigDecimal) {
            val x1 = x
            return x1.scale == scale && x1.bitLength == bitLength && if (bitLength < 64) x1.smallValue == smallValue else x1.intVal!!.equals(
                intVal
            )
        }
        return false
    }

    /**
     * Returns the minimum of this `BigDecimal` and `val`.
     *
     * @param val
     * value to be used to compute the minimum with this.
     * @return `min(this, val`.
     * @throws NullPointerException
     * if `val == null`.
     */
    fun min(`val`: BigDecimal): BigDecimal {
        return if (compareTo(`val`) <= 0) this else `val`
    }

    /**
     * Returns the maximum of this `BigDecimal` and `val`.
     *
     * @param val
     * value to be used to compute the maximum with this.
     * @return `max(this, val`.
     * @throws NullPointerException
     * if `val == null`.
     */
    fun max(`val`: BigDecimal): BigDecimal {
        return if (compareTo(`val`) >= 0) this else `val`
    }

    /**
     * Returns a hash code for this `BigDecimal`.
     *
     * @return hash code for `this`.
     */
    override fun hashCode(): Int {
        if (hashCode != 0) {
            return hashCode
        }
        if (bitLength < 64) {
            hashCode = (smallValue and -0x1).toInt()
            hashCode = 33 * hashCode + (smallValue shr 32 and -0x1).toInt()
            hashCode = 17 * hashCode + scale
            return hashCode
        }
        hashCode = 17 * intVal.hashCode() + scale
        return hashCode
    }

    /**
     * Returns a canonical string representation of this `BigDecimal`. If
     * necessary, scientific notation is used. This representation always prints
     * all significant digits of this value.
     *
     *
     * If the scale is negative or if `scale - precision >= 6` then
     * scientific notation is used.
     *
     * @return a string representation of `this` in scientific notation if
     * necessary.
     */
    override fun toString(): String {
        if (toStringImage != null) {
            return toStringImage!!
        }
        if (bitLength < 32) {
            toStringImage = Conversion.toDecimalScaledString(smallValue, scale)
            return toStringImage!!
        }
        val intString = unscaledValue.toString()
        if (scale == 0) {
            return intString
        }
        val begin = if (unscaledValue!!.signum() < 0) 2 else 1
        var end = intString.length
        val exponent = (-scale).toLong() + end - begin
        val result = StringBuilder()
        result.append(intString)
        if (scale > 0 && exponent >= -6) {
            if (exponent >= 0) {
                result.insert(end - scale, '.')
            } else {
                result.insert(begin - 1, "0.")
                result.insertRange(begin + 1, CH_ZEROS, 0, (-exponent).toInt() - 1)
            }
        } else {
            if (end - begin >= 1) {
                result.insert(begin, '.')
                end++
            }
            result.insert(end, 'E')
            if (exponent > 0) {
                result.insert(++end, '+')
            }
            result.insert(++end, exponent.toString())
        }
        toStringImage = result.toString()
        return toStringImage!!
    }

    /**
     * Returns a string representation of this `BigDecimal`. This
     * representation always prints all significant digits of this value.
     *
     *
     * If the scale is negative or if `scale - precision >= 6` then
     * engineering notation is used. Engineering notation is similar to the
     * scientific notation except that the exponent is made to be a multiple of
     * 3 such that the integer part is >= 1 and < 1000.
     *
     * @return a string representation of `this` in engineering notation
     * if necessary.
     */
    fun toEngineeringString(): String {
        val intString = unscaledValue.toString()
        if (scale == 0) {
            return intString
        }
        var begin = if (unscaledValue!!.signum() < 0) 2 else 1
        var end = intString.length
        var exponent = (-scale).toLong() + end - begin
        val result = StringBuilder(intString)
        if (scale > 0 && exponent >= -6) {
            if (exponent >= 0) {
                result.insert(end - scale, '.')
            } else {
                result.insert(begin - 1, "0.")
                result.insertRange(begin + 1, CH_ZEROS, 0, (-exponent).toInt() - 1)
            }
        } else {
            val delta = end - begin
            var rem = (exponent % 3).toInt()
            if (rem != 0) {
                // adjust exponent so it is a multiple of three
                if (unscaledValue!!.signum() == 0) {
                    // zero value
                    rem = if (rem < 0) -rem else 3 - rem
                    exponent += rem.toLong()
                } else {
                    // nonzero value
                    rem = if (rem < 0) rem + 3 else rem
                    exponent -= rem.toLong()
                    begin += rem
                }
                if (delta < 3) {
                    for (i in rem - delta downTo 1) {
                        result.insert(end++, '0')
                    }
                }
            }
            if (end - begin >= 1) {
                result.insert(begin, '.')
                end++
            }
            if (exponent != 0L) {
                result.insert(end, 'E')
                if (exponent > 0) {
                    result.insert(++end, '+')
                }
                result.insert(++end, exponent.toString())
            }
        }
        return result.toString()
    }

    /**
     * Returns a string representation of this `BigDecimal`. No scientific
     * notation is used. This methods adds zeros where necessary.
     *
     *
     * If this string representation is used to create a new instance, this
     * instance is generally not identical to `this` as the precision
     * changes.
     *
     *
     * `x.equals(new BigDecimal(x.toPlainString())` usually returns
     * `false`.
     *
     *
     * `x.compareTo(new BigDecimal(x.toPlainString())` returns `0`.
     *
     * @return a string representation of `this` without exponent part.
     */
    fun toPlainString(): String {
        val intStr = unscaledValue.toString()
        if (scale == 0 || isZero && scale < 0) {
            return intStr
        }
        val begin = if (signum() < 0) 1 else 0
        var delta = scale
        // We take space for all digits, plus a possible decimal point, plus 'scale'
        val result = StringBuilder(intStr.length + 1 + abs(scale))
        if (begin == 1) {
            // If the number is negative, we insert a '-' character at front
            result.append('-')
        }
        if (scale > 0) {
            delta -= intStr.length - begin
            if (delta >= 0) {
                result.append("0.")
                // To append zeros after the decimal point
                while (delta > CH_ZEROS.size) {
                    result.append(CH_ZEROS)
                    delta -= CH_ZEROS.size
                }
                result.append(CH_ZEROS, 0, delta)
                result.append(intStr.substring(begin))
            } else {
                delta = begin - delta
                result.append(intStr.substring(begin, delta))
                result.append('.')
                result.append(intStr.substring(delta))
            }
        } else { // (scale <= 0)
            result.append(intStr.substring(begin))
            // To append trailing zeros
            while (delta < -CH_ZEROS.size) {
                result.append(CH_ZEROS)
                delta += CH_ZEROS.size
            }
            result.append(CH_ZEROS, 0, -delta)
        }
        return result.toString()
    }

    /**
     * Returns this `BigDecimal` as a big integer instance. A fractional
     * part is discarded.
     *
     * @return this `BigDecimal` as a big integer instance.
     */
    fun toBigInteger(): BigInteger {
        return if (scale == 0 || isZero) {
            unscaledValue!!
        } else if (scale < 0) {
            unscaledValue!!.multiply(kendy.math.Multiplication.powerOf10((-scale).toLong()))
        } else { // (scale > 0)
            unscaledValue!!.divide(kendy.math.Multiplication.powerOf10(scale.toLong()))
        }
    }

    /**
     * Returns this `BigDecimal` as a big integer instance if it has no
     * fractional part. If this `BigDecimal` has a fractional part, i.e.
     * if rounding would be necessary, an `ArithmeticException` is thrown.
     *
     * @return this `BigDecimal` as a big integer value.
     * @throws ArithmeticException
     * if rounding is necessary.
     */
    fun toBigIntegerExact(): BigInteger {
        return if (scale == 0 || isZero) {
            unscaledValue!!
        } else if (scale < 0) {
            unscaledValue!!.multiply(kendy.math.Multiplication.powerOf10((-scale).toLong()))
        } else { // (scale > 0)
            val integerAndFraction: Array<BigInteger>?
            // An optimization before do a heavy division
            if (scale > approxPrecision() || scale > unscaledValue!!.lowestSetBit) {
                throw ArithmeticException("Rounding necessary")
            }
            integerAndFraction =
                unscaledValue!!.divideAndRemainder(kendy.math.Multiplication.powerOf10(scale.toLong()))
            if (integerAndFraction!![1].signum() != 0) {
                // It exists a non-zero fractional part
                throw ArithmeticException("Rounding necessary")
            }
            integerAndFraction[0]
        }
    }

    /**
     * Returns this `BigDecimal` as an long value. Any fractional part is
     * discarded. If the integral part of `this` is too big to be
     * represented as an long, then `this % 2<sup>64</sup>` is returned.
     */
    override fun toLong(): Long {
        /*
         * If scale <= -64 there are at least 64 trailing bits zero in
         * 10^(-scale). If the scale is positive and very large the long value
         * could be zero.
         */
        return if (scale <= -64 || scale > approxPrecision()) 0L else toBigInteger().toLong()
    }

    /**
     * Returns this `BigDecimal` as a long value if it has no fractional
     * part and if its value fits to the int range ([-2<sup>63</sup>..2<sup>63</sup>-1]). If
     * these conditions are not met, an `ArithmeticException` is thrown.
     *
     * @throws ArithmeticException
     * if rounding is necessary or the number doesn't fit in a long.
     */
    fun longValueExact(): Long {
        return valueExact(64)
    }

    /**
     * Returns this `BigDecimal` as an int value. Any fractional part is
     * discarded. If the integral part of `this` is too big to be
     * represented as an int, then `this % 2<sup>32</sup>` is returned.
     */
    override fun toInt(): Int {
        /*
         * If scale <= -32 there are at least 32 trailing bits zero in
         * 10^(-scale). If the scale is positive and very large the long value
         * could be zero.
         */
        return if (scale <= -32 || scale > approxPrecision()) 0 else toBigInteger().toInt()
    }

    /**
     * Returns this `BigDecimal` as a int value if it has no fractional
     * part and if its value fits to the int range ([-2<sup>31</sup>..2<sup>31</sup>-1]). If
     * these conditions are not met, an `ArithmeticException` is thrown.
     *
     * @throws ArithmeticException
     * if rounding is necessary or the number doesn't fit in an int.
     */
    fun intValueExact(): Int {
        return valueExact(32).toInt()
    }

    /**
     * Returns this `BigDecimal` as a short value if it has no fractional
     * part and if its value fits to the short range ([-2<sup>15</sup>..2<sup>15</sup>-1]). If
     * these conditions are not met, an `ArithmeticException` is thrown.
     *
     * @throws ArithmeticException
     * if rounding is necessary of the number doesn't fit in a short.
     */
    fun shortValueExact(): Short {
        return valueExact(16).toShort()
    }

    /**
     * Returns this `BigDecimal` as a byte value if it has no fractional
     * part and if its value fits to the byte range ([-128..127]). If these
     * conditions are not met, an `ArithmeticException` is thrown.
     *
     * @throws ArithmeticException
     * if rounding is necessary or the number doesn't fit in a byte.
     */
    fun byteValueExact(): Byte {
        return valueExact(8).toByte()
    }

    override fun toByte(): Byte {
        return toInt().toByte()
    }

    override fun toChar(): Char {
        return toInt().toChar()
    }

    override fun toShort(): Short {
        return toInt().toShort()
    }

    /**
     * Returns this `BigDecimal` as a float value. If `this` is too
     * big to be represented as an float, then `Float.POSITIVE_INFINITY`
     * or `Float.NEGATIVE_INFINITY` is returned.
     *
     *
     * Note, that if the unscaled value has more than 24 significant digits,
     * then this decimal cannot be represented exactly in a float variable. In
     * this case the result is rounded.
     *
     *
     * For example, if the instance `x1 = new BigDecimal("0.1")` cannot be
     * represented exactly as a float, and thus `x1.equals(new
     * BigDecimal(x1.floatValue())` returns `false` for this case.
     *
     *
     * Similarly, if the instance `new BigDecimal(16777217)` is converted
     * to a float, the result is `1.6777216E`7.
     *
     * @return this `BigDecimal` as a float value.
     */
    override fun toFloat(): Float {
        /* A similar code like in doubleValue() could be repeated here,
         * but this simple implementation is quite efficient. */
        var floatResult = signum().toFloat()
        val powerOfTwo = bitLength - (scale / LOG10_2).toLong()
        if (powerOfTwo < -149 || floatResult == 0.0f) {
            // Cases which 'this' is very small
            floatResult *= 0.0f
        } else if (powerOfTwo > 129) {
            // Cases which 'this' is very large
            floatResult *= Float.POSITIVE_INFINITY
        } else {
            floatResult = toDouble().toFloat()
        }
        return floatResult
    }

    /**
     * Returns this `BigDecimal` as a double value. If `this` is too
     * big to be represented as an float, then `Double.POSITIVE_INFINITY`
     * or `Double.NEGATIVE_INFINITY` is returned.
     *
     *
     * Note, that if the unscaled value has more than 53 significant digits,
     * then this decimal cannot be represented exactly in a double variable. In
     * this case the result is rounded.
     *
     *
     * For example, if the instance `x1 = new BigDecimal("0.1")` cannot be
     * represented exactly as a double, and thus `x1.equals(new
     * BigDecimal(x1.doubleValue())` returns `false` for this case.
     *
     *
     * Similarly, if the instance `new BigDecimal(9007199254740993L)` is
     * converted to a double, the result is `9.007199254740992E15`.
     *
     *
     *
     * @return this `BigDecimal` as a double value.
     */
    override fun toDouble(): Double {
        val sign = signum()
        var exponent = 1076 // bias + 53
        val lowestSetBit: Int
        val discardedSize: Int
        val powerOfTwo = bitLength - (scale / LOG10_2).toLong()
        var bits: Long // IEEE-754 Standard
        var tempBits: Long // for temporal calculations
        var mantissa: BigInteger
        if (powerOfTwo < -1074 || sign == 0) {
            // Cases which 'this' is very small
            return sign * 0.0
        } else if (powerOfTwo > 1025) {
            // Cases which 'this' is very large
            return sign * Double.POSITIVE_INFINITY
        }
        mantissa = unscaledValue!!.abs()
        // Let be:  this = [u,s], with s > 0
        if (scale <= 0) {
            // mantissa = abs(u) * 10^s
            mantissa = mantissa.multiply(kendy.math.Multiplication.powerOf10(-scale.toLong()))
        } else { // (scale > 0)
            val quotAndRem: Array<BigInteger>?
            val powerOfTen: BigInteger = kendy.math.Multiplication.powerOf10(scale.toLong())
            val k = 100 - powerOfTwo.toInt()
            val compRem: Int
            if (k > 0) {
                /* Computing (mantissa * 2^k) , where 'k' is a enough big
                 * power of '2' to can divide by 10^s */
                mantissa = mantissa.shiftLeft(k)
                exponent -= k
            }
            // Computing (mantissa * 2^k) / 10^s
            quotAndRem = mantissa.divideAndRemainder(powerOfTen)
            // To check if the fractional part >= 0.5
            compRem = quotAndRem!![1].shiftLeftOneBit().compareTo(powerOfTen)
            // To add two rounded bits at end of mantissa
            mantissa = quotAndRem[0].shiftLeft(2).add(
                BigInteger.valueOf((compRem * (compRem + 3) / 2 + 1).toLong())
            )
            exponent -= 2
        }
        lowestSetBit = mantissa.lowestSetBit
        discardedSize = mantissa.bitLength() - 54
        if (discardedSize > 0) { // (n > 54)
            // mantissa = (abs(u) * 10^s) >> (n - 54)
            bits = mantissa.shiftRight(discardedSize).toLong()
            tempBits = bits
            // #bits = 54, to check if the discarded fraction produces a carry
            if (bits and 1 == 1L && lowestSetBit < discardedSize
                || bits and 3 == 3L
            ) {
                bits += 2
            }
        } else { // (n <= 54)
            // mantissa = (abs(u) * 10^s) << (54 - n)
            bits = mantissa.toLong() shl -discardedSize
            tempBits = bits
            // #bits = 54, to check if the discarded fraction produces a carry:
            if (bits and 3 == 3L) {
                bits += 2
            }
        }
        // Testing bit 54 to check if the carry creates a new binary digit
        if (bits and 0x40000000000000L == 0L) {
            // To drop the last bit of mantissa (first discarded)
            bits = bits shr 1
            // exponent = 2^(s-n+53+bias)
            exponent += discardedSize
        } else { // #bits = 54
            bits = bits shr 2
            exponent += discardedSize + 1
        }
        // To test if the 53-bits number fits in 'double'
        if (exponent > 2046) { // (exponent - bias > 1023)
            return sign * Double.POSITIVE_INFINITY
        } else if (exponent <= 0) { // (exponent - bias <= -1023)
            // Denormalized numbers (having exponent == 0)
            if (exponent < -53) { // exponent - bias < -1076
                return sign * 0.0
            }
            // -1076 <= exponent - bias <= -1023
            // To discard '- exponent + 1' bits
            bits = tempBits shr 1
            tempBits = bits and (-1L ushr 63 + exponent)
            bits = bits shr -exponent
            // To test if after discard bits, a new carry is generated
            if (bits and 3 == 3L || (bits and 1 == 1L && tempBits != 0L
                        && lowestSetBit < discardedSize)
            ) {
                bits += 1
            }
            exponent = 0
            bits = bits shr 1
        }
        // Construct the 64 double bits: [sign(1), exponent(11), mantissa(52)]
        bits = ((sign.toLong() and (1L shl 63)) or (exponent.toLong() shl 52)
                or (bits and 0xFFFFFFFFFFFFFL))
        return Double.fromBits(bits)
    }

    /**
     * Returns the unit in the last place (ULP) of this `BigDecimal`
     * instance. An ULP is the distance to the nearest big decimal with the same
     * precision.
     *
     *
     * The amount of a rounding error in the evaluation of a floating-point
     * operation is often expressed in ULPs. An error of 1 ULP is often seen as
     * a tolerable error.
     *
     *
     * For class `BigDecimal`, the ULP of a number is simply 10<sup>-scale</sup>.
     * For example, `new BigDecimal(0.1).ulp()` returns `1E-55`.
     *
     * @return unit in the last place (ULP) of this `BigDecimal` instance.
     */
    fun ulp(): BigDecimal? {
        return valueOf(1, scale)
    }
    /* Private Methods */
    /**
     * It does all rounding work of the public method
     * `round(MathContext)`, performing an inplace rounding
     * without creating a new object.
     *
     * @param mc
     * the `MathContext` for perform the rounding.
     * @see .round
     */
    private fun inplaceRound(mc: kendy.math.MathContext) {
        val mcPrecision: Int = mc.precision
        if (approxPrecision() < mcPrecision || mcPrecision == 0) {
            return
        }
        val discardedPrecision = precision() - mcPrecision
        // If no rounding is necessary it returns immediately
        if (discardedPrecision <= 0) {
            return
        }
        // When the number is small perform an efficient rounding
        if (bitLength < 64) {
            smallRound(mc, discardedPrecision)
            return
        }
        // Getting the integer part and the discarded fraction
        val sizeOfFraction: BigInteger =
            kendy.math.Multiplication.powerOf10(discardedPrecision.toLong())
        val integerAndFraction = unscaledValue!!.divideAndRemainder(sizeOfFraction)
        var newScale = scale.toLong() - discardedPrecision
        var compRem: Int
        val tempBD: BigDecimal
        // If the discarded fraction is non-zero, perform rounding
        if (integerAndFraction!![1].signum() != 0) {
            // To check if the discarded fraction >= 0.5
            compRem = integerAndFraction[1].abs().shiftLeftOneBit().compareTo(sizeOfFraction)
            // To look if there is a carry
            compRem = roundingBehavior(
                if (integerAndFraction[0].testBit(0)) 1 else 0,
                integerAndFraction[1].signum() * (5 + compRem),
                mc.roundingMode!!
            )
            if (compRem != 0) {
                integerAndFraction[0] =
                    integerAndFraction[0].add(BigInteger.valueOf(compRem.toLong()))
            }
            tempBD = BigDecimal(integerAndFraction[0])
            // If after to add the increment the precision changed, we normalize the size
            if (tempBD.precision() > mcPrecision) {
                integerAndFraction[0] = integerAndFraction[0].divide(BigInteger.TEN)
                newScale--
            }
        }
        // To update all internal fields
        scale = safeLongToInt(newScale)
        precision = mcPrecision
        unscaledValue = integerAndFraction[0]
    }

    /**
     * This method implements an efficient rounding for numbers which unscaled
     * value fits in the type `long`.
     *
     * @param mc
     * the context to use
     * @param discardedPrecision
     * the number of decimal digits that are discarded
     * @see .round
     */
    private fun smallRound(mc: kendy.math.MathContext, discardedPrecision: Int) {
        val sizeOfFraction = MathUtils.LONG_POWERS_OF_TEN[discardedPrecision]
        var newScale = scale.toLong() - discardedPrecision
        val unscaledVal = smallValue
        // Getting the integer part and the discarded fraction
        var integer = unscaledVal / sizeOfFraction
        val fraction = unscaledVal % sizeOfFraction
        val compRem: Int
        // If the discarded fraction is non-zero perform rounding
        if (fraction != 0L) {
            // To check if the discarded fraction >= 0.5
            compRem = compareForRounding(fraction, sizeOfFraction)
            // To look if there is a carry
            integer += roundingBehavior(
                integer.toInt() and 1,
                fraction.sign * (5 + compRem),
                mc.roundingMode!!
            ).toLong()
            // If after to add the increment the precision changed, we normalize the size
            if (log10(abs(integer).toDouble()) >= mc.precision) {
                integer /= 10
                newScale--
            }
        }
        // To update all internal fields
        scale = safeLongToInt(newScale)
        precision = mc.precision
        smallValue = integer
        bitLength = bitLength(integer)
        intVal = null
    }

    /**
     * If `intVal` has a fractional part throws an exception,
     * otherwise it counts the number of bits of value and checks if it's out of
     * the range of the primitive type. If the number fits in the primitive type
     * returns this number as `long`, otherwise throws an
     * exception.
     *
     * @param bitLengthOfType
     * number of bits of the type whose value will be calculated
     * exactly
     * @return the exact value of the integer part of `BigDecimal`
     * when is possible
     * @throws ArithmeticException when rounding is necessary or the
     * number don't fit in the primitive type
     */
    private fun valueExact(bitLengthOfType: Int): Long {
        val bigInteger = toBigIntegerExact()
        if (bigInteger.bitLength() < bitLengthOfType) {
            // It fits in the primitive type
            return bigInteger.toLong()
        }
        throw ArithmeticException("Rounding necessary")
    }

    /**
     * If the precision already was calculated it returns that value, otherwise
     * it calculates a very good approximation efficiently . Note that this
     * value will be `precision()` or `precision()-1`
     * in the worst case.
     *
     * @return an approximation of `precision()` value
     */
    private fun approxPrecision(): Int {
        return if (precision > 0) precision else ((bitLength - 1) * LOG10_2).toInt() + 1
    }

    /**
     * Assigns all transient fields upon deserialization of a
     * `BigDecimal` instance (bitLength and smallValue). The transient
     * field precision is assigned lazily.
     */
    /* TODO IOS
    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(`in`: java.io.ObjectInputStream) {
        `in`.defaultReadObject()
        bitLength = intVal!!.bitLength()
        if (bitLength < 64) {
            smallValue = intVal!!.toLong()
        }
    }
    */

    /**
     * Prepares this `BigDecimal` for serialization, i.e. the
     * non-transient field `intVal` is assigned.
     */
    /* TODO IOS
    @Throws(java.io.IOException::class)
    private fun writeObject(out: java.io.ObjectOutputStream) {
        unscaledValue
        out.defaultWriteObject()
    }
    */

    private var unscaledValue: BigInteger
        private get() {
            if (intVal == null) {
                intVal = BigInteger.valueOf(smallValue)
            }
            return intVal!!
        }
        private set(unscaledValue) {
            intVal = unscaledValue
            bitLength = unscaledValue.bitLength()
            if (bitLength < 64) {
                smallValue = unscaledValue.toLong()
            }
        }
}