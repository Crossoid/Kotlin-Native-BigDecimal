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

internal object NativeBN {
    external fun BN_new(): Long

    // BIGNUM *BN_new(void);
    external fun BN_free(a: Long)

    // void BN_free(BIGNUM *a);
    external fun BN_cmp(a: Long, b: Long): Int

    // int BN_cmp(const BIGNUM *a, const BIGNUM *b);
    external fun BN_copy(to: Long, from: Long)

    // BIGNUM *BN_copy(BIGNUM *to, const BIGNUM *from);
    external fun putLongInt(a: Long, dw: Long)
    external fun putULongInt(a: Long, dw: Long, neg: Boolean)
    external fun BN_dec2bn(a: Long, str: String?): Int

    // int BN_dec2bn(BIGNUM **a, const char *str);
    external fun BN_hex2bn(a: Long, str: String?): Int

    // int BN_hex2bn(BIGNUM **a, const char *str);
    external fun BN_bin2bn(s: ByteArray?, len: Int, neg: Boolean, ret: Long)

    // BIGNUM * BN_bin2bn(const unsigned char *s, int len, BIGNUM *ret);
    // BN-Docu: s is taken as unsigned big endian;
    // Additional parameter: neg.
    external fun litEndInts2bn(ints: IntArray?, len: Int, neg: Boolean, ret: Long)
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
    external fun sign(a: Long): Int

    // Returns -1, 0, 1 AND NOT boolean.
    // #define BN_is_negative(a) ((a)->neg != 0)
    external fun BN_set_negative(b: Long, n: Int)

    // void BN_set_negative(BIGNUM *b, int n);
    external fun bitLength(a: Long): Int
    external fun BN_is_bit_set(a: Long, n: Int): Boolean

    // int BN_is_bit_set(const BIGNUM *a, int n);
    external fun BN_shift(r: Long, a: Long, n: Int)

    // int BN_shift(BIGNUM *r, const BIGNUM *a, int n);
    external fun BN_add_word(a: Long, w: Int)

    // ATTENTION: w is treated as unsigned.
    // int BN_add_word(BIGNUM *a, BN_ULONG w);
    external fun BN_mul_word(a: Long, w: Int)

    // ATTENTION: w is treated as unsigned.
    // int BN_mul_word(BIGNUM *a, BN_ULONG w);
    external fun BN_mod_word(a: Long, w: Int): Int

    // ATTENTION: w is treated as unsigned.
    // BN_ULONG BN_mod_word(BIGNUM *a, BN_ULONG w);
    external fun BN_add(r: Long, a: Long, b: Long)

    // int BN_add(BIGNUM *r, const BIGNUM *a, const BIGNUM *b);
    external fun BN_sub(r: Long, a: Long, b: Long)

    // int BN_sub(BIGNUM *r, const BIGNUM *a, const BIGNUM *b);
    external fun BN_gcd(r: Long, a: Long, b: Long)

    // int BN_gcd(BIGNUM *r, const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);
    external fun BN_mul(r: Long, a: Long, b: Long)

    // int BN_mul(BIGNUM *r, const BIGNUM *a, const BIGNUM *b, BN_CTX *ctx);
    external fun BN_exp(r: Long, a: Long, p: Long)

    // int BN_exp(BIGNUM *r, const BIGNUM *a, const BIGNUM *p, BN_CTX *ctx);
    external fun BN_div(dv: Long, rem: Long, m: Long, d: Long)

    // int BN_div(BIGNUM *dv, BIGNUM *rem, const BIGNUM *m, const BIGNUM *d, BN_CTX *ctx);
    external fun BN_nnmod(r: Long, a: Long, m: Long)

    // int BN_nnmod(BIGNUM *r, const BIGNUM *a, const BIGNUM *m, BN_CTX *ctx);
    external fun BN_mod_exp(r: Long, a: Long, p: Long, m: Long)

    // int BN_mod_exp(BIGNUM *r, const BIGNUM *a, const BIGNUM *p, const BIGNUM *m, BN_CTX *ctx);
    external fun BN_mod_inverse(ret: Long, a: Long, n: Long)

    // BIGNUM * BN_mod_inverse(BIGNUM *ret, const BIGNUM *a, const BIGNUM *n, BN_CTX *ctx);
    external fun BN_generate_prime_ex(
        ret: Long, bits: Int, safe: Boolean,
        add: Long, rem: Long
    )

    // int BN_generate_prime_ex(BIGNUM *ret, int bits, int safe,
    //         const BIGNUM *add, const BIGNUM *rem, BN_GENCB *cb);
    external fun BN_primality_test(
        candidate: Long, checks: Int,
        do_trial_division: Boolean
    ): Boolean

    // int BN_primality_test(int *is_probably_prime, const BIGNUM *candidate, int checks,
    //                       BN_CTX *ctx, int do_trial_division, BN_GENCB *cb);
    // Returns *is_probably_prime on success and throws an exception on error.
    // &BN_free
    val nativeFinalizer: Long
        external get
}