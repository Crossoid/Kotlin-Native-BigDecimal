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

/**
 * Static library that provides all operations related with division and modular
 * arithmetic to [BigInteger]. Some methods are provided in both mutable
 * and immutable way. There are several variants provided listed below:
 *
 *
 *  *  **Division**
 *
 *  * [BigInteger] division and remainder by [BigInteger].
 *  * [BigInteger] division and remainder by `int`.
 *  * *gcd* between [BigInteger] numbers.
 *
 *
 *  *  **Modular arithmetic **
 *
 *  * Modular exponentiation between [BigInteger] numbers.
 *  * Modular inverse of a [BigInteger] numbers.
 *
 *
 *
 */
internal object Division {
    /**
     * Divides an array by an integer value. Implements the Knuth's division
     * algorithm. See D. Knuth, The Art of Computer Programming, vol. 2.
     *
     * @return remainder
     */
    fun divideArrayByInt(
        quotient: IntArray, dividend: IntArray, dividendLength: Int,
        divisor: Int
    ): Int {
        var rem: Long = 0
        val bLong = (divisor.toLong() and 0xffffffffL)
        for (i in dividendLength - 1 downTo 0) {
            val temp = rem shl 32 or (dividend[i].toLong() and 0xffffffffL)
            var quot: Long
            if (temp >= 0) {
                quot = temp / bLong
                rem = temp % bLong
            } else {
                /*
                 * make the dividend positive shifting it right by 1 bit then
                 * get the quotient an remainder and correct them properly
                 */
                val aPos = temp ushr 1
                val bPos = (divisor ushr 1).toLong()
                quot = aPos / bPos
                rem = aPos % bPos
                // double the remainder and add 1 if a is odd
                rem = (rem shl 1) + (temp and 1)
                if (divisor and 1 != 0) {
                    // the divisor is odd
                    if (quot <= rem) {
                        rem -= quot
                    } else {
                        if (quot - rem <= bLong) {
                            rem += bLong - quot
                            quot -= 1
                        } else {
                            rem += (bLong shl 1) - quot
                            quot -= 2
                        }
                    }
                }
            }
            quotient[i] = (quot and 0xffffffffL).toInt()
        }
        return rem.toInt()
    }
}