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
internal actual object NativeBN {
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
        checkValid(a)
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
    fun BN_new(): Long {
        return toLong(boringssl.BN_new())
    }

    // void BN_free(BIGNUM *a);
    fun BN_free(a: Long) {
        boringssl.BN_free(toBigNum(a))
    }

    // int BN_cmp(const BIGNUM *a, const BIGNUM *b);
    fun BN_cmp(a: Long, b: Long): Int {
        return boringssl.BN_cmp(toBigNum(a), toBigNum(b));
    }

    // BIGNUM *BN_copy(BIGNUM *to, const BIGNUM *from);
    fun BN_copy(to: Long, from: Long) {
        checkValid(boringssl.BN_copy(toBigNum(to), toBigNum(from)))
    }

    fun putLongInt(a: Long, dw: Long) {
        if (dw >= 0) {
            putULongInt(a, dw, false);
        } else {
            putULongInt(a, -dw, true);
        }
    }

    fun putULongInt(a: Long, dw: Long, neg: Boolean) {
        val bnA = toBigNum(a)
        if (boringssl.BN_set_u64(bnA, dw.toULong()) == 0) {
            throw ArithmeticException("BN_set_u64 failed")
            return
        }

        boringssl.BN_set_negative(bnA, neg.toInt());
    }

    // int BN_dec2bn(BIGNUM **a, const char *str);
    fun BN_dec2bn(a: Long, str: String?): Int {
        val result = boringssl.BN_dec2bn(cValuesOf(toBigNum(a)), str);
        if (result == 0) {
            throw ArithmeticException("BN_dec2bn failed")
        }
        return result;
    }

    // int BN_hex2bn(BIGNUM **a, const char *str);
    fun BN_hex2bn(a: Long, str: String?): Int {
        val result = boringssl.BN_hex2bn(cValuesOf(toBigNum(a)), str);
        if (result == 0) {
            throw ArithmeticException("BN_hex2bn failed")
        }
        return result;
    }

    // BIGNUM * BN_bin2bn(const unsigned char *s, int len, BIGNUM *ret);
    // BN-Docu: s is taken as unsigned big endian;
    // Additional parameter: neg.
    fun BN_bin2bn(s: ByteArray?, len: Int, neg: Boolean, ret: Long) {
        val retBN = toBigNum(ret)
        // "pin" the ByteArray to fix it in memory at a given place
        s?.usePinned { pinned ->
            checkValid(boringssl.BN_bin2bn(pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>(), len.toULong(), retBN))
        }

        boringssl.BN_set_negative(retBN, neg.toInt());
    }

    fun litEndInts2bn(ints: IntArray?, len: Int, neg: Boolean, ret: Long) {
        // TODO assert(ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)

        val retBN = toBigNum(ret);
        // "pin" the IntArray to fix it in memory at a given place
        ints?.usePinned { pinned ->
            checkValid(boringssl.BN_le2bn(pinned.addressOf(0).reinterpret<UByteVarOf<UByte>>(), len.toULong() * 4UL /* sizeof Int */, retBN))
        }

        boringssl.BN_set_negative(retBN, neg.toInt());
    }

    external fun twosComp2bn(s: ByteArray?, len: Int, ret: Long)
    external fun longInt(a: Long): Long

    // unsigned long BN_get_word(BIGNUM *a);
    external fun BN_bn2dec(a: Long): String?

    // char * BN_bn2dec(const BIGNUM *a);
    external fun BN_bn2hex(a: Long): String?

    // char * BN_bn2hex(const BIGNUM *a);
    external fun BN_bn2bin(a: Long): ByteArray?

    // Returns result byte[] AND NOT length.
    // int BN_bn2bin(const BIGNUM *a, unsigned char *to);
    external fun bn2litEndInts(a: Long): IntArray?

    // Returns -1, 0, 1 AND NOT boolean.
    // #define BN_is_negative(a) ((a)->neg != 0)
    fun sign(a: Long): Int {
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
    fun BN_set_negative(b: Long, n: Int) {
        checkValid(b)
        boringssl.BN_set_negative(toBigNum(b), n);
    }

    external fun bitLength(a: Long): Int

    // int BN_is_bit_set(const BIGNUM *a, int n);
    fun BN_is_bit_set(a: Long, n: Int): Boolean {
        checkValid(a)

        // NOTE: this is only called in the positive case, so BN_is_bit_set is fine here.
        if (boringssl.BN_is_bit_set(toBigNum(a), n) != 0)
            return true

        return false
    }

    // int BN_shift(BIGNUM *r, const BIGNUM *a, int n);
    fun BN_shift(r: Long, a: Long, n: Int) {
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
    fun BN_add_word(a: Long, w: Int) {
        checkValid(a)

        if (boringssl.BN_add_word(toBigNum(a), w.toULong()) == 0) {
            throw ArithmeticException("BN_add_word failed")
        }
    }

    // ATTENTION: w is treated as unsigned.
    // int BN_mul_word(BIGNUM *a, BN_ULONG w);
    fun BN_mul_word(a: Long, w: Int) {
        checkValid(a)

        if (boringssl.BN_mul_word(toBigNum(a), w.toULong()) == 0) {
            throw ArithmeticException("BN_mul_word failed")
        }
    }

    // ATTENTION: w is treated as unsigned.
    // BN_ULONG BN_mod_word(BIGNUM *a, BN_ULONG w);
    fun BN_mod_word(a: Long, w: Int): Int {
        checkValid(a)

        val result = boringssl.BN_mod_word(toBigNum(a), w.toULong());
        if (result == (-1).toULong()) {
            throw ArithmeticException("BN_mod_word failed")
        }
        return result.toInt();
    }

    // int BN_add(BIGNUM *r, const BIGNUM *a, const BIGNUM *b);
    fun BN_add(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        if (boringssl.BN_add(toBigNum(r), toBigNum(a), toBigNum(b)) == 0) {
            throw ArithmeticException("BN_add failed")
        }
    }

    // int BN_sub(BIGNUM *r, const BIGNUM *a, const BIGNUM *b);
    fun BN_sub(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        if (boringssl.BN_sub(toBigNum(r), toBigNum(a), toBigNum(b)) == 0) {
            throw ArithmeticException("BN_sub failed")
        }
    }

    // int BN_gcd(BIGNUM *r, const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);
    fun BN_gcd(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_gcd(toBigNum(r), toBigNum(a), toBigNum(b), ctx) == 0) {
            throw ArithmeticException("BN_gcd failed")
        }
    }

    // int BN_mul(BIGNUM *r, const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);
    fun BN_mul(r: Long, a: Long, b: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(b)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_mul(toBigNum(r), toBigNum(a), toBigNum(b), ctx) == 0) {
            throw ArithmeticException("BN_mul failed")
        }
    }

    // int BN_exp(BIGNUM *r, const BIGNUM *a, const BIGNUM *p, BN_CTX *ctx);
    fun BN_exp(r: Long, a: Long, p: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(p)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_exp(toBigNum(r), toBigNum(a), toBigNum(p), ctx) == 0) {
            throw ArithmeticException("BN_exp failed")
        }
    }

    // int BN_div(BIGNUM *dv, BIGNUM *rem, const BIGNUM *m, const BIGNUM *d, BN_CTX *ctx);
    fun BN_div(dv: Long, rem: Long, m: Long, d: Long) {
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
    fun BN_nnmod(r: Long, a: Long, m: Long) {
        checkValid(r)
        checkValid(a)
        checkValid(m)

        val ctx = boringssl.BN_CTX_new()
        if (boringssl.BN_nnmod(toBigNum(r), toBigNum(a), toBigNum(m), ctx) == 0) {
            throw ArithmeticException("BN_nnmod failed")
        }
    }

    // int BN_mod_exp(BIGNUM *r, const BIGNUM *a, const BIGNUM *p, const BIGNUM *m, BN_CTX *ctx);
    fun BN_mod_exp(r: Long, a: Long, p: Long, m: Long) {
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
    fun BN_mod_inverse(ret: Long, a: Long, n: Long) {
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
    fun BN_generate_prime_ex(ret: Long, bits: Int, safe: Boolean, add: Long, rem: Long) {
        checkValid(ret)

        if (boringssl.BN_generate_prime_ex(toBigNum(ret), bits, safe.toInt(), toBigNum(add), toBigNum(rem), null) == 0) {
            throw ArithmeticException("BN_generate_prime_ex failed")
        }
    }

    // int BN_primality_test(int *is_probably_prime, const BIGNUM *candidate, int checks,
    //                       BN_CTX *ctx, int do_trial_division, BN_GENCB *cb);
    // Returns *is_probably_prime on success and throws an exception on error.
    fun BN_primality_test(candidate: Long, checks: Int, do_trial_division: Boolean): Boolean {
        checkValid(candidate)

        val ctx = boringssl.BN_CTX_new()
        var is_probably_prime: Int = 0
        if (boringssl.BN_primality_test(cValuesOf(is_probably_prime), toBigNum(candidate), checks, ctx, do_trial_division.toInt(), null) == 0) {
            throw ArithmeticException("BN_primality_test failed")
        }

        if (is_probably_prime != 0)
            return true

        return false
    }

    // &BN_free
    external fun getNativeFinalizer(): Long
}
