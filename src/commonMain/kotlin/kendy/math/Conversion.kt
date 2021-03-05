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

import kendy.math.BitLevel.nonZeroDroppedBits
import kotlin.math.*

/**
 * Static library that provides [BigInteger] base conversion from/to any
 * integer represented in an [java.lang.String] Object.
 */
internal object Conversion {
    /**
     * Holds the maximal exponent for each radix, so that radix<sup>digitFitInInt[radix]</sup>
     * fit in an `int` (32 bits).
     */
    val digitFitInInt = intArrayOf(
        -1, -1, 31, 19, 15, 13, 11,
        11, 10, 9, 9, 8, 8, 8, 8, 7, 7, 7, 7, 7, 7, 7, 6, 6, 6, 6, 6, 6, 6,
        6, 6, 6, 6, 6, 6, 6, 5
    )

    /**
     * bigRadices values are precomputed maximal powers of radices (integer
     * numbers from 2 to 36) that fit into unsigned int (32 bits). bigRadices[0] =
     * 2 ^ 31, bigRadices[8] = 10 ^ 9, etc.
     */
    val bigRadices = intArrayOf(
        -2147483648, 1162261467,
        1073741824, 1220703125, 362797056, 1977326743, 1073741824,
        387420489, 1000000000, 214358881, 429981696, 815730721, 1475789056,
        170859375, 268435456, 410338673, 612220032, 893871739, 1280000000,
        1801088541, 113379904, 148035889, 191102976, 244140625, 308915776,
        387420489, 481890304, 594823321, 729000000, 887503681, 1073741824,
        1291467969, 1544804416, 1838265625, 60466176
    )

    /** @see BigInteger.toString
     */
    fun bigInteger2String(`val`: BigInteger, radix: Int): String {
        `val`.prepareJavaRepresentation()
        val sign = `val`.sign
        val numberLength = `val`.numberLength
        val digits = `val`.digits
        if (sign == 0) {
            return "0"
        }
        if (numberLength == 1) {
            val highDigit = digits[numberLength - 1]
            var v = highDigit.toLong() and 0xFFFFFFFFL
            if (sign < 0) {
                v = -v
            }
            return v.toString(radix)
        }
        if (radix == 10 // TODO IOS || radix < java.lang.Character.MIN_RADIX
            // TODO IOS || radix > java.lang.Character.MAX_RADIX
        ) {
            return `val`.toString()
        }
        val bitsForRadixDigit: Double
        bitsForRadixDigit = log2(radix.toDouble())
        val resLengthInChars =
            (`val`.abs().bitLength() / bitsForRadixDigit + if (sign < 0) 1 else 0).toInt() + 1
        val result = CharArray(resLengthInChars)
        var currentChar = resLengthInChars
        var resDigit: Int
        if (radix != 16) {
            val temp = IntArray(numberLength)
            digits.copyInto(temp, 0, 0, numberLength)
            var tempLen = numberLength
            val charsPerInt = digitFitInInt[radix]
            var i: Int
            // get the maximal power of radix that fits in int
            val bigRadix = bigRadices[radix - 2]
            while (true) {
                // divide the array of digits by bigRadix and convert remainders
                // to characters collecting them in the char array
                resDigit = kendy.math.Division.divideArrayByInt(
                    temp, temp, tempLen,
                    bigRadix
                )
                val previous = currentChar
                do {
                    result[--currentChar] = (resDigit % radix).toString(radix)[0]
                } while (radix.let { resDigit /= it; resDigit } != 0 && currentChar != 0)
                val delta = charsPerInt - previous + currentChar
                i = 0
                while (i < delta && currentChar > 0) {
                    result[--currentChar] = '0'
                    i++
                }
                i = tempLen - 1
                while (i > 0 && temp[i] == 0) {
                    i--
                }
                tempLen = i + 1
                if (tempLen == 1 && temp[0] == 0) { // the quotient is 0
                    break
                }
            }
        } else {
            // radix == 16
            for (i in 0 until numberLength) {
                var j = 0
                while (j < 8 && currentChar > 0) {
                    resDigit = digits[i] shr (j shl 2) and 0xf
                    result[--currentChar] = resDigit.toString(16)[0]
                    j++
                }
            }
        }
        while (result[currentChar] == '0') {
            currentChar++
        }
        if (sign == -1) {
            result[--currentChar] = '-'
        }
        return String(result, currentChar, resLengthInChars - currentChar)
    }

    /**
     * Builds the correspondent `String` representation of `val`
     * being scaled by `scale`.
     *
     * @see BigInteger.toString
     * @see BigDecimal.toString
     */
    fun toDecimalScaledString(`val`: BigInteger, scale: Int): String {
        `val`.prepareJavaRepresentation()
        val sign = `val`.sign
        val numberLength = `val`.numberLength
        val digits = `val`.digits
        val resLengthInChars: Int
        var currentChar: Int
        val result: CharArray
        if (sign == 0) {
            return when (scale) {
                0 -> "0"
                1 -> "0.0"
                2 -> "0.00"
                3 -> "0.000"
                4 -> "0.0000"
                5 -> "0.00000"
                6 -> "0.000000"
                else -> {
                    val result1 = StringBuilder()
                    if (scale < 0) {
                        result1.append("0E+")
                    } else {
                        result1.append("0E")
                    }
                    result1.append(-scale)
                    result1.toString()
                }
            }
        }
        // one 32-bit unsigned value may contains 10 decimal digits
        resLengthInChars = numberLength * 10 + 1 + 7
        // Explanation why +1+7:
        // +1 - one char for sign if needed.
        // +7 - For "special case 2" (see below) we have 7 free chars for
        // inserting necessary scaled digits.
        result = CharArray(resLengthInChars + 1)
        // allocated [resLengthInChars+1] characters.
        // a free latest character may be used for "special case 1" (see
        // below)
        currentChar = resLengthInChars
        if (numberLength == 1) {
            val highDigit = digits[0]
            if (highDigit < 0) {
                var v = highDigit.toLong() and 0xFFFFFFFFL
                do {
                    val prev = v
                    v /= 10
                    result[--currentChar] = (0x0030 + (prev - v * 10).toInt()).toChar()
                } while (v != 0L)
            } else {
                var v = highDigit
                do {
                    val prev = v
                    v /= 10
                    result[--currentChar] = (0x0030 + (prev - v * 10)).toChar()
                } while (v != 0)
            }
        } else {
            val temp = IntArray(numberLength)
            var tempLen = numberLength
            digits.copyInto(temp, 0, 0, tempLen)
            BIG_LOOP@ while (true) {
                // divide the array of digits by bigRadix and convert
                // remainders
                // to characters collecting them in the char array
                var result11: Long = 0
                for (i1 in tempLen - 1 downTo 0) {
                    val temp1 = ((result11 shl 32)
                            + (temp[i1].toLong() and 0xFFFFFFFFL))
                    val res = divideLongByBillion(temp1)
                    temp[i1] = res.toInt()
                    result11 = ((res shr 32) as Int).toLong()
                }
                var resDigit = result11.toInt()
                val previous = currentChar
                do {
                    result[--currentChar] = (0x0030 + resDigit % 10).toChar()
                } while (10.let { resDigit /= it; resDigit } != 0 && currentChar != 0)
                val delta = 9 - previous + currentChar
                var i = 0
                while (i < delta && currentChar > 0) {
                    result[--currentChar] = '0'
                    i++
                }
                var j = tempLen - 1
                while (temp[j] == 0) {
                    if (j == 0) { // means temp[0] == 0
                        break@BIG_LOOP
                    }
                    j--
                }
                tempLen = j + 1
            }
            while (result[currentChar] == '0') {
                currentChar++
            }
        }
        val negNumber = sign < 0
        val exponent = resLengthInChars - currentChar - scale - 1
        if (scale == 0) {
            if (negNumber) {
                result[--currentChar] = '-'
            }
            return String(
                result, currentChar, resLengthInChars
                        - currentChar
            )
        }
        if (scale > 0 && exponent >= -6) {
            if (exponent >= 0) {
                // special case 1
                var insertPoint = currentChar + exponent
                for (j in resLengthInChars - 1 downTo insertPoint) {
                    result[j + 1] = result[j]
                }
                result[++insertPoint] = '.'
                if (negNumber) {
                    result[--currentChar] = '-'
                }
                return String(
                    result, currentChar, resLengthInChars
                            - currentChar + 1
                )
            }
            // special case 2
            for (j in 2 until -exponent + 1) {
                result[--currentChar] = '0'
            }
            result[--currentChar] = '.'
            result[--currentChar] = '0'
            if (negNumber) {
                result[--currentChar] = '-'
            }
            return String(
                result, currentChar, resLengthInChars
                        - currentChar
            )
        }
        val startPoint = currentChar + 1
        val result1 = StringBuilder(16 + resLengthInChars - startPoint)
        if (negNumber) {
            result1.append('-')
        }
        if (resLengthInChars - startPoint >= 1) {
            result1.append(result[currentChar])
            result1.append('.')
            result1.append(
                result, currentChar + 1, resLengthInChars
                        - currentChar - 1
            )
        } else {
            result1.append(
                result, currentChar, resLengthInChars
                        - currentChar
            )
        }
        result1.append('E')
        if (exponent > 0) {
            result1.append('+')
        }
        result1.append(exponent.toString())
        return result1.toString()
    }

    /* can process only 32-bit numbers */
    fun toDecimalScaledString(value: Long, scale: Int): String {
        var value = value
        val resLengthInChars: Int
        var currentChar: Int
        val result: CharArray
        val negNumber = value < 0
        if (negNumber) {
            value = -value
        }
        if (value == 0L) {
            return when (scale) {
                0 -> "0"
                1 -> "0.0"
                2 -> "0.00"
                3 -> "0.000"
                4 -> "0.0000"
                5 -> "0.00000"
                6 -> "0.000000"
                else -> {
                    val result1 = StringBuilder()
                    if (scale < 0) {
                        result1.append("0E+")
                    } else {
                        result1.append("0E")
                    }
                    result1.append(
                        if (scale == Int.MIN_VALUE) "2147483648" else (-scale).toString()
                    )
                    result1.toString()
                }
            }
        }
        // one 32-bit unsigned value may contains 10 decimal digits
        resLengthInChars = 18
        // Explanation why +1+7:
        // +1 - one char for sign if needed.
        // +7 - For "special case 2" (see below) we have 7 free chars for
        //  inserting necessary scaled digits.
        result = CharArray(resLengthInChars + 1)
        //  Allocated [resLengthInChars+1] characters.
        // a free latest character may be used for "special case 1" (see below)
        currentChar = resLengthInChars
        var v = value
        do {
            val prev = v
            v /= 10
            result[--currentChar] = (0x0030 + (prev - v * 10)).toChar()
        } while (v != 0L)
        val exponent = resLengthInChars.toLong() - currentChar.toLong() - scale - 1L
        if (scale == 0) {
            if (negNumber) {
                result[--currentChar] = '-'
            }
            return String(result, currentChar, resLengthInChars - currentChar)
        }
        if (scale > 0 && exponent >= -6) {
            if (exponent >= 0) {
                // special case 1
                var insertPoint = currentChar + exponent.toInt()
                for (j in resLengthInChars - 1 downTo insertPoint) {
                    result[j + 1] = result[j]
                }
                result[++insertPoint] = '.'
                if (negNumber) {
                    result[--currentChar] = '-'
                }
                return String(result, currentChar, resLengthInChars - currentChar + 1)
            }
            // special case 2
            for (j in 2 until -exponent + 1) {
                result[--currentChar] = '0'
            }
            result[--currentChar] = '.'
            result[--currentChar] = '0'
            if (negNumber) {
                result[--currentChar] = '-'
            }
            return String(result, currentChar, resLengthInChars - currentChar)
        }
        val startPoint = currentChar + 1
        val result1 = StringBuilder(16 + resLengthInChars - startPoint)
        if (negNumber) {
            result1.append('-')
        }
        if (resLengthInChars - startPoint >= 1) {
            result1.append(result[currentChar])
            result1.append('.')
            result1.appendRange(result, currentChar + 1, resLengthInChars)
        } else {
            result1.appendRange(result, currentChar, resLengthInChars)
        }
        result1.append('E')
        if (exponent > 0) {
            result1.append('+')
        }
        result1.append(exponent.toString())
        return result1.toString()
    }

    fun divideLongByBillion(a: Long): Long {
        val quot: Long
        var rem: Long
        if (a >= 0) {
            val bLong = 1000000000L
            quot = a / bLong
            rem = a % bLong
        } else {
            /*
             * Make the dividend positive shifting it right by 1 bit then get
             * the quotient an remainder and correct them properly
             */
            val aPos = a ushr 1
            val bPos = 1000000000L ushr 1
            quot = aPos / bPos
            rem = aPos % bPos
            // double the remainder and add 1 if 'a' is odd
            rem = (rem shl 1) + (a and 1)
        }
        return rem shl 32 or (quot and 0xFFFFFFFFL)
    }

    /** @see BigInteger.doubleValue
     */
    fun bigInteger2Double(`val`: BigInteger): Double {
        `val`.prepareJavaRepresentation()
        // val.bitLength() < 64
        if (`val`.numberLength < 2
            || `val`.numberLength == 2 && `val`.digits[1] > 0
        ) {
            return `val`.toLong().toDouble()
        }
        // val.bitLength() >= 33 * 32 > 1024
        if (`val`.numberLength > 32) {
            return if (`val`.sign > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        }
        val bitLen = `val`.abs().bitLength()
        var exponent = (bitLen - 1).toLong()
        val delta = bitLen - 54
        // We need 54 top bits from this, the 53th bit is always 1 in lVal.
        val lVal = `val`.abs().shiftRight(delta).toLong()
        /*
         * Take 53 bits from lVal to mantissa. The least significant bit is
         * needed for rounding.
         */
        var mantissa = lVal and 0x1FFFFFFFFFFFFFL
        if (exponent == 1023L) {
            if (mantissa == 0X1FFFFFFFFFFFFFL) {
                return if (`val`.sign > 0) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
            }
            if (mantissa == 0x1FFFFFFFFFFFFEL) {
                return if (`val`.sign > 0) Double.MAX_VALUE else -Double.MAX_VALUE
            }
        }
        // Round the mantissa
        if (mantissa and 1 == 1L
            && (mantissa and 2 == 2L || nonZeroDroppedBits(
                delta,
                `val`.digits
            ))
        ) {
            mantissa += 2
        }
        mantissa = mantissa shr 1 // drop the rounding bit
        val resSign = if (`val`.sign < 0) (-(1L shl 63)) else 0
        exponent = 1023 + exponent shl 52 and 0x7FF0000000000000L
        val result = resSign or exponent or mantissa
        return Double.fromBits(result)
    }
}