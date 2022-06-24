/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package kendy.math

import kotlin.jvm.JvmStatic
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/*
 * In contrast to BigIntegers this class doesn't fake two's complement representation.
 * Any Bit-Operations, including Shifting, solely regard the unsigned magnitude.
 * Moreover BigInt objects are mutable and offer efficient in-place-operations.
 */
@Serializable
class BigInt {
    /* Fields used for the internal representation. */
    //@ReachabilitySensitive
    @Transient
    private var bignum: Long = 0
    override fun toString(): String {
        return decString()!!
    }

    fun hasNativeBignum(): Boolean {
        return bignum != 0L
    }

    private fun makeValid() {
        if (bignum == 0L) {
            bignum = NativeBN.BN_new()
            // TODO IOS registry.registerNativeAllocation(this, bignum)
        }
    }

    fun putCopy(from: BigInt) {
        makeValid()
        NativeBN.BN_copy(bignum, from.bignum)
    }

    fun copy(): BigInt {
        val bi = BigInt()
        bi.putCopy(this)
        return bi
    }

    fun putLongInt(`val`: Long) {
        makeValid()
        NativeBN.putLongInt(bignum, `val`)
    }

    fun putULongInt(`val`: Long, neg: Boolean) {
        makeValid()
        NativeBN.putULongInt(bignum, `val`, neg)
    }

    private fun invalidBigInteger(s: String): NumberFormatException {
        throw NumberFormatException("Invalid BigInteger: $s")
    }

    fun putDecString(original: String) {
        val s = checkString(original, 10)
        makeValid()
        val usedLen: Int = NativeBN.BN_dec2bn(bignum, s)
        if (usedLen < s.length) {
            throw invalidBigInteger(original)
        }
    }

    fun putHexString(original: String) {
        val s = checkString(original, 16)
        makeValid()
        val usedLen: Int = NativeBN.BN_hex2bn(bignum, s)
        if (usedLen < s.length) {
            throw invalidBigInteger(original)
        }
    }

    /**
     * Returns a string suitable for passing to OpenSSL.
     * Throws if 's' doesn't match Java's rules for valid BigInteger strings.
     * BN_dec2bn and BN_hex2bn do very little checking, so we need to manually
     * ensure we comply with Java's rules.
     * http://code.google.com/p/android/issues/detail?id=7036
     */
    fun checkString(s: String?, base: Int): String {
        var s = s
        if (s == null) {
            throw NullPointerException("s == null")
        }
        // A valid big integer consists of an optional '-' or '+' followed by
        // one or more digit characters appropriate to the given base,
        // and no other characters.
        var charCount = s.length
        var i = 0
        if (charCount > 0) {
            val ch = s[0]
            if (ch == '+') {
                // Java supports leading +, but OpenSSL doesn't, so we need to strip it.
                s = s.substring(1)
                --charCount
            } else if (ch == '-') {
                ++i
            }
        }
        if (charCount - i == 0) {
            throw invalidBigInteger(s)
        }
        var nonAscii = false
        while (i < charCount) {
            val ch = s[i]
            if (ch.toString().toIntOrNull(base) == null) {
                throw invalidBigInteger(s)
            }
            if (ch.toInt() > 128) {
                nonAscii = true
            }
            ++i
        }
        return if (nonAscii) toAscii(s, base) else s
    }

    fun putBigEndian(a: ByteArray, neg: Boolean) {
        makeValid()
        kendy.math.NativeBN.BN_bin2bn(a, a.size, neg, bignum)
    }

    fun putLittleEndianInts(a: IntArray, neg: Boolean) {
        makeValid()
        kendy.math.NativeBN.litEndInts2bn(a, a.size, neg, bignum)
    }

    fun putBigEndianTwosComplement(a: ByteArray) {
        makeValid()
        kendy.math.NativeBN.twosComp2bn(a, a.size, bignum)
    }

    fun longInt(): Long {
        return kendy.math.NativeBN.longInt(bignum)
    }

    fun decString(): String? {
        return kendy.math.NativeBN.BN_bn2dec(bignum)
    }

    fun hexString(): String? {
        return kendy.math.NativeBN.BN_bn2hex(bignum)
    }

    fun bigEndianMagnitude(): ByteArray? {
        return kendy.math.NativeBN.BN_bn2bin(bignum)
    }

    fun littleEndianIntsMagnitude(): IntArray? {
        return kendy.math.NativeBN.bn2litEndInts(bignum)
    }

    fun sign(): Int {
        return kendy.math.NativeBN.sign(bignum)
    }

    fun setSign(`val`: Int) {
        if (`val` > 0) {
            kendy.math.NativeBN.BN_set_negative(bignum, 0)
        } else {
            if (`val` < 0) kendy.math.NativeBN.BN_set_negative(bignum, 1)
        }
    }

    fun twosCompFitsIntoBytes(desiredByteCount: Int): Boolean {
        val actualByteCount: Int = (kendy.math.NativeBN.bitLength(bignum) + 7) / 8
        return actualByteCount <= desiredByteCount
    }

    fun bitLength(): Int {
        return kendy.math.NativeBN.bitLength(bignum)
    }

    fun isBitSet(n: Int): Boolean {
        return kendy.math.NativeBN.BN_is_bit_set(bignum, n)
    }

    fun shift(n: Int) {
        kendy.math.NativeBN.BN_shift(bignum, bignum, n)
    }

    fun addPositiveInt(w: Int) {
        kendy.math.NativeBN.BN_add_word(bignum, w)
    }

    fun multiplyByPositiveInt(w: Int) {
        kendy.math.NativeBN.BN_mul_word(bignum, w)
    }

    fun add(a: BigInt) {
        kendy.math.NativeBN.BN_add(bignum, bignum, a.bignum)
    }

    fun isPrime(certainty: Int): Boolean {
        return kendy.math.NativeBN.BN_primality_test(bignum, certainty, false)
    }

    companion object {
        /* TODO IOS Maybe needed?
        private val registry: NativeAllocationRegistry = NativeAllocationRegistry.createMalloced(
            BigInt::class.java.getClassLoader(), kendy.math.NativeBN.getNativeFinalizer()
        )
        */

        private fun newBigInt(): BigInt {
            val bi = BigInt()
            bi.bignum = NativeBN.BN_new()
            // TODO IOS registry.registerNativeAllocation(bi, bi.bignum)
            return bi
        }

        @JvmStatic
        fun cmp(a: BigInt, b: BigInt): Int {
            return kendy.math.NativeBN.BN_cmp(a.bignum, b.bignum)
        }

        // Java supports non-ASCII decimal digits, but OpenSSL doesn't.
        // We need to translate the decimal digits but leave any other characters alone.
        // This method assumes it's being called on a string that has already been validated.
        private fun toAscii(s: String, base: Int): String {
            val length = s.length
            val result = StringBuilder(length)
            for (i in 0 until length) {
                var ch = s[i]
                val value = ch.toString().toIntOrNull(base)
                if (value != null && value >= 0 && value <= 9) {
                    ch = ('0'.toInt() + value).toChar()
                }
                result.append(ch)
            }
            return result.toString()
        }

        // n > 0: shift left (multiply)
        @JvmStatic
        fun shift(a: BigInt, n: Int): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_shift(r.bignum, a.bignum, n)
            return r
        }

        @JvmStatic
        fun remainderByPositiveInt(a: BigInt, w: Int): Int {
            return kendy.math.NativeBN.BN_mod_word(a.bignum, w)
        }

        @JvmStatic
        fun addition(a: BigInt, b: BigInt): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_add(r.bignum, a.bignum, b.bignum)
            return r
        }

        @JvmStatic
        fun subtraction(a: BigInt, b: BigInt): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_sub(r.bignum, a.bignum, b.bignum)
            return r
        }

        @JvmStatic
        fun gcd(a: BigInt, b: BigInt): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_gcd(r.bignum, a.bignum, b.bignum)
            return r
        }

        @JvmStatic
        fun product(a: BigInt, b: BigInt): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_mul(r.bignum, a.bignum, b.bignum)
            return r
        }

        fun bigExp(a: BigInt, p: BigInt): BigInt {
            // Sign of p is ignored!
            val r = newBigInt()
            kendy.math.NativeBN.BN_exp(r.bignum, a.bignum, p.bignum)
            return r
        }

        @JvmStatic
        fun exp(a: BigInt, p: Int): BigInt {
            // Sign of p is ignored!
            val power = BigInt()
            power.putLongInt(p.toLong())
            return bigExp(a, power)
            // OPTIONAL:
            // int BN_sqr(BigInteger r, BigInteger a, BN_CTX ctx);
            // int BN_sqr(BIGNUM *r, const BIGNUM *a,BN_CTX *ctx);
        }

        @JvmStatic
        fun division(dividend: BigInt, divisor: BigInt, quotient: BigInt?, remainder: BigInt?) {
            val quot: Long
            val rem: Long
            quot = if (quotient != null) {
                quotient.makeValid()
                quotient.bignum
            } else {
                0
            }
            rem = if (remainder != null) {
                remainder.makeValid()
                remainder.bignum
            } else {
                0
            }
            kendy.math.NativeBN.BN_div(quot, rem, dividend.bignum, divisor.bignum)
        }

        @JvmStatic
        fun modulus(a: BigInt, m: BigInt): BigInt {
            // Sign of p is ignored! ?
            val r = newBigInt()
            kendy.math.NativeBN.BN_nnmod(r.bignum, a.bignum, m.bignum)
            return r
        }

        @JvmStatic
        fun modExp(a: BigInt, p: BigInt, m: BigInt): BigInt {
            // Sign of p is ignored!
            val r = newBigInt()
            kendy.math.NativeBN.BN_mod_exp(r.bignum, a.bignum, p.bignum, m.bignum)
            return r
        }

        @JvmStatic
        fun modInverse(a: BigInt, m: BigInt): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_mod_inverse(r.bignum, a.bignum, m.bignum)
            return r
        }

        @JvmStatic
        fun generatePrimeDefault(bitLength: Int): BigInt {
            val r = newBigInt()
            kendy.math.NativeBN.BN_generate_prime_ex(r.bignum, bitLength, false, 0, 0)
            return r
        }
    }
}