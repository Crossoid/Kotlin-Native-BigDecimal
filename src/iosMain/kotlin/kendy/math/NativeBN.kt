/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (C) 2021 Jan Holešovský
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

import boringssl.BIGNUM
import kotlinx.cinterop.*

/**
 * Binding between the Kotlin BigDecimal and boringssl's BIGNUM.
 * https://kotlinlang.org/docs/native-c-interop.html
 */
actual internal object NativeBN {
    /**
     * Just throw if the BIGNUM handle is not correct.
     */
    private fun checkValid(a: Long) {
        if (a == 0L)
            throw NullPointerException("BIGNUM not valid")
    }

    /**
     * Throw if the BIGNUM handle is not correct.
     */
    private fun checkValid(bn: CPointer<BIGNUM>?) {
        toLong(bn) // it performs the check
    }

    /**
     * Convert the Long to the C representation of BIGNUM.
     */
    private fun toBigNum(a: Long): CPointer<BIGNUM>? {
        return a.toCPointer<BIGNUM>()
    }

    /**
     * Convert the BIGNUM C representation to Long.
     */
    private fun toLong(bn: CPointer<BIGNUM>?): Long {
        val bnLong = bn?.toLong() ?: 0
        checkValid(bnLong)
        return bnLong
    }

    /**
     * Convenience function to convert Boolean to Int.
     */
    private fun Boolean.toInt(): Int = if (this) 1 else 0

    // BIGNUM *BN_new(void);
    actual fun BN_new(): Long {
        return toLong(boringssl.BN_new())
    }

    // void BN_free(BIGNUM *a);
    fun BN_free(a: Long) {
        boringssl.BN_free(toBigNum(a))
    }

    // int BN_cmp(const BIGNUM *a, const BIGNUM *b);
    actual fun BN_cmp(a: Long, b: Long): Int {
        return boringssl.BN_cmp(toBigNum(a), toBigNum(b));
    }

    // BIGNUM *BN_copy(BIGNUM *to, const BIGNUM *from);
    actual fun BN_copy(to: Long, from: Long) {
        checkValid(boringssl.BN_copy(toBigNum(to), toBigNum(from)))
    }

    actual fun putLongInt(a: Long, dw: Long) {
        if (dw >= 0) {
            putULongInt(a, dw, false);
        } else {
            putULongInt(a, -dw, true);
        }
    }

    actual fun putULongInt(a: Long, dw: Long, neg: Boolean) {
        val bnA = toBigNum(a)
        if (boringssl.BN_set_u64(bnA, dw.toULong()) == 0) {
            throw ArithmeticException("BN_set_u64 failed")
            return
        }

        boringssl.BN_set_negative(bnA, neg.toInt());
    }

    // int BN_dec2bn(BIGNUM **a, const char *str);
    actual fun BN_dec2bn(a: Long, str: String?): Int {
        val result = boringssl.BN_dec2bn(cValuesOf(toBigNum(a)), str);
        if (result == 0) {
            throw ArithmeticException("BN_dec2bn failed")
        }
        return result;
    }

    // int BN_hex2bn(BIGNUM **a, const char *str);
    actual fun BN_hex2bn(a: Long, str: String?): Int {
        val result = boringssl.BN_hex2bn(cValuesOf(toBigNum(a)), str);
        if (result == 0) {
            throw ArithmeticException("BN_hex2bn failed")
        }
        return result;
    }

    // BIGNUM * BN_bin2bn(const unsigned char *s, int len, BIGNUM *ret);
    // BN-Docu: s is taken as unsigned big endian;
    // Additional parameter: neg.
    actual fun BN_bin2bn(s: ByteArray?, len: Int, neg: Boolean, ret: Long) {
        val retBN = toBigNum(ret)
        // "pin" the ByteArray to fix it in memory at a given place
        s?.usePinned { pinned ->
            checkValid(boringssl.BN_bin2bn(pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>(), len.toULong(), retBN))
        }

        boringssl.BN_set_negative(retBN, neg.toInt());
    }

    actual fun litEndInts2bn(ints: IntArray?, len: Int, neg: Boolean, ret: Long) {
        // TODO assert(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)

        val retBN = toBigNum(ret);
        // "pin" the IntArray to fix it in memory at a given place
        ints?.usePinned { pinned ->
            checkValid(boringssl.BN_le2bn(pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>(), len.toULong() * 4UL /* sizeof Int */, retBN))
        }

        boringssl.BN_set_negative(retBN, neg.toInt());
    }

    actual fun twosComp2bn(s: ByteArray?, len: Int, ret: Long) {
        checkValid(ret)

        val retBN = toBigNum(ret);

        if (s == null) {
            return;
        }

        if (len == 0) {
            boringssl.BN_zero(retBN);
            return;
        }

        // "pin" to fix it in memory at a given place
        s.usePinned { pinned ->
            if (boringssl.BN_bin2bn(pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>(), len.toULong(), retBN) == null) {
                throw ArithmeticException("BN_bin2bn failed")
                return;
            }
        }

        // Use the high bit to determine the sign in twos-complement.
        boringssl.BN_set_negative(retBN, ((s[0].toInt() and 0x80) != 0).toInt());

        if (boringssl.BN_is_negative(retBN) != 0) {
            // For negative values, BN_bin2bn doesn't interpret the twos-complement
            // representation, so ret is now (- value - 2^N). We can use nnmod_pow2 to set
            // ret to (-value).
            if (boringssl.BN_nnmod_pow2(retBN, retBN, len.toULong() * 8U) == 0) {
                throw ArithmeticException("BN_nnmod_pow2 failed")
                return;
            }

            // And now we correct the sign.
            boringssl.BN_set_negative(retBN, 1);
        }
    }

    actual fun longInt(a: Long): Long {
        checkValid(a)

        val aBN = toBigNum(a)

        memScoped {
            val wordPointer = alloc<ULongVar>()
            wordPointer.value = 0UL

            if (boringssl.BN_get_u64(aBN, wordPointer.ptr) != 0) {
                if (boringssl.BN_is_negative(aBN) != 0)
                    return -(wordPointer.value.toLong())
                else
                    return wordPointer.value.toLong()
            } else {
                // This should be unreachable if our caller checks BigInt::twosCompFitsIntoBytes(8)
                throw ArithmeticException("BN_get_u64 failed")
            }
        }
    }

    /**
     * Convert the c-string to Kotlin String and trim leading zeros.
     */
    private fun leadingZerosTrimmed(str: CPointer<ByteVar>): String {
        val s = str.toKString()
        val minus = (s[0] == '-')
        var i = if (minus) 1 else 0

        while (i < s.length && s[i] == '0') {
            ++i
        }

        if (i == s.length)
            return "0"
        else if ((i == 0) || (minus && i == 1))
            return s
        else if (minus)
            return '-' + s.substring(i)
        else
            return s.substring(i)
    }

    // char * BN_bn2dec(const BIGNUM *a);
    actual fun BN_bn2dec(a: Long): String? {
        checkValid(a)

        val tmpStr = boringssl.BN_bn2dec(toBigNum(a));
        if (tmpStr == null) {
            throw ArithmeticException("BN_bn2dec failed")
            return null
        }
        return leadingZerosTrimmed(tmpStr)
    }

    // char * BN_bn2hex(const BIGNUM *a);
    actual fun BN_bn2hex(a: Long): String? {
        checkValid(a)

        val tmpStr = boringssl.BN_bn2hex(toBigNum(a));
        if (tmpStr == null) {
            throw ArithmeticException("BN_bn2hex failed")
            return null
        }
        return leadingZerosTrimmed(tmpStr)
    }

    // Returns result byte[] AND NOT length.
    // int BN_bn2bin(const BIGNUM *a, unsigned char *to);
    actual fun BN_bn2bin(a: Long): ByteArray? {
        checkValid(a)

        val aBN = toBigNum(a);
        val len = boringssl.BN_num_bytes(aBN)
        var result = ByteArray(len.toInt())

        // "pin" to fix it in memory at a given place
        result?.usePinned { pinned ->
            boringssl.BN_bn2bin(aBN, pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>());
        }

        return result;
    }

    actual fun bn2litEndInts(a: Long): IntArray? {
        checkValid(a)

        val aBN = toBigNum(a);

        // The number of integers we need is BN_num_bytes(a) / sizeof(int), rounded up
        val intSize = 4
        val intLen = (boringssl.BN_num_bytes(aBN).toInt() + intSize - 1) / intSize;

        // Allocate our result with the JNI boilerplate
        var result = IntArray(intLen);

        // "pin" to fix it in memory at a given place
        result?.usePinned { pinned ->
            // We can simply interpret a little-endian byte stream as a little-endian integer stream.
            if (boringssl.BN_bn2le_padded(pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>(), (intLen * intSize).toULong(), aBN) == 0) {
                throw ArithmeticException("BN_bn2le_padded failed")
                return null
            }
        }

        return result;
    }

    // Returns -1, 0, 1 AND NOT boolean.
    // #define BN_is_negative(a) ((a)->neg != 0)
    actual fun sign(a: Long): Int {
        checkValid(a)

        val aBN = toBigNum(a)
        if (boringssl.BN_is_zero(aBN) != 0) {
            return 0;
        } else if (boringssl.BN_is_negative(aBN) != 0) {
            return -1;
        }
        return 1;
    }

    // void BN_set_negative(BIGNUM *b, int n);
    actual fun BN_set_negative(b: Long, n: Int) {
        checkValid(b)
        boringssl.BN_set_negative(toBigNum(b), n);
    }

    actual fun bitLength(a: Long): Int {
        checkValid(a)

        val aBN = toBigNum(a);

        // If a is not negative, we can use BN_num_bits directly.
        if (boringssl.BN_is_negative(aBN) == 0) {
            return boringssl.BN_num_bits(aBN).toInt()
        }

        // In the negative case, the number of bits in a is the same as the number of bits in |a|,
        // except one less when |a| is a power of two.
        val positiveA = boringssl.BN_new()

        if (boringssl.BN_copy(positiveA, aBN) == null) {
            boringssl.BN_free(positiveA)
            throw ArithmeticException("BN_copy failed")
            return -1;
        }

        boringssl.BN_set_negative(positiveA, 0)
        val numBits = if (boringssl.BN_is_pow2(positiveA) != 0) boringssl.BN_num_bits(positiveA).toInt() - 1 else boringssl.BN_num_bits(positiveA).toInt()

        boringssl.BN_free(positiveA);
        return numBits;
    }

    // int BN_is_bit_set(const BIGNUM *a, int n);
    actual fun BN_is_bit_set(a: Long, n: Int): Boolean {
        checkValid(a)

        // NOTE: this is only called in the positive case, so BN_is_bit_set is fine here.
        if (boringssl.BN_is_bit_set(toBigNum(a), n) != 0)
            return true

        return false
    }

    // int BN_shift(BIGNUM *r, const BIGNUM *a, int n);
    actual fun BN_shift(r: Long, a: Long, n: Int) {
        checkValid(r)
        checkValid(a)

        var ok: Int
        if (n >= 0) {
            ok = boringssl.BN_lshift(toBigNum(r), toBigNum(a), n);
        } else {
            ok = boringssl.BN_rshift(toBigNum(r), toBigNum(a), -n);
        }

        if (ok == 0)
            throw ArithmeticException("BN_shift failed")
    }

    // ATTENTION: w is treated as unsigned.
    // int BN_add_word(BIGNUM *a, BN_ULONG w);
    actual fun BN_add_word(a: Long, w: Int) {
        checkValid(a)

        if (boringssl.BN_add_word(toBigNum(a), w.toULong()) == 0) {
            throw ArithmeticException("BN_add_word failed")
        }
    }

    // ATTENTION: w is treated as unsigned.
    // int BN_mul_word(BIGNUM *a, BN_ULONG w);
    actual fun BN_mul_word(a: Long, w: Int) {
        checkValid(a)

        if (boringssl.BN_mul_word(toBigNum(a), w.toULong()) == 0) {
            throw ArithmeticException("BN_mul_word failed")
        }
    }

    // ATTENTION: w is treated as unsigned.
    // BN_ULONG BN_mod_word(BIGNUM *a, BN_ULONG w);
    actual fun BN_mod_word(a: Long, w: Int): Int {
        checkValid(a)

        val result = boringssl.BN_mod_word(toBigNum(a), w.toULong());
        if (result == (-1).toULong()) {
            throw ArithmeticException("BN_mod_word failed")
        }
        return result.toInt();
    }

    // int BN_add(BIGNUM *r, const BIGNUM *a, const BIGNUM *b);
    actual fun BN_add(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        if (boringssl.BN_add(toBigNum(r), toBigNum(a), toBigNum(b)) == 0) {
            throw ArithmeticException("BN_add failed")
        }
    }

    // int BN_sub(BIGNUM *r, const BIGNUM *a, const BIGNUM *b);
    actual fun BN_sub(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        if (boringssl.BN_sub(toBigNum(r), toBigNum(a), toBigNum(b)) == 0) {
            throw ArithmeticException("BN_sub failed")
        }
    }

    // int BN_gcd(BIGNUM *r, const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);
    actual fun BN_gcd(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_gcd(toBigNum(r), toBigNum(a), toBigNum(b), ctx) == 0) {
            throw ArithmeticException("BN_gcd failed")
        }
    }

    // int BN_mul(BIGNUM *r, const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);
    actual fun BN_mul(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_mul(toBigNum(r), toBigNum(a), toBigNum(b), ctx) == 0) {
            throw ArithmeticException("BN_mul failed")
        }
    }

    // int BN_exp(BIGNUM *r, const BIGNUM *a, const BIGNUM *p, BN_CTX *ctx);
    actual fun BN_exp(r: Long, a: Long, p: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(p)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_exp(toBigNum(r), toBigNum(a), toBigNum(p), ctx) == 0) {
            throw ArithmeticException("BN_exp failed")
        }
    }

    // int BN_div(BIGNUM *dv, BIGNUM *rem, const BIGNUM *m, const BIGNUM *d, BN_CTX *ctx);
    actual fun BN_div(dv: Long, rem: Long, m: Long, d: Long) {
        checkValid(if (rem != 0L) rem else dv)
        checkValid(if (dv != 0L) dv else rem)
        checkValid(m)
        checkValid(d)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_div(toBigNum(dv), toBigNum(rem), toBigNum(m), toBigNum(d), ctx) == 0) {
            throw ArithmeticException("BN_div failed")
        }
    }

    // int BN_nnmod(BIGNUM *r, const BIGNUM *a, const BIGNUM *m, BN_CTX *ctx);
    actual fun BN_nnmod(r: Long, a: Long, m: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(m)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_nnmod(toBigNum(r), toBigNum(a), toBigNum(m), ctx) == 0) {
            throw ArithmeticException("BN_nnmod failed")
        }
    }

    // int BN_mod_exp(BIGNUM *r, const BIGNUM *a, const BIGNUM *p, const BIGNUM *m, BN_CTX *ctx);
    actual fun BN_mod_exp(r: Long, a: Long, p: Long, m: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(p)
        checkValid(m)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_mod_exp(toBigNum(r), toBigNum(a), toBigNum(p), toBigNum(m), ctx) == 0) {
            throw ArithmeticException("BN_mod_exp failed")
        }
    }

    // BIGNUM * BN_mod_inverse(BIGNUM *ret, const BIGNUM *a, const BIGNUM *n, BN_CTX *ctx);
    actual fun BN_mod_inverse(ret: Long, a: Long, n: Long) {
        checkValid(ret)
        checkValid(a)
        checkValid(n)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_mod_inverse(toBigNum(ret), toBigNum(a), toBigNum(n), ctx) == null) {
            throw ArithmeticException("BN_mod_inverse failed")
        }
    }

    // int BN_generate_prime_ex(BIGNUM *ret, int bits, int safe,
    //         const BIGNUM *add, const BIGNUM *rem, BN_GENCB *cb);
    actual fun BN_generate_prime_ex(ret: Long, bits: Int, safe: Boolean, add: Long, rem: Long) {
        checkValid(ret)

        if (boringssl.BN_generate_prime_ex(toBigNum(ret), bits, safe.toInt(), toBigNum(add), toBigNum(rem), null) == 0) {
            throw ArithmeticException("BN_generate_prime_ex failed")
        }
    }

    // int BN_primality_test(int *is_probably_prime, const BIGNUM *candidate, int checks,
    //                       BN_CTX *ctx, int do_trial_division, BN_GENCB *cb);
    // Returns *is_probably_prime on success and throws an exception on error.
    actual fun BN_primality_test(candidate: Long, checks: Int, do_trial_division: Boolean): Boolean {
        checkValid(candidate)

        val ctx = boringssl.BN_CTX_new()
        memScoped {
            val is_probably_prime = alloc<IntVar>()
            is_probably_prime.value = 0

            if (boringssl.BN_primality_test(is_probably_prime.ptr, toBigNum(candidate), checks, ctx, do_trial_division.toInt(), null) == 0) {
                throw ArithmeticException("BN_primality_test failed")
            }

            if (is_probably_prime.value != 0)
                return true
        }

        return false
    }

    // &BN_free
    /* TODO IOS Maybe needed? - commented out in BigInt.kt
    fun getNativeFinalizer(): Long
    */
}
