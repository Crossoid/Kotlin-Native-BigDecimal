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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.math.sqrt
import kotlin.native.concurrent.ThreadLocal
import kotlin.random.Random
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Custom serializer; the most primitive one, just serialize to the string representation (and back).
 */
object BigIntegerSerializer : KSerializer<BigInteger> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("BigInteger", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigInteger) {
        val string = value.toString()
        encoder.encodeString(string)
    }

    override fun deserialize(decoder: Decoder): BigInteger {
        val string = decoder.decodeString()
        return BigInteger(string)
    }
}

/**
 * An immutable arbitrary-precision signed integer.
 *
 * <h3>Fast Cryptography</h3>
 * This implementation is efficient for operations traditionally used in
 * cryptography, such as the generation of large prime numbers and computation
 * of the modular inverse.
 *
 * <h3>Slow Two's Complement Bitwise Operations</h3>
 * This API includes operations for bitwise operations in two's complement
 * representation. Two's complement is not the internal representation used by
 * this implementation, so such methods may be inefficient. Use [ ] for high-performance bitwise operations on
 * arbitrarily-large sequences of bits.
 */
@Serializable(with = BigIntegerSerializer::class)
class BigInteger : Number, Comparable<BigInteger?> /*, java.io.Serializable*/ {
    @Transient
    private var bigInt: BigInt? = null

    @Transient
    private var nativeIsValid = false

    @Transient
    private var javaIsValid = false

    /** The magnitude of this in the little-endian representation.  */
    @Transient
    lateinit var digits: IntArray

    /**
     * The length of this in measured in ints. Can be less than
     * digits.length().
     */
    @JvmField
    @Transient
    var numberLength = 0

    /** The sign of this.  */
    @JvmField
    @Transient
    var sign = 0

    @Transient
    var firstNonzeroDigit = -2
        get() {
            if (field == -2) {
                var i: Int
                if (sign == 0) {
                    i = -1
                } else {
                    i = 0
                    while (digits[i] == 0) {
                        i++
                    }
                }
                field = i
            }
            return field
        }
        private set

    /** sign field, used for serialization.  */
    private var signum = 0

    /** absolute value field, used for serialization  */
    private lateinit var magnitude: ByteArray

    /** Cache for the hash code.  */
    @Transient
    private var hashCode = 0

    internal constructor(bigInt: BigInt?) {
        if (bigInt == null || !bigInt.hasNativeBignum()) {
            throw AssertionError()
        }
        setBigInt(bigInt)
    }

    internal constructor(sign: Int, value: Long) {
        val bigInt = BigInt()
        bigInt.putULongInt(value, sign < 0)
        setBigInt(bigInt)
    }

    /**
     * Constructs a number without creating new space. This construct should be
     * used only if the three fields of representation are known.
     *
     * @param sign the sign of the number.
     * @param numberLength the length of the internal array.
     * @param digits a reference of some array created before.
     */
    internal constructor(sign: Int, numberLength: Int, digits: IntArray) {
        setJavaRepresentation(sign, numberLength, digits)
    }

    /**
     * Constructs a random non-negative `BigInteger` instance in the range
     * `[0, pow(2, numBits)-1]`.
     *
     * @param numBits maximum length of the new `BigInteger` in bits.
     * @param random is the random number generator to be used.
     * @throws IllegalArgumentException if `numBits` < 0.
     */
    constructor(numBits: Int, random: Random) {
        if (numBits < 0) {
            throw IllegalArgumentException("numBits < 0: $numBits")
        }
        if (numBits == 0) {
            setJavaRepresentation(0, 1, intArrayOf(0))
        } else {
            val sign = 1
            val numberLength = numBits + 31 shr 5
            val digits = IntArray(numberLength)
            for (i in 0 until numberLength) {
                digits[i] = random.nextInt()
            }
            // Clear any extra bits.
            digits[numberLength - 1] = digits[numberLength - 1] ushr (-numBits and 31)
            setJavaRepresentation(sign, numberLength, digits)
        }
        javaIsValid = true
    }

    /**
     * Constructs a random `BigInteger` instance in the range `[0,
     * pow(2, bitLength)-1]` which is probably prime. The probability that the
     * returned `BigInteger` is prime is greater than
     * `1 - 1/2<sup>certainty</sup>)`.
     *
     *
     * **Note:** the `Random` argument is ignored if
     * `bitLength >= 16`, where this implementation will use OpenSSL's
     * `BN_generate_prime_ex` as a source of cryptographically strong pseudo-random numbers.
     *
     * @param bitLength length of the new `BigInteger` in bits.
     * @param certainty tolerated primality uncertainty.
     * @throws ArithmeticException if `bitLength < 2`.
     * @see [
     * Specification of random generator used from OpenSSL library](http://www.openssl.org/docs/crypto/BN_rand.html)
     */
    constructor(bitLength: Int, certainty: Int, random: Random) {
        if (bitLength < 2) {
            throw ArithmeticException("bitLength < 2: $bitLength")
        }
        if (bitLength < 16) {
            // We have to generate short primes ourselves, because OpenSSL bottoms out at 16 bits.
            var candidate: Int
            do {
                candidate = random.nextInt() and (1 shl bitLength) - 1
                candidate = candidate or (1 shl bitLength - 1) // Set top bit.
                if (bitLength > 2) {
                    candidate =
                        candidate or 1 // Any prime longer than 2 bits must have the bottom bit set.
                }
            } while (!isSmallPrime(candidate))
            val prime = BigInt()
            prime.putULongInt(candidate.toLong(), false)
            setBigInt(prime)
        } else {
            // We need a loop here to work around an OpenSSL bug; http://b/8588028.
            do {
                setBigInt(BigInt.generatePrimeDefault(bitLength))
            } while (bitLength() != bitLength)
        }
    }

    /**
     * Constructs a new `BigInteger` by parsing `value`. The string
     * representation consists of an optional plus or minus sign followed by a
     * non-empty sequence of decimal digits. Digits are interpreted as if by
     * `Character.digit(char,10)`.
     *
     * @param value string representation of the new `BigInteger`.
     * @throws NullPointerException if `value == null`.
     * @throws NumberFormatException if `value` is not a valid
     * representation of a `BigInteger`.
     */
    constructor(value: String) {
        val bigInt = BigInt()
        bigInt.putDecString(value!!)
        setBigInt(bigInt)
    }

    /**
     * Constructs a new `BigInteger` instance by parsing `value`.
     * The string representation consists of an optional plus or minus sign
     * followed by a non-empty sequence of digits in the specified radix. Digits
     * are interpreted as if by `Character.digit(char, radix)`.
     *
     * @param value string representation of the new `BigInteger`.
     * @param radix the base to be used for the conversion.
     * @throws NullPointerException if `value == null`.
     * @throws NumberFormatException if `value` is not a valid
     * representation of a `BigInteger` or if `radix <
     * Character.MIN_RADIX` or `radix > Character.MAX_RADIX`.
     */
    /* TODO IOS
    constructor(value: String, radix: Int) {
        if (value == null) {
            throw NullPointerException("value == null")
        }
        if (radix == 10) {
            val bigInt = BigInt()
            bigInt.putDecString(value)
            setBigInt(bigInt)
        } else if (radix == 16) {
            val bigInt = BigInt()
            bigInt.putHexString(value)
            setBigInt(bigInt)
        } else {
            if (radix < java.lang.Character.MIN_RADIX || radix > java.lang.Character.MAX_RADIX) {
                throw NumberFormatException("Invalid radix: $radix")
            }
            if (value.isEmpty()) {
                throw NumberFormatException("value.isEmpty()")
            }
            parseFromString(this, value, radix)
        }
    }
    */

    /**
     * Constructs a new `BigInteger` instance with the given sign and
     * magnitude.
     *
     * @param signum sign of the new `BigInteger` (-1 for negative, 0 for
     * zero, 1 for positive).
     * @param magnitude magnitude of the new `BigInteger` with the most
     * significant byte first.
     * @throws NullPointerException if `magnitude == null`.
     * @throws NumberFormatException if the sign is not one of -1, 0, 1 or if
     * the sign is zero and the magnitude contains non-zero entries.
     */
    constructor(signum: Int, magnitude: ByteArray) {
        if (magnitude == null) {
            throw NullPointerException("magnitude == null")
        }
        if (signum < -1 || signum > 1) {
            throw NumberFormatException("Invalid signum: $signum")
        }
        if (signum == 0) {
            for (element in magnitude) {
                if (element.toInt() != 0) {
                    throw NumberFormatException("signum-magnitude mismatch")
                }
            }
        }
        val bigInt = BigInt()
        bigInt.putBigEndian(magnitude, signum < 0)
        setBigInt(bigInt)
    }

    /**
     * Constructs a new `BigInteger` from the given two's complement
     * representation. The most significant byte is the entry at index 0. The
     * most significant bit of this entry determines the sign of the new `BigInteger` instance. The array must be nonempty.
     *
     * @param value two's complement representation of the new `BigInteger`.
     * @throws NullPointerException if `value == null`.
     * @throws NumberFormatException if the length of `value` is zero.
     */
    constructor(value: ByteArray) {
        if (value!!.size == 0) {
            throw NumberFormatException("value.length == 0")
        }
        val bigInt = BigInt()
        bigInt.putBigEndianTwosComplement(value)
        setBigInt(bigInt)
    }

    /**
     * Returns the internal native representation of this big integer, computing
     * it if necessary.
     */
    fun getBigInt(): BigInt? {
        if (nativeIsValid) {
            return bigInt
        }
        // TODO IOS synchronized(this) {
            if (nativeIsValid) {
                return bigInt
            }
            val bigInt = BigInt()
            bigInt.putLittleEndianInts(digits, sign < 0)
            setBigInt(bigInt)
            return bigInt
        // TODO IOS }
    }

    private fun setBigInt(bigInt: BigInt) {
        this.bigInt = bigInt
        nativeIsValid = true
    }

    private fun setJavaRepresentation(sign: Int, numberLength: Int, digits: IntArray) {
        // decrement numberLength to drop leading zeroes...
        var sign = sign
        var numberLength = numberLength
        while (numberLength > 0 && digits[--numberLength] == 0) {
        }
        // ... and then increment it back because we always drop one too many
        if (digits[numberLength++] == 0) {
            sign = 0
        }
        this.sign = sign
        this.digits = digits
        this.numberLength = numberLength
        javaIsValid = true
    }

    fun prepareJavaRepresentation() {
        if (javaIsValid) {
            return
        }
        // TODO IOS synchronized(this) {
            if (javaIsValid) {
                return
            }
            val sign = bigInt!!.sign()
            val digits = if (sign != 0) bigInt!!.littleEndianIntsMagnitude() else intArrayOf(0)
            setJavaRepresentation(sign, digits!!.size, digits)
        // TODO IOS}
    }

    /**
     * Returns the two's complement representation of this `BigInteger` in
     * a byte array.
     */
    fun toByteArray(): ByteArray {
        return twosComplement()
    }

    /**
     * Returns a `BigInteger` whose value is the absolute value of `this`.
     */
    fun abs(): BigInteger {
        val bigInt = getBigInt()
        if (bigInt!!.sign() >= 0) {
            return this
        }
        val a = bigInt.copy()
        a.setSign(1)
        return BigInteger(a)
    }

    /**
     * Returns a `BigInteger` whose value is the `-this`.
     */
    fun negate(): BigInteger {
        val bigInt = getBigInt()
        val sign = bigInt!!.sign()
        if (sign == 0) {
            return this
        }
        val a = bigInt.copy()
        a.setSign(-sign)
        return BigInteger(a)
    }

    /**
     * Returns a `BigInteger` whose value is `this + value`.
     */
    fun add(value: BigInteger): BigInteger {
        val lhs = getBigInt()
        val rhs = value.getBigInt()
        if (rhs!!.sign() == 0) {
            return this
        }
        return if (lhs!!.sign() == 0) {
            value
        } else BigInteger(BigInt.addition(lhs, rhs))
    }

    /**
     * Returns a `BigInteger` whose value is `this - value`.
     */
    fun subtract(value: BigInteger): BigInteger {
        val lhs = getBigInt()
        val rhs = value.getBigInt()
        return if (rhs!!.sign() == 0) {
            this
        } else BigInteger(BigInt.subtraction(lhs!!, rhs))
    }

    /**
     * Returns the sign of this `BigInteger`.
     *
     * @return `-1` if `this < 0`, `0` if `this == 0`,
     * `1` if `this > 0`.
     */
    fun signum(): Int {
        return if (javaIsValid) {
            sign
        } else getBigInt()!!.sign()
    }

    /**
     * Returns a `BigInteger` whose value is `this >> n`. For
     * negative arguments, the result is also negative. The shift distance may
     * be negative which means that `this` is shifted left.
     *
     *
     * **Implementation Note:** Usage of this method on negative values is
     * not recommended as the current implementation is not efficient.
     *
     * @param n shift distance
     * @return `this >> n` if `n >= 0`; `this << (-n)`
     * otherwise
     */
    fun shiftRight(n: Int): BigInteger {
        return shiftLeft(-n)
    }

    /**
     * Returns a `BigInteger` whose value is `this << n`. The
     * result is equivalent to `this * pow(2, n)` if n >= 0. The shift
     * distance may be negative which means that `this` is shifted right.
     * The result then corresponds to `floor(this / pow(2, -n))`.
     *
     *
     * **Implementation Note:** Usage of this method on negative values is
     * not recommended as the current implementation is not efficient.
     *
     * @param n shift distance.
     * @return `this << n` if `n >= 0`; `this >> (-n)`.
     * otherwise
     */
    fun shiftLeft(n: Int): BigInteger {
        if (n == 0) {
            return this
        }
        val sign = signum()
        if (sign == 0) {
            return this
        }
        return if (sign > 0 || n >= 0) {
            BigInteger(BigInt.shift(getBigInt()!!, n))
        } else {
            // Negative numbers faking 2's complement:
            // Not worth optimizing this:
            // Sticking to Harmony Java implementation.
            kendy.math.BitLevel.shiftRight(this, -n)
        }
    }

    fun shiftLeftOneBit(): BigInteger {
        return if (signum() == 0) this else kendy.math.BitLevel.shiftLeftOneBit(this)
    }

    /**
     * Returns the length of the value's two's complement representation without
     * leading zeros for positive numbers / without leading ones for negative
     * values.
     *
     *
     * The two's complement representation of `this` will be at least
     * `bitLength() + 1` bits long.
     *
     *
     * The value will fit into an `int` if `bitLength() < 32` or
     * into a `long` if `bitLength() < 64`.
     *
     * @return the length of the minimal two's complement representation for
     * `this` without the sign bit.
     */
    fun bitLength(): Int {
        // Optimization to avoid unnecessary duplicate representation:
        return if (!nativeIsValid && javaIsValid) {
            kendy.math.BitLevel.bitLength(this)
        } else getBigInt()!!.bitLength()
    }

    /**
     * Tests whether the bit at position n in `this` is set. The result is
     * equivalent to `this & pow(2, n) != 0`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     *
     * @param n position where the bit in `this` has to be inspected.
     * @throws ArithmeticException if `n < 0`.
     */
    fun testBit(n: Int): Boolean {
        var n = n
        if (n < 0) {
            throw ArithmeticException("n < 0: $n")
        }
        val sign = signum()
        return if (sign > 0 && nativeIsValid && !javaIsValid) {
            getBigInt()!!.isBitSet(n)
        } else {
            // Negative numbers faking 2's complement:
            // Not worth optimizing this:
            // Sticking to Harmony Java implementation.
            prepareJavaRepresentation()
            if (n == 0) {
                return digits[0] and 1 != 0
            }
            val intCount = n shr 5
            if (intCount >= numberLength) {
                return sign < 0
            }
            var digit = digits[intCount]
            n = 1 shl (n and 31) // int with 1 set to the needed position
            if (sign < 0) {
                val firstNonZeroDigit = firstNonzeroDigit
                digit = if (intCount < firstNonZeroDigit) {
                    return false
                } else if (firstNonZeroDigit == intCount) {
                    -digit
                } else {
                    digit.inv()
                }
            }
            digit and n != 0
        }
    }

    /**
     * Returns a `BigInteger` which has the same binary representation
     * as `this` but with the bit at position n set. The result is
     * equivalent to `this | pow(2, n)`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     *
     * @param n position where the bit in `this` has to be set.
     * @throws ArithmeticException if `n < 0`.
     */
    fun setBit(n: Int): BigInteger {
        prepareJavaRepresentation()
        return if (!testBit(n)) {
            kendy.math.BitLevel.flipBit(this, n)
        } else {
            this
        }
    }

    /**
     * Returns a `BigInteger` which has the same binary representation
     * as `this` but with the bit at position n cleared. The result is
     * equivalent to `this & ~pow(2, n)`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     *
     * @param n position where the bit in `this` has to be cleared.
     * @throws ArithmeticException if `n < 0`.
     */
    fun clearBit(n: Int): BigInteger {
        prepareJavaRepresentation()
        return if (testBit(n)) {
            kendy.math.BitLevel.flipBit(this, n)
        } else {
            this
        }
    }

    /**
     * Returns a `BigInteger` which has the same binary representation
     * as `this` but with the bit at position n flipped. The result is
     * equivalent to `this ^ pow(2, n)`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     *
     * @param n position where the bit in `this` has to be flipped.
     * @throws ArithmeticException if `n < 0`.
     */
    fun flipBit(n: Int): BigInteger {
        prepareJavaRepresentation()
        if (n < 0) {
            throw ArithmeticException("n < 0: $n")
        }
        return kendy.math.BitLevel.flipBit(this, n)
    }// (sign != 0) implies that exists some non zero digit

    /**
     * Returns the position of the lowest set bit in the two's complement
     * representation of this `BigInteger`. If all bits are zero (this==0)
     * then -1 is returned as result.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     */
    val lowestSetBit: Int
        get() {
            prepareJavaRepresentation()
            if (sign == 0) {
                return -1
            }
            // (sign != 0) implies that exists some non zero digit
            val i = firstNonzeroDigit
            return (i shl 5) + digits[i].countTrailingZeroBits()
        }

    /**
     * Returns the number of bits in the two's complement representation of
     * `this` which differ from the sign bit. If `this` is negative,
     * the result is equivalent to the number of bits set in the two's
     * complement representation of `-this - 1`.
     *
     *
     * Use `bitLength(0)` to find the length of the binary value in
     * bits.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     */
    fun bitCount(): Int {
        prepareJavaRepresentation()
        return kendy.math.BitLevel.bitCount(this)
    }

    /**
     * Returns a `BigInteger` whose value is `~this`. The result
     * of this operation is `-this-1`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     */
    operator fun not(): BigInteger {
        prepareJavaRepresentation()
        return kendy.math.Logical.not(this)
    }

    /**
     * Returns a `BigInteger` whose value is `this & value`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended
     * as the current implementation is not efficient.
     *
     * @param value value to be and'ed with `this`.
     * @throws NullPointerException if `value == null`.
     */
    fun and(value: BigInteger): BigInteger {
        prepareJavaRepresentation()
        value.prepareJavaRepresentation()
        return kendy.math.Logical.and(this, value)
    }

    /**
     * Returns a `BigInteger` whose value is `this | value`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     *
     * @param value value to be or'ed with `this`.
     * @throws NullPointerException if `value == null`.
     */
    fun or(value: BigInteger): BigInteger {
        prepareJavaRepresentation()
        value.prepareJavaRepresentation()
        return kendy.math.Logical.or(this, value)
    }

    /**
     * Returns a `BigInteger` whose value is `this ^ value`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended as
     * the current implementation is not efficient.
     *
     * @param value value to be xor'ed with `this`
     * @throws NullPointerException if `value == null`
     */
    fun xor(value: BigInteger): BigInteger {
        prepareJavaRepresentation()
        value.prepareJavaRepresentation()
        return kendy.math.Logical.xor(this, value)
    }

    /**
     * Returns a `BigInteger` whose value is `this & ~value`.
     * Evaluating `x.andNot(value)` returns the same result as `x.and(value.not())`.
     *
     *
     * **Implementation Note:** Usage of this method is not recommended
     * as the current implementation is not efficient.
     *
     * @param value value to be not'ed and then and'ed with `this`.
     * @throws NullPointerException if `value == null`.
     */
    fun andNot(value: BigInteger): BigInteger {
        prepareJavaRepresentation()
        value.prepareJavaRepresentation()
        return kendy.math.Logical.andNot(this, value)
    }

    override fun toInt(): Int {
        if (nativeIsValid && bigInt!!.twosCompFitsIntoBytes(4)) {
            return bigInt!!.longInt().toInt()
        }
        prepareJavaRepresentation()
        return sign * digits[0]
    }

    override fun toShort(): Short {
        return toInt().toShort();
    }

    override fun toByte(): Byte {
        return toInt().toByte()
    }

    override fun toChar(): Char {
        return toInt().toChar()
    }

    /**
     * Returns this `BigInteger` as a long value. If `this` is too
     * big to be represented as a long, then `this % pow(2, 64)` is
     * returned.
     */
    override fun toLong(): Long {
        if (nativeIsValid && bigInt!!.twosCompFitsIntoBytes(8)) {
            return bigInt!!.longInt()
        }
        prepareJavaRepresentation()
        val value = if (numberLength > 1) digits[1]
            .toLong() shl 32 or digits[0].toLong() and 0xFFFFFFFFL else (digits[0].toLong() and 0xFFFFFFFFL)
        return sign * value
    }

    /**
     * Returns this `BigInteger` as a float. If `this` is too big to
     * be represented as a float, then `Float.POSITIVE_INFINITY` or
     * `Float.NEGATIVE_INFINITY` is returned. Note that not all integers
     * in the range `[-Float.MAX_VALUE, Float.MAX_VALUE]` can be exactly
     * represented as a float.
     */
    override fun toFloat(): Float {
        return toDouble().toFloat()
    }

    /**
     * Returns this `BigInteger` as a double. If `this` is too big
     * to be represented as a double, then `Double.POSITIVE_INFINITY` or
     * `Double.NEGATIVE_INFINITY` is returned. Note that not all integers
     * in the range `[-Double.MAX_VALUE, Double.MAX_VALUE]` can be exactly
     * represented as a double.
     */
    override fun toDouble(): Double {
        return Conversion.bigInteger2Double(this)
    }

    /**
     * Compares this `BigInteger` with `value`. Returns `-1`
     * if `this < value`, `0` if `this == value` and `1`
     * if `this > value`, .
     *
     * @param value value to be compared with `this`.
     * @throws NullPointerException if `value == null`.
     */
    override operator fun compareTo(value: BigInteger?): Int {
        return BigInt.cmp(getBigInt()!!, value!!.getBigInt()!!)
    }

    /**
     * Returns the minimum of this `BigInteger` and `value`.
     *
     * @param value value to be used to compute the minimum with `this`.
     * @throws NullPointerException if `value == null`.
     */
    fun min(value: BigInteger): BigInteger {
        return if (this.compareTo(value) == -1) this else value
    }

    /**
     * Returns the maximum of this `BigInteger` and `value`.
     *
     * @param value value to be used to compute the maximum with `this`
     * @throws NullPointerException if `value == null`
     */
    fun max(value: BigInteger): BigInteger {
        return if (this.compareTo(value) == 1) this else value
    }

    override fun hashCode(): Int {
        if (hashCode == 0) {
            prepareJavaRepresentation()
            var hash = 0
            for (i in 0 until numberLength) {
                hash = hash * 33 + digits[i]
            }
            hashCode = hash * sign
        }
        return hashCode
    }

    override fun equals(x: Any?): Boolean {
        if (this === x) {
            return true
        }
        return if (x is BigInteger) {
            this.compareTo(x) == 0
        } else false
    }

    /**
     * Returns a string representation of this `BigInteger` in decimal
     * form.
     */
    override fun toString(): String {
        return getBigInt()!!.decString()!!
    }

    /**
     * Returns a string containing a string representation of this `BigInteger` with base radix. If `radix < Character.MIN_RADIX` or
     * `radix > Character.MAX_RADIX` then a decimal representation is
     * returned. The characters of the string representation are generated with
     * method `Character.forDigit`.
     *
     * @param radix base to be used for the string representation.
     */
    fun toString(radix: Int): String {
        return if (radix == 10) {
            getBigInt()!!.decString()!!
        } else {
            prepareJavaRepresentation()
            Conversion.bigInteger2String(this, radix)
        }
    }

    /**
     * Returns a `BigInteger` whose value is greatest common divisor
     * of `this` and `value`. If `this == 0` and `value == 0` then zero is returned, otherwise the result is positive.
     *
     * @param value value with which the greatest common divisor is computed.
     * @throws NullPointerException if `value == null`.
     */
    fun gcd(value: BigInteger): BigInteger {
        return BigInteger(BigInt.gcd(getBigInt()!!, value.getBigInt()!!))
    }

    /**
     * Returns a `BigInteger` whose value is `this * value`.
     *
     * @throws NullPointerException if `value == null`.
     */
    fun multiply(value: BigInteger): BigInteger {
        return BigInteger(BigInt.product(getBigInt()!!, value.getBigInt()!!))
    }

    /**
     * Returns a `BigInteger` whose value is `pow(this, exp)`.
     *
     * @throws ArithmeticException if `exp < 0`.
     */
    fun pow(exp: Int): BigInteger {
        if (exp < 0) {
            throw ArithmeticException("exp < 0: $exp")
        }
        return BigInteger(BigInt.exp(getBigInt()!!, exp))
    }

    /**
     * Returns a two element `BigInteger` array containing
     * `this / divisor` at index 0 and `this % divisor` at index 1.
     *
     * @param divisor value by which `this` is divided.
     * @throws NullPointerException if `divisor == null`.
     * @throws ArithmeticException if `divisor == 0`.
     * @see .divide
     *
     * @see .remainder
     */
    fun divideAndRemainder(divisor: BigInteger): Array<BigInteger> {
        val divisorBigInt = divisor.getBigInt()
        val quotient = BigInt()
        val remainder = BigInt()
        BigInt.division(getBigInt()!!, divisorBigInt!!, quotient, remainder)
        return arrayOf(BigInteger(quotient), BigInteger(remainder))
    }

    /**
     * Returns a `BigInteger` whose value is `this / divisor`.
     *
     * @param divisor value by which `this` is divided.
     * @return `this / divisor`.
     * @throws NullPointerException if `divisor == null`.
     * @throws ArithmeticException if `divisor == 0`.
     */
    fun divide(divisor: BigInteger): BigInteger {
        val quotient = BigInt()
        BigInt.division(getBigInt()!!, divisor.getBigInt()!!, quotient, null)
        return BigInteger(quotient)
    }

    /**
     * Returns a `BigInteger` whose value is `this % divisor`.
     * Regarding signs this methods has the same behavior as the % operator on
     * ints: the sign of the remainder is the same as the sign of this.
     *
     * @param divisor value by which `this` is divided.
     * @throws NullPointerException if `divisor == null`.
     * @throws ArithmeticException if `divisor == 0`.
     */
    fun remainder(divisor: BigInteger): BigInteger {
        val remainder = BigInt()
        BigInt.division(getBigInt()!!, divisor.getBigInt()!!, null, remainder)
        return BigInteger(remainder)
    }

    /**
     * Returns a `BigInteger` whose value is `1/this mod m`. The
     * modulus `m` must be positive. The result is guaranteed to be in the
     * interval `[0, m)` (0 inclusive, m exclusive). If `this` is
     * not relatively prime to m, then an exception is thrown.
     *
     * @param m the modulus.
     * @throws NullPointerException if `m == null`
     * @throws ArithmeticException if `m < 0 or` if `this` is not
     * relatively prime to `m`
     */
    fun modInverse(m: BigInteger): BigInteger {
        if (m.signum() <= 0) {
            throw ArithmeticException("modulus not positive")
        }
        return BigInteger(BigInt.modInverse(getBigInt()!!, m.getBigInt()!!))
    }

    /**
     * Returns a `BigInteger` whose value is `pow(this, exponent) mod modulus`. The modulus must be positive. The
     * result is guaranteed to be in the interval `[0, modulus)`.
     * If the exponent is negative, then
     * `pow(this.modInverse(modulus), -exponent) mod modulus` is computed.
     * The inverse of this only exists if `this` is relatively prime to the modulus,
     * otherwise an exception is thrown.
     *
     * @throws NullPointerException if `modulus == null` or `exponent == null`.
     * @throws ArithmeticException if `modulus < 0` or if `exponent < 0` and
     * not relatively prime to `modulus`.
     */
    fun modPow(exponent: BigInteger, modulus: BigInteger): BigInteger {
        if (modulus.signum() <= 0) {
            throw ArithmeticException("modulus.signum() <= 0")
        }
        val exponentSignum = exponent.signum()
        if (exponentSignum == 0) { // OpenSSL gets this case wrong; http://b/8574367.
            return ONE.mod(modulus)
        }
        val base = if (exponentSignum < 0) modInverse(modulus) else this
        return BigInteger(
            BigInt.modExp(
                base.getBigInt()!!,
                exponent.getBigInt()!!,
                modulus.getBigInt()!!
            )
        )
    }

    /**
     * Returns a `BigInteger` whose value is `this mod m`. The
     * modulus `m` must be positive. The result is guaranteed to be in the
     * interval `[0, m)` (0 inclusive, m exclusive). The behavior of this
     * function is not equivalent to the behavior of the % operator defined for
     * the built-in `int`'s.
     *
     * @param m the modulus.
     * @return `this mod m`.
     * @throws NullPointerException if `m == null`.
     * @throws ArithmeticException if `m < 0`.
     */
    fun mod(m: BigInteger): BigInteger {
        if (m.signum() <= 0) {
            throw ArithmeticException("m.signum() <= 0")
        }
        return BigInteger(BigInt.modulus(getBigInt()!!, m.getBigInt()!!))
    }

    /**
     * Tests whether this `BigInteger` is probably prime. If `true`
     * is returned, then this is prime with a probability greater than
     * `1 - 1/2<sup>certainty</sup>)`. If `false` is returned, then this
     * is definitely composite. If the argument `certainty` <= 0, then
     * this method returns true.
     *
     * @param certainty tolerated primality uncertainty.
     * @return `true`, if `this` is probably prime, `false`
     * otherwise.
     */
    fun isProbablePrime(certainty: Int): Boolean {
        return if (certainty <= 0) {
            true
        } else getBigInt()!!.isPrime(certainty)
    }

    /**
     * Returns the smallest integer x > `this` which is probably prime as
     * a `BigInteger` instance. The probability that the returned `BigInteger` is prime is greater than `1 - 1/2<sup>100</sup>`.
     *
     * @return smallest integer > `this` which is probably prime.
     * @throws ArithmeticException if `this < 0`.
     */
    fun nextProbablePrime(): BigInteger {
        if (sign < 0) {
            throw ArithmeticException("sign < 0")
        }
        return kendy.math.Primality.nextProbablePrime(this)!!
    }
    /* Private Methods */
    /**
     * Returns the two's complement representation of this BigInteger in a byte
     * array.
     */
    private fun twosComplement(): ByteArray {
        prepareJavaRepresentation()
        if (sign == 0) {
            return byteArrayOf(0)
        }
        val temp = this
        val bitLen = bitLength()
        val iThis = firstNonzeroDigit
        var bytesLen = (bitLen shr 3) + 1
        /* Puts the little-endian int array representing the magnitude
         * of this BigInteger into the big-endian byte array. */
        val bytes = ByteArray(bytesLen)
        var firstByteNumber = 0
        val highBytes: Int
        var bytesInInteger = 4
        val hB: Int
        if (bytesLen - (numberLength shl 2) == 1) {
            bytes[0] = (if (sign < 0) -1 else 0).toByte()
            highBytes = 4
            firstByteNumber++
        } else {
            hB = bytesLen and 3
            highBytes = if (hB == 0) 4 else hB
        }
        var digitIndex = iThis
        bytesLen -= iThis shl 2
        if (sign < 0) {
            var digit = -temp.digits[digitIndex]
            digitIndex++
            if (digitIndex == numberLength) {
                bytesInInteger = highBytes
            }
            var i = 0
            while (i < bytesInInteger) {
                bytes[--bytesLen] = digit.toByte()
                i++
                digit = digit shr 8
            }
            while (bytesLen > firstByteNumber) {
                digit = temp.digits[digitIndex].inv()
                digitIndex++
                if (digitIndex == numberLength) {
                    bytesInInteger = highBytes
                }
                var i = 0
                while (i < bytesInInteger) {
                    bytes[--bytesLen] = digit.toByte()
                    i++
                    digit = digit shr 8
                }
            }
        } else {
            while (bytesLen > firstByteNumber) {
                var digit = temp.digits[digitIndex]
                digitIndex++
                if (digitIndex == numberLength) {
                    bytesInInteger = highBytes
                }
                var i = 0
                while (i < bytesInInteger) {
                    bytes[--bytesLen] = digit.toByte()
                    i++
                    digit = digit shr 8
                }
            }
        }
        return bytes
    }

    /**
     * Returns a copy of the current instance to achieve immutability
     */
    /* TODO IOS
    fun copy(): BigInteger {
        prepareJavaRepresentation()
        val copyDigits = IntArray(numberLength)
        java.lang.System.arraycopy(digits, 0, copyDigits, 0, numberLength)
        return BigInteger(sign, numberLength, copyDigits)
    }
    */

    @ThreadLocal
    companion object {
        /** This is the serialVersionUID used by the sun implementation.  */
        private const val serialVersionUID = -8287574255936472291L

        /** The `BigInteger` constant 0.  */
        @JvmField
        val ZERO: BigInteger = BigInteger(0, 0)

        /** The `BigInteger` constant 1.  */
        @JvmField
        val ONE: BigInteger = BigInteger(1, 1)

        /** The `BigInteger` constant 10.  */
        @JvmField
        val TEN: BigInteger = BigInteger(1, 10)

        /** The `BigInteger` constant -1.  */
        @JvmField
        val MINUS_ONE: BigInteger = BigInteger(-1, 1)

        /** All the `BigInteger` numbers in the range [0,10] are cached.  */
        val SMALL_VALUES = arrayOf(
            ZERO, ONE, BigInteger(1, 2),
            BigInteger(1, 3), BigInteger(1, 4), BigInteger(1, 5),
            BigInteger(1, 6), BigInteger(1, 7), BigInteger(1, 8),
            BigInteger(1, 9), TEN
        )

        private fun isSmallPrime(x: Int): Boolean {
            if (x == 2) {
                return true
            }
            if (x % 2 == 0) {
                return false
            }
            val max = sqrt(x.toDouble()) as Int
            var i = 3
            while (i <= max) {
                if (x % i == 0) {
                    return false
                }
                i += 2
            }
            return true
        }

        /** Returns a `BigInteger` whose value is equal to `value`.  */
        @JvmStatic
        fun valueOf(value: Long): BigInteger {
            return if (value < 0) {
                if (value != -1L) {
                    BigInteger(-1, -value)
                } else MINUS_ONE
            } else if (value < SMALL_VALUES.size) {
                SMALL_VALUES[value.toInt()]
            } else { // (value > 10)
                BigInteger(1, value)
            }
        }

        /**
         * Returns a random positive `BigInteger` instance in the range `[0, pow(2, bitLength)-1]` which is probably prime. The probability that
         * the returned `BigInteger` is prime is greater than `1 - 1/2<sup>100</sup>)`.
         *
         * @param bitLength length of the new `BigInteger` in bits.
         * @return probably prime random `BigInteger` instance.
         * @throws IllegalArgumentException if `bitLength < 2`.
         */
        fun probablePrime(bitLength: Int, random: Random): BigInteger {
            return BigInteger(bitLength, 100, random)
        }

        fun multiplyByInt(res: IntArray, a: IntArray, aSize: Int, factor: Int): Int {
            var carry: Long = 0
            for (i in 0 until aSize) {
                carry += (a[i].toLong() and 0xFFFFFFFFL) * (factor.toLong() and 0xFFFFFFFFL)
                res[i] = carry.toInt()
                carry = carry ushr 32
            }
            return carry.toInt()
        }

        fun inplaceAdd(a: IntArray, aSize: Int, addend: Int): Int {
            var carry = (addend.toLong() and 0xFFFFFFFFL).toLong()
            var i = 0
            while (carry != 0L && i < aSize) {
                carry += a[i].toLong() and 0xFFFFFFFFL
                a[i] = carry.toInt()
                carry = carry shr 32
                i++
            }
            return carry.toInt()
        }

        /** @see BigInteger.BigInteger
         */
        private fun parseFromString(bi: BigInteger, value: String, radix: Int) {
            var stringLength = value.length
            val endChar = stringLength
            val sign: Int
            val startChar: Int
            if (value[0] == '-') {
                sign = -1
                startChar = 1
                stringLength--
            } else {
                sign = 1
                startChar = 0
            }

            /*
         * We use the following algorithm: split a string into portions of n
         * characters and convert each portion to an integer according to the
         * radix. Then convert an pow(radix, n) based number to binary using the
         * multiplication method. See D. Knuth, The Art of Computer Programming,
         * vol. 2.
         */
            val charsPerInt: Int = Conversion.digitFitInInt.get(radix)
            var bigRadixDigitsLength = stringLength / charsPerInt
            val topChars = stringLength % charsPerInt
            if (topChars != 0) {
                bigRadixDigitsLength++
            }
            val digits = IntArray(bigRadixDigitsLength)
            // Get the maximal power of radix that fits in int
            val bigRadix: Int = Conversion.bigRadices.get(radix - 2)
            // Parse an input string and accumulate the BigInteger's magnitude
            var digitIndex = 0 // index of digits array
            var substrEnd = startChar + if (topChars == 0) charsPerInt else topChars
            var substrStart = startChar
            while (substrStart < endChar) {
                val bigRadixDigit = value.substring(substrStart, substrEnd).toInt(radix)
                var newDigit = multiplyByInt(digits, digits, digitIndex, bigRadix)
                newDigit += inplaceAdd(digits, digitIndex, bigRadixDigit)
                digits[digitIndex++] = newDigit
                substrStart = substrEnd
                substrEnd = substrStart + charsPerInt
            }
            val numberLength = digitIndex
            bi.setJavaRepresentation(sign, numberLength, digits)
        }
    }
}
