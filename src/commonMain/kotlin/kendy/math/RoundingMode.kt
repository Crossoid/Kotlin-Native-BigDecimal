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
 * Specifies the rounding behavior for operations whose results cannot be
 * represented exactly.
 */
enum class RoundingMode
/** It sets the old constant.  */(
    /** The old constant of `BigDecimal`.  */
    private val bigDecimalRM: Int
) {
    /**
     * Rounding mode where positive values are rounded towards positive infinity
     * and negative values towards negative infinity.
     * <br></br>
     * Rule: `x.round().abs() >= x.abs()`
     */
    UP(kendy.math.BigDecimal.ROUND_UP),

    /**
     * Rounding mode where the values are rounded towards zero.
     * <br></br>
     * Rule: `x.round().abs() <= x.abs()`
     */
    DOWN(kendy.math.BigDecimal.ROUND_DOWN),

    /**
     * Rounding mode to round towards positive infinity. For positive values
     * this rounding mode behaves as [.UP], for negative values as
     * [.DOWN].
     * <br></br>
     * Rule: `x.round() >= x`
     */
    CEILING(kendy.math.BigDecimal.ROUND_CEILING),

    /**
     * Rounding mode to round towards negative infinity. For positive values
     * this rounding mode behaves as [.DOWN], for negative values as
     * [.UP].
     * <br></br>
     * Rule: `x.round() <= x`
     */
    FLOOR(kendy.math.BigDecimal.ROUND_FLOOR),

    /**
     * Rounding mode where values are rounded towards the nearest neighbor. Ties
     * are broken by rounding up.
     */
    HALF_UP(kendy.math.BigDecimal.ROUND_HALF_UP),

    /**
     * Rounding mode where values are rounded towards the nearest neighbor. Ties
     * are broken by rounding down.
     */
    HALF_DOWN(kendy.math.BigDecimal.ROUND_HALF_DOWN),

    /**
     * Rounding mode where values are rounded towards the nearest neighbor. Ties
     * are broken by rounding to the even neighbor.
     */
    HALF_EVEN(kendy.math.BigDecimal.ROUND_HALF_EVEN),

    /**
     * Rounding mode where the rounding operations throws an ArithmeticException
     * for the case that rounding is necessary, i.e. for the case that the value
     * cannot be represented exactly.
     */
    UNNECESSARY(kendy.math.BigDecimal.ROUND_UNNECESSARY);

    companion object {
        /**
         * Converts rounding mode constants from class `BigDecimal` into
         * `RoundingMode` values.
         *
         * @param mode
         * rounding mode constant as defined in class `BigDecimal`
         * @return corresponding rounding mode object
         */
        fun valueOf(mode: Int): RoundingMode {
            return when (mode) {
                kendy.math.BigDecimal.ROUND_CEILING -> CEILING
                kendy.math.BigDecimal.ROUND_DOWN -> DOWN
                kendy.math.BigDecimal.ROUND_FLOOR -> FLOOR
                kendy.math.BigDecimal.ROUND_HALF_DOWN -> HALF_DOWN
                kendy.math.BigDecimal.ROUND_HALF_EVEN -> HALF_EVEN
                kendy.math.BigDecimal.ROUND_HALF_UP -> HALF_UP
                kendy.math.BigDecimal.ROUND_UNNECESSARY -> UNNECESSARY
                kendy.math.BigDecimal.ROUND_UP -> UP
                else -> throw java.lang.IllegalArgumentException("Invalid rounding mode")
            }
        }
    }
}