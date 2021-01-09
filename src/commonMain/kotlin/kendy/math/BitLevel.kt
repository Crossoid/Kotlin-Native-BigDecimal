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

import kotlin.jvm.JvmStatic
import kotlin.math.max

/**
 * Static library that provides all the **bit level** operations for
 * [BigInteger]. The operations are:
 *
 *  * Left Shifting
 *  * Right Shifting
 *  * Bit clearing
 *  * Bit setting
 *  * Bit counting
 *  * Bit testing
 *  * Getting of the lowest bit set
 *
 * All operations are provided in immutable way, and some in both mutable and
 * immutable.
 */
internal object BitLevel {
    /** @see BigInteger.bitLength
     */
    fun bitLength(`val`: BigInteger): Int {
        `val`.prepareJavaRepresentation()
        if (`val`.sign == 0) {
            return 0
        }
        var bLength = `val`.numberLength shl 5
        var highDigit = `val`.digits[`val`.numberLength - 1]
        if (`val`.sign < 0) {
            val i = `val`.firstNonzeroDigit
            // We reduce the problem to the positive case.
            if (i == `val`.numberLength - 1) {
                highDigit--
            }
        }
        // Subtracting all sign bits
        bLength -= highDigit.countLeadingZeroBits()
        return bLength
    }

    /** @see BigInteger.bitCount
     */
    fun bitCount(`val`: BigInteger): Int {
        `val`.prepareJavaRepresentation()
        var bCount = 0
        if (`val`.sign == 0) {
            return 0
        }
        var i = `val`.firstNonzeroDigit
        if (`val`.sign > 0) {
            while (i < `val`.numberLength) {
                bCount += `val`.digits[i].countOneBits()
                i++
            }
        } else { // (sign < 0)
            // this digit absorbs the carry
            bCount += (-`val`.digits[i]).countOneBits()
            i++
            while (i < `val`.numberLength) {
                bCount += `val`.digits[i].inv().countOneBits()
                i++
            }
            // We take the complement sum:
            bCount = (`val`.numberLength shl 5) - bCount
        }
        return bCount
    }

    /**
     * Performs a fast bit testing for positive numbers. The bit to to be tested
     * must be in the range `[0, val.bitLength()-1]`
     */
    fun testBit(`val`: BigInteger, n: Int): Boolean {
        `val`.prepareJavaRepresentation()
        // PRE: 0 <= n < val.bitLength()
        return `val`.digits[n shr 5] and (1 shl (n and 31)) != 0
    }

    /**
     * Check if there are 1s in the lowest bits of this BigInteger
     *
     * @param numberOfBits the number of the lowest bits to check
     * @return false if all bits are 0s, true otherwise
     */
    @JvmStatic
    fun nonZeroDroppedBits(numberOfBits: Int, digits: IntArray): Boolean {
        val intCount = numberOfBits shr 5
        val bitCount = numberOfBits and 31
        var i: Int
        i = 0
        while (i < intCount && digits[i] == 0) {
            i++
        }
        return i != intCount || digits[i] shl 32 - bitCount != 0
    }

    fun shiftLeftOneBit(result: IntArray, source: IntArray, srcLen: Int) {
        var carry = 0
        for (i in 0 until srcLen) {
            val `val` = source[i]
            result[i] = `val` shl 1 or carry
            carry = `val` ushr 31
        }
        if (carry != 0) {
            result[srcLen] = carry
        }
    }

    fun shiftLeftOneBit(source: BigInteger): BigInteger {
        source.prepareJavaRepresentation()
        val srcLen = source.numberLength
        val resLen = srcLen + 1
        val resDigits = IntArray(resLen)
        shiftLeftOneBit(resDigits, source.digits, srcLen)
        return BigInteger(source.sign, resLen, resDigits)
    }

    /** @see BigInteger.shiftRight
     */
    fun shiftRight(source: BigInteger, count: Int): BigInteger {
        var count = count
        source.prepareJavaRepresentation()
        val intCount = count shr 5 // count of integers
        count = count and 31 // count of remaining bits
        if (intCount >= source.numberLength) {
            return if (source.sign < 0) BigInteger.MINUS_ONE else BigInteger.ZERO
        }
        var i: Int
        var resLength = source.numberLength - intCount
        val resDigits = IntArray(resLength + 1)
        shiftRight(resDigits, resLength, source.digits, intCount, count)
        if (source.sign < 0) {
            // Checking if the dropped bits are zeros (the remainder equals to
            // 0)
            i = 0
            while (i < intCount && source.digits[i] == 0) {
                i++
            }
            // If the remainder is not zero, add 1 to the result
            if (i < intCount
                || count > 0 && source.digits[i] shl 32 - count != 0
            ) {
                i = 0
                while (i < resLength && resDigits[i] == -1) {
                    resDigits[i] = 0
                    i++
                }
                if (i == resLength) {
                    resLength++
                }
                resDigits[i]++
            }
        }
        return BigInteger(source.sign, resLength, resDigits)
    }

    /**
     * Shifts right an array of integers. Total shift distance in bits is
     * intCount * 32 + count.
     *
     * @param result
     * the destination array
     * @param resultLen
     * the destination array's length
     * @param source
     * the source array
     * @param intCount
     * the number of elements to be shifted
     * @param count
     * the number of bits to be shifted
     * @return dropped bit's are all zero (i.e. remaider is zero)
     */
    fun shiftRight(
        result: IntArray,
        resultLen: Int,
        source: IntArray,
        intCount: Int,
        count: Int
    ): Boolean {
        var i: Int
        var allZero = true
        i = 0
        while (i < intCount) {
            allZero = allZero and (source[i] == 0)
            i++
        }
        if (count == 0) {
            source.copyInto(result, 0, intCount, intCount + resultLen)
            i = resultLen
        } else {
            val leftShiftCount = 32 - count
            allZero = allZero and (source[i] shl leftShiftCount == 0)
            i = 0
            while (i < resultLen - 1) {
                result[i] = (source[i + intCount] ushr count
                        or (source[i + intCount + 1] shl leftShiftCount))
                i++
            }
            result[i] = source[i + intCount] ushr count
            i++
        }
        return allZero
    }

    /**
     * Performs a flipBit on the BigInteger, returning a BigInteger with the the
     * specified bit flipped.
     */
    fun flipBit(`val`: BigInteger, n: Int): BigInteger {
        `val`.prepareJavaRepresentation()
        val resSign = if (`val`.sign == 0) 1 else `val`.sign
        val intCount = n shr 5
        val bitN = n and 31
        val resLength: Int = max(intCount + 1, `val`.numberLength) + 1
        val resDigits = IntArray(resLength)
        var i: Int
        val bitNumber = 1 shl bitN
        `val`.digits.copyInto(resDigits, 0, 0, `val`.numberLength)
        if (`val`.sign < 0) {
            if (intCount >= `val`.numberLength) {
                resDigits[intCount] = bitNumber
            } else {
                //val.sign<0 y intCount < val.numberLength
                val firstNonZeroDigit = `val`.firstNonzeroDigit
                if (intCount > firstNonZeroDigit) {
                    resDigits[intCount] = resDigits[intCount] xor bitNumber
                } else if (intCount < firstNonZeroDigit) {
                    resDigits[intCount] = -bitNumber
                    i = intCount + 1
                    while (i < firstNonZeroDigit) {
                        resDigits[i] = -1
                        i++
                    }
                    resDigits[i] = resDigits[i]--
                } else {
                    i = intCount
                    resDigits[i] = -(-resDigits[intCount] xor bitNumber)
                    if (resDigits[i] == 0) {
                        i++
                        while (resDigits[i] == -1) {
                            resDigits[i] = 0
                            i++
                        }
                        resDigits[i]++
                    }
                }
            }
        } else { //case where val is positive
            resDigits[intCount] = resDigits[intCount] xor bitNumber
        }
        return BigInteger(resSign, resLength, resDigits)
    }
}