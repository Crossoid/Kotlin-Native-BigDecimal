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

import kendy.math.BigInteger.Companion.valueOf

/**
 * Static library that provides all multiplication of [BigInteger] methods.
 */
internal object Multiplication {
    // BEGIN Android-removed
    // /**
    //  * Break point in digits (number of {@code int} elements)
    //  * between Karatsuba and Pencil and Paper multiply.
    //  */
    // static final int whenUseKaratsuba = 63; // an heuristic value
    // END Android-removed
    /**
     * An array with powers of ten that fit in the type `int`.
     * (`10^0,10^1,...,10^9`)
     */
    val tenPows = intArrayOf(
        1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000
    )

    /**
     * An array with powers of five that fit in the type `int`.
     * (`5^0,5^1,...,5^13`)
     */
    val fivePows = intArrayOf(
        1, 5, 25, 125, 625, 3125, 15625, 78125, 390625,
        1953125, 9765625, 48828125, 244140625, 1220703125
    )

    /**
     * An array with the first powers of ten in `BigInteger` version.
     * (`10^0,10^1,...,10^31`)
     */
    val bigTenPows = arrayOfNulls<BigInteger>(32)

    /**
     * An array with the first powers of five in `BigInteger` version.
     * (`5^0,5^1,...,5^31`)
     */
    val bigFivePows = arrayOfNulls<BigInteger>(32)
    // BEGIN android-note: multiply has been removed in favor of using OpenSSL BIGNUM
    // END android-note
    /**
     * Multiplies a number by a positive integer.
     * @param val an arbitrary `BigInteger`
     * @param factor a positive `int` number
     * @return `val * factor`
     */
    fun multiplyByPositiveInt(`val`: BigInteger, factor: Int): BigInteger {
        val bi = `val`.getBigInt()!!.copy()
        bi.multiplyByPositiveInt(factor)
        return BigInteger(bi)
    }

    /**
     * Multiplies a number by a power of ten.
     * This method is used in `BigDecimal` class.
     * @param val the number to be multiplied
     * @param exp a positive `long` exponent
     * @return `val * 10<sup>exp</sup>`
     */
    fun multiplyByTenPow(`val`: BigInteger, exp: Long): BigInteger {
        // PRE: exp >= 0
        return if (exp < tenPows.size) multiplyByPositiveInt(
            `val`,
            tenPows[exp.toInt()]
        ) else `val`.multiply(
            powerOf10(exp)!!
        )
    }

    /**
     * It calculates a power of ten, which exponent could be out of 32-bit range.
     * Note that internally this method will be used in the worst case with
     * an exponent equals to: `Integer.MAX_VALUE - Integer.MIN_VALUE`.
     * @param exp the exponent of power of ten, it must be positive.
     * @return a `BigInteger` with value `10<sup>exp</sup>`.
     */
    fun powerOf10(exp: Long): BigInteger? {
        // PRE: exp >= 0
        var intExp = exp.toInt()
        // "SMALL POWERS"
        if (exp < bigTenPows.size) {
            // The largest power that fit in 'long' type
            return bigTenPows[intExp]
        } else if (exp <= 50) {
            // To calculate:    10^exp
            return BigInteger.TEN.pow(intExp)
        }
        var res: BigInteger? = null
        try {
            // "LARGE POWERS"
            if (exp <= Int.MAX_VALUE) {
                // To calculate:    5^exp * 2^exp
                res = bigFivePows[1]!!.pow(intExp).shiftLeft(intExp)
            } else {
                /*
                 * "HUGE POWERS"
                 *
                 * This branch probably won't be executed since the power of ten is too
                 * big.
                 */
                // To calculate:    5^exp
                val powerOfFive = bigFivePows[1]!!
                    .pow(Int.MAX_VALUE)
                res = powerOfFive
                var longExp = exp - Int.MAX_VALUE
                intExp = (exp % Int.MAX_VALUE) as Int
                while (longExp > Int.MAX_VALUE) {
                    res = res!!.multiply(powerOfFive)
                    longExp -= Int.MAX_VALUE.toLong()
                }
                res = res!!.multiply(bigFivePows[1]!!.pow(intExp))
                // To calculate:    5^exp << exp
                res = res.shiftLeft(Int.MAX_VALUE)
                longExp = exp - Int.MAX_VALUE
                while (longExp > Int.MAX_VALUE) {
                    res = res!!.shiftLeft(Int.MAX_VALUE)
                    longExp -= Int.MAX_VALUE.toLong()
                }
                res = res!!.shiftLeft(intExp)
            }
        } catch (error: java.lang.OutOfMemoryError) {
            throw java.lang.ArithmeticException(error.message)
        }
        return res
    }

    /**
     * Multiplies a number by a power of five.
     * This method is used in `BigDecimal` class.
     * @param val the number to be multiplied
     * @param exp a positive `int` exponent
     * @return `val * 5<sup>exp</sup>`
     */
    fun multiplyByFivePow(`val`: BigInteger, exp: Int): BigInteger {
        // PRE: exp >= 0
        return if (exp < fivePows.size) {
            multiplyByPositiveInt(
                `val`,
                fivePows[exp]
            )
        } else if (exp < bigFivePows.size) {
            `val`.multiply(bigFivePows[exp]!!)
        } else { // Large powers of five
            `val`.multiply(bigFivePows[1]!!.pow(exp))
        }
    }

    init {
        var i: Int
        val fivePow = 1L
        kendy.math.i = 0
        while (kendy.math.i <= 18) {
            bigFivePows[kendy.math.i] = valueOf(kendy.math.fivePow)
            bigTenPows[kendy.math.i] = valueOf(kendy.math.fivePow shl kendy.math.i)
            kendy.math.fivePow *= 5
            kendy.math.i++
        }
        while (kendy.math.i < bigTenPows.size) {
            bigFivePows[kendy.math.i] = bigFivePows[kendy.math.i - 1]!!
                .multiply(bigFivePows[1]!!)
            bigTenPows[kendy.math.i] = bigTenPows[kendy.math.i - 1]!!
                .multiply(BigInteger.TEN)
            kendy.math.i++
        }
    }
}