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
 * Immutable objects describing settings such as rounding mode and digit
 * precision for the numerical operations provided by class [BigDecimal].
 */
class MathContext : java.io.Serializable {
    /**
     * Returns the precision. The precision is the number of digits used for an
     * operation. Results are rounded to this precision. The precision is
     * guaranteed to be non negative. If the precision is zero, then the
     * computations have to be performed exact, results are not rounded in this
     * case.
     *
     * @return the precision.
     */
    /**
     * The number of digits to be used for an operation; results are rounded to
     * this precision.
     */
    val precision: Int
    /**
     * Returns the rounding mode. The rounding mode is the strategy to be used
     * to round results.
     *
     *
     * The rounding mode is one of
     * [RoundingMode.UP],
     * [RoundingMode.DOWN],
     * [RoundingMode.CEILING],
     * [RoundingMode.FLOOR],
     * [RoundingMode.HALF_UP],
     * [RoundingMode.HALF_DOWN],
     * [RoundingMode.HALF_EVEN], or
     * [RoundingMode.UNNECESSARY].
     *
     * @return the rounding mode.
     */
    /**
     * A `RoundingMode` object which specifies the algorithm to be used
     * for rounding.
     */
    val roundingMode: RoundingMode?
    /**
     * Constructs a new `MathContext` with the specified precision and
     * with the specified rounding mode. If the precision passed is zero, then
     * this implies that the computations have to be performed exact, the
     * rounding mode in this case is irrelevant.
     *
     * @param precision
     * the precision for the new `MathContext`.
     * @param roundingMode
     * the rounding mode for the new `MathContext`.
     * @throws IllegalArgumentException
     * if `precision < 0`.
     * @throws NullPointerException
     * if `roundingMode` is `null`.
     */
    /**
     * Constructs a new `MathContext` with the specified precision and
     * with the rounding mode [HALF_UP][RoundingMode.HALF_UP]. If the
     * precision passed is zero, then this implies that the computations have to
     * be performed exact, the rounding mode in this case is irrelevant.
     *
     * @param precision
     * the precision for the new `MathContext`.
     * @throws IllegalArgumentException
     * if `precision < 0`.
     */
    @JvmOverloads
    constructor(precision: Int, roundingMode: RoundingMode? = RoundingMode.HALF_UP) {
        this.precision = precision
        this.roundingMode = roundingMode
        checkValid()
    }

    /**
     * Constructs a new `MathContext` from a string. The string has to
     * specify the precision and the rounding mode to be used and has to follow
     * the following syntax: "precision=&lt;precision&gt; roundingMode=&lt;roundingMode&gt;"
     * This is the same form as the one returned by the [.toString]
     * method.
     *
     * @throws IllegalArgumentException
     * if the string is not in the correct format or if the
     * precision specified is < 0.
     */
    constructor(s: String) {
        val precisionLength = "precision=".length
        val roundingModeLength = "roundingMode=".length
        var spaceIndex: Int
        if (!s.startsWith("precision=") || s.indexOf(' ', precisionLength)
                .also { spaceIndex = it } == -1
        ) {
            throw invalidMathContext("Missing precision", s)
        }
        val precisionString = s.substring(precisionLength, spaceIndex)
        try {
            precision = precisionString.toInt()
        } catch (nfe: java.lang.NumberFormatException) {
            throw invalidMathContext("Bad precision", s)
        }
        var roundingModeStart = spaceIndex + 1
        if (!s.regionMatches(roundingModeStart, "roundingMode=", 0, roundingModeLength)) {
            throw invalidMathContext("Missing rounding mode", s)
        }
        roundingModeStart += roundingModeLength
        roundingMode = RoundingMode.valueOf(s.substring(roundingModeStart))
        checkValid()
    }

    private fun invalidMathContext(reason: String, s: String): java.lang.IllegalArgumentException {
        throw java.lang.IllegalArgumentException("$reason: $s")
    }

    private fun checkValid() {
        if (precision < 0) {
            throw java.lang.IllegalArgumentException("Negative precision: $precision")
        }
        if (roundingMode == null) {
            throw java.lang.NullPointerException("roundingMode == null")
        }
    }

    /**
     * Returns true if x is a `MathContext` with the same precision
     * setting and the same rounding mode as this `MathContext` instance.
     *
     * @param x
     * object to be compared.
     * @return `true` if this `MathContext` instance is equal to the
     * `x` argument; `false` otherwise.
     */
    override fun equals(x: Any?): Boolean {
        return (x is MathContext
                && x.precision == precision && x
            .roundingMode === roundingMode)
    }

    /**
     * Returns the hash code for this `MathContext` instance.
     *
     * @return the hash code for this `MathContext`.
     */
    override fun hashCode(): Int {
        // Make place for the necessary bits to represent 8 rounding modes
        return precision shl 3 or roundingMode!!.ordinal
    }

    /**
     * Returns the string representation for this `MathContext` instance.
     * The string has the form
     * `"precision=<precision> roundingMode=<roundingMode>"
    ` *  where `<precision>` is an integer describing the number
     * of digits used for operations and `<roundingMode>` is the
     * string representation of the rounding mode.
     *
     * @return a string representation for this `MathContext` instance
     */
    override fun toString(): String {
        return "precision=$precision roundingMode=$roundingMode"
    }

    /**
     * Makes checks upon deserialization of a `MathContext` instance.
     * Checks whether `precision >= 0` and `roundingMode != null`
     *
     * @throws StreamCorruptedException
     * if `precision < 0`
     * @throws StreamCorruptedException
     * if `roundingMode == null`
     */
    @Throws(java.io.IOException::class, java.lang.ClassNotFoundException::class)
    private fun readObject(s: java.io.ObjectInputStream) {
        s.defaultReadObject()
        try {
            checkValid()
        } catch (ex: java.lang.Exception) {
            throw java.io.StreamCorruptedException(ex.message)
        }
    }

    companion object {
        private const val serialVersionUID = 5579720004786848255L

        /**
         * A `MathContext` which corresponds to the [IEEE 754](http://en.wikipedia.org/wiki/IEEE_754-1985) quadruple
         * decimal precision format: 34 digit precision and
         * [RoundingMode.HALF_EVEN] rounding.
         */
        val DECIMAL128 = MathContext(34, RoundingMode.HALF_EVEN)

        /**
         * A `MathContext` which corresponds to the [IEEE 754](http://en.wikipedia.org/wiki/IEEE_754-1985) single decimal
         * precision format: 7 digit precision and [RoundingMode.HALF_EVEN]
         * rounding.
         */
        val DECIMAL32 = MathContext(7, RoundingMode.HALF_EVEN)

        /**
         * A `MathContext` which corresponds to the [IEEE 754](http://en.wikipedia.org/wiki/IEEE_754-1985) double decimal
         * precision format: 16 digit precision and [RoundingMode.HALF_EVEN]
         * rounding.
         */
        val DECIMAL64 = MathContext(16, RoundingMode.HALF_EVEN)

        /**
         * A `MathContext` for unlimited precision with
         * [RoundingMode.HALF_UP] rounding.
         */
        val UNLIMITED = MathContext(0, RoundingMode.HALF_UP)
    }
}