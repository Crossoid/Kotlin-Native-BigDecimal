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

import kotlin.math.max
import kotlin.math.min

/**
 * The library implements some logical operations over `BigInteger`. The
 * operations provided are listed below.
 *
 *  * not
 *  * and
 *  * andNot
 *  * or
 *  * xor
 *
 */
internal object Logical {
    /** @see BigInteger.not
     */
    fun not(`val`: BigInteger): BigInteger {
        if (`val`.sign == 0) {
            return BigInteger.MINUS_ONE
        }
        if (`val`.equals(BigInteger.MINUS_ONE)) {
            return BigInteger.ZERO
        }
        val resDigits = IntArray(`val`.numberLength + 1)
        var i: Int
        if (`val`.sign > 0) {
            // ~val = -val + 1
            if (`val`.digits[`val`.numberLength - 1] != -1) {
                i = 0
                while (`val`.digits[i] == -1) {
                    i++
                }
            } else {
                i = 0
                while (i < `val`.numberLength && `val`.digits[i] == -1) {
                    i++
                }
                if (i == `val`.numberLength) {
                    resDigits[i] = 1
                    return BigInteger(-`val`.sign, i + 1, resDigits)
                }
            }
            // Here a carry 1 was generated
        } else { // (val.sign < 0)
            // ~val = -val - 1
            i = 0
            while (`val`.digits[i] == 0) {
                resDigits[i] = -1
                i++
            }
            // Here a borrow -1 was generated
        }
        // Now, the carry/borrow can be absorbed
        resDigits[i] = `val`.digits[i] + `val`.sign
        // Copying the remaining unchanged digit
        i++
        while (i < `val`.numberLength) {
            resDigits[i] = `val`.digits[i]
            i++
        }
        return BigInteger(-`val`.sign, i, resDigits)
    }

    /** @see BigInteger.and
     */
    fun and(`val`: BigInteger, that: BigInteger): BigInteger {
        if (that.sign == 0 || `val`.sign == 0) {
            return BigInteger.ZERO
        }
        if (that.equals(BigInteger.MINUS_ONE)) {
            return `val`
        }
        if (`val`.equals(BigInteger.MINUS_ONE)) {
            return that
        }
        return if (`val`.sign > 0) {
            if (that.sign > 0) {
                andPositive(`val`, that)
            } else {
                andDiffSigns(`val`, that)
            }
        } else {
            if (that.sign > 0) {
                andDiffSigns(that, `val`)
            } else if (`val`.numberLength > that.numberLength) {
                andNegative(`val`, that)
            } else {
                andNegative(that, `val`)
            }
        }
    }

    /** @return sign = 1, magnitude = val.magnitude & that.magnitude
     */
    fun andPositive(`val`: BigInteger, that: BigInteger): BigInteger {
        // PRE: both arguments are positive
        val resLength: Int = min(`val`.numberLength, that.numberLength)
        var i: Int = max(`val`.firstNonzeroDigit, that.firstNonzeroDigit)
        if (i >= resLength) {
            return BigInteger.ZERO
        }
        val resDigits = IntArray(resLength)
        while (i < resLength) {
            resDigits[i] = `val`.digits[i] and that.digits[i]
            i++
        }
        return BigInteger(1, resLength, resDigits)
    }

    /** @return sign = positive.magnitude & magnitude = -negative.magnitude
     */
    fun andDiffSigns(positive: BigInteger, negative: BigInteger): BigInteger {
        // PRE: positive is positive and negative is negative
        val iPos = positive.firstNonzeroDigit
        val iNeg = negative.firstNonzeroDigit

        // Look if the trailing zeros of the negative will "blank" all
        // the positive digits
        if (iNeg >= positive.numberLength) {
            return BigInteger.ZERO
        }
        val resLength = positive.numberLength
        val resDigits = IntArray(resLength)

        // Must start from max(iPos, iNeg)
        var i: Int = max(iPos, iNeg)
        if (i == iNeg) {
            resDigits[i] = -negative.digits[i] and positive.digits[i]
            i++
        }
        val limit: Int = min(negative.numberLength, positive.numberLength)
        while (i < limit) {
            resDigits[i] = negative.digits[i].inv() and positive.digits[i]
            i++
        }
        // if the negative was shorter must copy the remaining digits
        // from positive
        if (i >= negative.numberLength) {
            while (i < positive.numberLength) {
                resDigits[i] = positive.digits[i]
                i++
            }
        } // else positive ended and must "copy" virtual 0's, do nothing then
        return BigInteger(1, resLength, resDigits)
    }

    /** @return sign = -1, magnitude = -(-longer.magnitude & -shorter.magnitude)
     */
    fun andNegative(longer: BigInteger, shorter: BigInteger): BigInteger {
        // PRE: longer and shorter are negative
        // PRE: longer has at least as many digits as shorter
        val iLonger = longer.firstNonzeroDigit
        val iShorter = shorter.firstNonzeroDigit

        // Does shorter matter?
        if (iLonger >= shorter.numberLength) {
            return longer
        }
        val resLength: Int
        val resDigits: IntArray
        var i: Int = max(iShorter, iLonger)
        var digit: Int
        digit = if (iShorter > iLonger) {
            -shorter.digits[i] and longer.digits[i].inv()
        } else if (iShorter < iLonger) {
            shorter.digits[i].inv() and -longer.digits[i]
        } else {
            -shorter.digits[i] and -longer.digits[i]
        }
        if (digit == 0) {
            i++
            while (i < shorter.numberLength && (longer.digits[i] or shorter.digits[i]).inv()
                    .also { digit = it } == 0
            ) {
                // digit = ~longer.digits[i] & ~shorter.digits[i]
                i++
            }
            if (digit == 0) {
                // shorter has only the remaining virtual sign bits
                while (i < longer.numberLength && longer.digits[i].inv().also { digit = it } == 0) {
                    i++
                }
                if (digit == 0) {
                    resLength = longer.numberLength + 1
                    resDigits = IntArray(resLength)
                    resDigits[resLength - 1] = 1
                    return BigInteger(-1, resLength, resDigits)
                }
            }
        }
        resLength = longer.numberLength
        resDigits = IntArray(resLength)
        resDigits[i] = -digit
        i++
        while (i < shorter.numberLength) {

            // resDigits[i] = ~(~longer.digits[i] & ~shorter.digits[i];)
            resDigits[i] = longer.digits[i] or shorter.digits[i]
            i++
        }
        // shorter has only the remaining virtual sign bits
        while (i < longer.numberLength) {
            resDigits[i] = longer.digits[i]
            i++
        }
        return BigInteger(-1, resLength, resDigits)
    }

    /** @see BigInteger.andNot
     */
    fun andNot(`val`: BigInteger, that: BigInteger): BigInteger {
        if (that.sign == 0) {
            return `val`
        }
        if (`val`.sign == 0) {
            return BigInteger.ZERO
        }
        if (`val`.equals(BigInteger.MINUS_ONE)) {
            return that.not()
        }
        if (that.equals(BigInteger.MINUS_ONE)) {
            return BigInteger.ZERO
        }

        //if val == that, return 0
        return if (`val`.sign > 0) {
            if (that.sign > 0) {
                andNotPositive(`val`, that)
            } else {
                andNotPositiveNegative(`val`, that)
            }
        } else {
            if (that.sign > 0) {
                andNotNegativePositive(`val`, that)
            } else {
                andNotNegative(`val`, that)
            }
        }
    }

    /** @return sign = 1, magnitude = val.magnitude & ~that.magnitude
     */
    fun andNotPositive(`val`: BigInteger, that: BigInteger): BigInteger {
        // PRE: both arguments are positive
        val resDigits = IntArray(`val`.numberLength)
        val limit: Int = min(`val`.numberLength, that.numberLength)
        var i: Int
        i = `val`.firstNonzeroDigit
        while (i < limit) {
            resDigits[i] = `val`.digits[i] and that.digits[i].inv()
            i++
        }
        while (i < `val`.numberLength) {
            resDigits[i] = `val`.digits[i]
            i++
        }
        return BigInteger(1, `val`.numberLength, resDigits)
    }

    /** @return sign = 1, magnitude = positive.magnitude & ~(-negative.magnitude)
     */
    fun andNotPositiveNegative(positive: BigInteger, negative: BigInteger): BigInteger {
        // PRE: positive > 0 && negative < 0
        val iNeg = negative.firstNonzeroDigit
        val iPos = positive.firstNonzeroDigit
        if (iNeg >= positive.numberLength) {
            return positive
        }
        val resLength: Int = min(positive.numberLength, negative.numberLength)
        val resDigits = IntArray(resLength)

        // Always start from first non zero of positive
        var i = iPos
        while (i < iNeg) {

            // resDigits[i] = positive.digits[i] & -1 (~0)
            resDigits[i] = positive.digits[i]
            i++
        }
        if (i == iNeg) {
            resDigits[i] = positive.digits[i] and negative.digits[i] - 1
            i++
        }
        while (i < resLength) {

            // resDigits[i] = positive.digits[i] & ~(~negative.digits[i]);
            resDigits[i] = positive.digits[i] and negative.digits[i]
            i++
        }
        return BigInteger(1, resLength, resDigits)
    }

    /** @return sign = -1, magnitude = -(-negative.magnitude & ~positive.magnitude)
     */
    fun andNotNegativePositive(negative: BigInteger, positive: BigInteger): BigInteger {
        // PRE: negative < 0 && positive > 0
        var resLength: Int
        val resDigits: IntArray
        var limit: Int
        var digit: Int
        val iNeg = negative.firstNonzeroDigit
        val iPos = positive.firstNonzeroDigit
        if (iNeg >= positive.numberLength) {
            return negative
        }
        resLength = max(negative.numberLength, positive.numberLength)
        var i = iNeg
        if (iPos > iNeg) {
            resDigits = IntArray(resLength)
            limit = min(negative.numberLength, iPos)
            while (i < limit) {

                // 1st case:  resDigits [i] = -(-negative.digits[i] & (~0))
                // otherwise: resDigits[i] = ~(~negative.digits[i] & ~0)  ;
                resDigits[i] = negative.digits[i]
                i++
            }
            if (i == negative.numberLength) {
                i = iPos
                while (i < positive.numberLength) {

                    // resDigits[i] = ~(~positive.digits[i] & -1);
                    resDigits[i] = positive.digits[i]
                    i++
                }
            }
        } else {
            digit = -negative.digits[i] and positive.digits[i].inv()
            if (digit == 0) {
                limit = min(positive.numberLength, negative.numberLength)
                i++
                while (i < limit && (negative.digits[i] or positive.digits[i]).inv()
                        .also { digit = it } == 0
                ) {
                    // digit = ~negative.digits[i] & ~positive.digits[i]
                    i++
                }
                if (digit == 0) {
                    // the shorter has only the remaining virtual sign bits
                    while (i < positive.numberLength && positive.digits[i].inv()
                            .also { digit = it } == 0
                    ) {
                        // digit = -1 & ~positive.digits[i]
                        i++
                    }
                    while (i < negative.numberLength && negative.digits[i].inv()
                            .also { digit = it } == 0
                    ) {
                        // digit = ~negative.digits[i] & ~0
                        i++
                    }
                    if (digit == 0) {
                        resLength++
                        resDigits = IntArray(resLength)
                        resDigits[resLength - 1] = 1
                        return BigInteger(-1, resLength, resDigits)
                    }
                }
            }
            resDigits = IntArray(resLength)
            resDigits[i] = -digit
            i++
        }
        limit = min(positive.numberLength, negative.numberLength)
        while (i < limit) {

            //resDigits[i] = ~(~negative.digits[i] & ~positive.digits[i]);
            resDigits[i] = negative.digits[i] or positive.digits[i]
            i++
        }
        // Actually one of the next two cycles will be executed
        while (i < negative.numberLength) {
            resDigits[i] = negative.digits[i]
            i++
        }
        while (i < positive.numberLength) {
            resDigits[i] = positive.digits[i]
            i++
        }
        return BigInteger(-1, resLength, resDigits)
    }

    /** @return sign = 1, magnitude = -val.magnitude & ~(-that.magnitude)
     */
    fun andNotNegative(`val`: BigInteger, that: BigInteger): BigInteger {
        // PRE: val < 0 && that < 0
        val iVal = `val`.firstNonzeroDigit
        val iThat = that.firstNonzeroDigit
        if (iVal >= that.numberLength) {
            return BigInteger.ZERO
        }
        val resLength = that.numberLength
        val resDigits = IntArray(resLength)
        var limit: Int
        var i = iVal
        if (iVal < iThat) {
            // resDigits[i] = -val.digits[i] & -1;
            resDigits[i] = -`val`.digits[i]
            limit = min(`val`.numberLength, iThat)
            i++
            while (i < limit) {

                // resDigits[i] = ~val.digits[i] & -1;
                resDigits[i] = `val`.digits[i].inv()
                i++
            }
            if (i == `val`.numberLength) {
                while (i < iThat) {

                    // resDigits[i] = -1 & -1;
                    resDigits[i] = -1
                    i++
                }
                // resDigits[i] = -1 & ~-that.digits[i];
                resDigits[i] = that.digits[i] - 1
            } else {
                // resDigits[i] = ~val.digits[i] & ~-that.digits[i];
                resDigits[i] = `val`.digits[i].inv() and that.digits[i] - 1
            }
        } else if (iThat < iVal) {
            // resDigits[i] = -val.digits[i] & ~~that.digits[i];
            resDigits[i] = -`val`.digits[i] and that.digits[i]
        } else {
            // resDigits[i] = -val.digits[i] & ~-that.digits[i];
            resDigits[i] = -`val`.digits[i] and that.digits[i] - 1
        }
        limit = min(`val`.numberLength, that.numberLength)
        i++
        while (i < limit) {

            // resDigits[i] = ~val.digits[i] & ~~that.digits[i];
            resDigits[i] = `val`.digits[i].inv() and that.digits[i]
            i++
        }
        while (i < that.numberLength) {

            // resDigits[i] = -1 & ~~that.digits[i];
            resDigits[i] = that.digits[i]
            i++
        }
        return BigInteger(1, resLength, resDigits)
    }

    /** @see BigInteger.or
     */
    fun or(`val`: BigInteger, that: BigInteger): BigInteger {
        if (that.equals(BigInteger.MINUS_ONE) || `val`.equals(BigInteger.MINUS_ONE)) {
            return BigInteger.MINUS_ONE
        }
        if (that.sign == 0) {
            return `val`
        }
        if (`val`.sign == 0) {
            return that
        }
        return if (`val`.sign > 0) {
            if (that.sign > 0) {
                if (`val`.numberLength > that.numberLength) {
                    orPositive(`val`, that)
                } else {
                    orPositive(that, `val`)
                }
            } else {
                orDiffSigns(`val`, that)
            }
        } else {
            if (that.sign > 0) {
                orDiffSigns(that, `val`)
            } else if (that.firstNonzeroDigit > `val`.firstNonzeroDigit) {
                orNegative(that, `val`)
            } else {
                orNegative(`val`, that)
            }
        }
    }

    /** @return sign = 1, magnitude = longer.magnitude | shorter.magnitude
     */
    fun orPositive(longer: BigInteger, shorter: BigInteger): BigInteger {
        // PRE: longer and shorter are positive;
        // PRE: longer has at least as many digits as shorter
        val resLength = longer.numberLength
        val resDigits = IntArray(resLength)
        var i: Int
        i = 0
        while (i < shorter.numberLength) {
            resDigits[i] = longer.digits[i] or shorter.digits[i]
            i++
        }
        while (i < resLength) {
            resDigits[i] = longer.digits[i]
            i++
        }
        return BigInteger(1, resLength, resDigits)
    }

    /** @return sign = -1, magnitude = -(-val.magnitude | -that.magnitude)
     */
    fun orNegative(`val`: BigInteger, that: BigInteger): BigInteger {
        // PRE: val and that are negative;
        // PRE: val has at least as many trailing zeros digits as that
        val iThat = that.firstNonzeroDigit
        val iVal = `val`.firstNonzeroDigit
        var i: Int
        if (iVal >= that.numberLength) {
            return that
        } else if (iThat >= `val`.numberLength) {
            return `val`
        }
        val resLength: Int = min(`val`.numberLength, that.numberLength)
        val resDigits = IntArray(resLength)

        //Looking for the first non-zero digit of the result
        if (iThat == iVal) {
            resDigits[iVal] = -(-`val`.digits[iVal] or -that.digits[iVal])
            i = iVal
        } else {
            i = iThat
            while (i < iVal) {
                resDigits[i] = that.digits[i]
                i++
            }
            resDigits[i] = that.digits[i] and `val`.digits[i] - 1
        }
        i++
        while (i < resLength) {
            resDigits[i] = `val`.digits[i] and that.digits[i]
            i++
        }
        return BigInteger(-1, resLength, resDigits)
    }

    /** @return sign = -1, magnitude = -(positive.magnitude | -negative.magnitude)
     */
    fun orDiffSigns(positive: BigInteger, negative: BigInteger): BigInteger {
        // Jumping over the least significant zero bits
        val iNeg = negative.firstNonzeroDigit
        val iPos = positive.firstNonzeroDigit
        var i: Int
        var limit: Int

        // Look if the trailing zeros of the positive will "copy" all
        // the negative digits
        if (iPos >= negative.numberLength) {
            return negative
        }
        val resLength = negative.numberLength
        val resDigits = IntArray(resLength)
        if (iNeg < iPos) {
            // We know for sure that this will
            // be the first non zero digit in the result
            i = iNeg
            while (i < iPos) {
                resDigits[i] = negative.digits[i]
                i++
            }
        } else if (iPos < iNeg) {
            i = iPos
            resDigits[i] = -positive.digits[i]
            limit = min(positive.numberLength, iNeg)
            i++
            while (i < limit) {
                resDigits[i] = positive.digits[i].inv()
                i++
            }
            if (i != positive.numberLength) {
                resDigits[i] = (-negative.digits[i] or positive.digits[i]).inv()
            } else {
                while (i < iNeg) {
                    resDigits[i] = -1
                    i++
                }
                // resDigits[i] = ~(-negative.digits[i] | 0);
                resDigits[i] = negative.digits[i] - 1
            }
            i++
        } else { // iNeg == iPos
            // Applying two complement to negative and to result
            i = iPos
            resDigits[i] = -(-negative.digits[i] or positive.digits[i])
            i++
        }
        limit = min(negative.numberLength, positive.numberLength)
        while (i < limit) {

            // Applying two complement to negative and to result
            // resDigits[i] = ~(~negative.digits[i] | positive.digits[i] );
            resDigits[i] = negative.digits[i] and positive.digits[i].inv()
            i++
        }
        while (i < negative.numberLength) {
            resDigits[i] = negative.digits[i]
            i++
        }
        return BigInteger(-1, resLength, resDigits)
    }

    /** @see BigInteger.xor
     */
    fun xor(`val`: BigInteger, that: BigInteger): BigInteger {
        if (that.sign == 0) {
            return `val`
        }
        if (`val`.sign == 0) {
            return that
        }
        if (that.equals(BigInteger.MINUS_ONE)) {
            return `val`.not()
        }
        if (`val`.equals(BigInteger.MINUS_ONE)) {
            return that.not()
        }
        return if (`val`.sign > 0) {
            if (that.sign > 0) {
                if (`val`.numberLength > that.numberLength) {
                    xorPositive(`val`, that)
                } else {
                    xorPositive(that, `val`)
                }
            } else {
                xorDiffSigns(`val`, that)
            }
        } else {
            if (that.sign > 0) {
                xorDiffSigns(that, `val`)
            } else if (that.firstNonzeroDigit > `val`.firstNonzeroDigit) {
                xorNegative(that, `val`)
            } else {
                xorNegative(`val`, that)
            }
        }
    }

    /** @return sign = 0, magnitude = longer.magnitude | shorter.magnitude
     */
    fun xorPositive(longer: BigInteger, shorter: BigInteger): BigInteger {
        // PRE: longer and shorter are positive;
        // PRE: longer has at least as many digits as shorter
        val resLength = longer.numberLength
        val resDigits = IntArray(resLength)
        var i: Int = min(longer.firstNonzeroDigit, shorter.firstNonzeroDigit)
        while (i < shorter.numberLength) {
            resDigits[i] = longer.digits[i] xor shorter.digits[i]
            i++
        }
        while (i < longer.numberLength) {
            resDigits[i] = longer.digits[i]
            i++
        }
        return BigInteger(1, resLength, resDigits)
    }

    /** @return sign = 0, magnitude = -val.magnitude ^ -that.magnitude
     */
    fun xorNegative(`val`: BigInteger, that: BigInteger): BigInteger {
        // PRE: val and that are negative
        // PRE: val has at least as many trailing zero digits as that
        val resLength: Int = max(`val`.numberLength, that.numberLength)
        val resDigits = IntArray(resLength)
        val iVal = `val`.firstNonzeroDigit
        val iThat = that.firstNonzeroDigit
        var i = iThat
        var limit: Int
        if (iVal == iThat) {
            resDigits[i] = -`val`.digits[i] xor -that.digits[i]
        } else {
            resDigits[i] = -that.digits[i]
            limit = min(that.numberLength, iVal)
            i++
            while (i < limit) {
                resDigits[i] = that.digits[i].inv()
                i++
            }
            // Remains digits in that?
            if (i == that.numberLength) {
                //Jumping over the remaining zero to the first non one
                while (i < iVal) {

                    //resDigits[i] = 0 ^ -1;
                    resDigits[i] = -1
                    i++
                }
                //resDigits[i] = -val.digits[i] ^ -1;
                resDigits[i] = `val`.digits[i] - 1
            } else {
                resDigits[i] = -`val`.digits[i] xor that.digits[i].inv()
            }
        }
        limit = min(`val`.numberLength, that.numberLength)
        //Perform ^ between that al val until that ends
        i++
        while (i < limit) {

            //resDigits[i] = ~val.digits[i] ^ ~that.digits[i];
            resDigits[i] = `val`.digits[i] xor that.digits[i]
            i++
        }
        //Perform ^ between val digits and -1 until val ends
        while (i < `val`.numberLength) {

            //resDigits[i] = ~val.digits[i] ^ -1  ;
            resDigits[i] = `val`.digits[i]
            i++
        }
        while (i < that.numberLength) {

            //resDigits[i] = -1 ^ ~that.digits[i] ;
            resDigits[i] = that.digits[i]
            i++
        }
        return BigInteger(1, resLength, resDigits)
    }

    /** @return sign = 1, magnitude = -(positive.magnitude ^ -negative.magnitude)
     */
    fun xorDiffSigns(positive: BigInteger, negative: BigInteger): BigInteger {
        var resLength: Int = max(negative.numberLength, positive.numberLength)
        val resDigits: IntArray
        val iNeg = negative.firstNonzeroDigit
        val iPos = positive.firstNonzeroDigit
        var i: Int
        var limit: Int

        //The first
        if (iNeg < iPos) {
            resDigits = IntArray(resLength)
            i = iNeg
            //resDigits[i] = -(-negative.digits[i]);
            resDigits[i] = negative.digits[i]
            limit = min(negative.numberLength, iPos)
            //Skip the positive digits while they are zeros
            i++
            while (i < limit) {

                //resDigits[i] = ~(~negative.digits[i]);
                resDigits[i] = negative.digits[i]
                i++
            }
            //if the negative has no more elements, must fill the
            //result with the remaining digits of the positive
            if (i == negative.numberLength) {
                while (i < positive.numberLength) {

                    //resDigits[i] = ~(positive.digits[i] ^ -1) -> ~(~positive.digits[i])
                    resDigits[i] = positive.digits[i]
                    i++
                }
            }
        } else if (iPos < iNeg) {
            resDigits = IntArray(resLength)
            i = iPos
            //Applying two complement to the first non-zero digit of the result
            resDigits[i] = -positive.digits[i]
            limit = min(positive.numberLength, iNeg)
            i++
            while (i < limit) {

                //Continue applying two complement the result
                resDigits[i] = positive.digits[i].inv()
                i++
            }
            //When the first non-zero digit of the negative is reached, must apply
            //two complement (arithmetic negation) to it, and then operate
            if (i == iNeg) {
                resDigits[i] = (positive.digits[i] xor -negative.digits[i]).inv()
                i++
            } else {
                //if the positive has no more elements must fill the remaining digits with
                //the negative ones
                while (i < iNeg) {

                    // resDigits[i] = ~(0 ^ 0)
                    resDigits[i] = -1
                    i++
                }
                while (i < negative.numberLength) {

                    //resDigits[i] = ~(~negative.digits[i] ^ 0)
                    resDigits[i] = negative.digits[i]
                    i++
                }
            }
        } else {
            //The first non-zero digit of the positive and negative are the same
            i = iNeg
            var digit = positive.digits[i] xor -negative.digits[i]
            if (digit == 0) {
                limit = min(positive.numberLength, negative.numberLength)
                i++
                while (i < limit && positive.digits[i] xor negative.digits[i].inv()
                        .also { digit = it } == 0
                ) {
                    i++
                }
                if (digit == 0) {
                    // shorter has only the remaining virtual sign bits
                    while (i < positive.numberLength && positive.digits[i].inv()
                            .also { digit = it } == 0
                    ) {
                        i++
                    }
                    while (i < negative.numberLength && negative.digits[i].inv()
                            .also { digit = it } == 0
                    ) {
                        i++
                    }
                    if (digit == 0) {
                        resLength = resLength + 1
                        resDigits = IntArray(resLength)
                        resDigits[resLength - 1] = 1
                        return BigInteger(-1, resLength, resDigits)
                    }
                }
            }
            resDigits = IntArray(resLength)
            resDigits[i] = -digit
            i++
        }
        limit = min(negative.numberLength, positive.numberLength)
        while (i < limit) {
            resDigits[i] = (negative.digits[i].inv() xor positive.digits[i]).inv()
            i++
        }
        while (i < positive.numberLength) {

            // resDigits[i] = ~(positive.digits[i] ^ -1)
            resDigits[i] = positive.digits[i]
            i++
        }
        while (i < negative.numberLength) {

            // resDigits[i] = ~(0 ^ ~negative.digits[i])
            resDigits[i] = negative.digits[i]
            i++
        }
        return BigInteger(-1, resLength, resDigits)
    }
}