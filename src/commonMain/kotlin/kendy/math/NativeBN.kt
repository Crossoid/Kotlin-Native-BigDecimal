package kendy.math

/**
 * Expect declaration of the NativeBN so that we can have both the
 * Kotlin/Native-based and JNI-based implementations.
 */
expect internal object NativeBN {
    fun BN_new(): Long

    fun BN_cmp(a: Long, b: Long): Int

    fun BN_copy(to: Long, from: Long)

    fun putLongInt(a: Long, dw: Long)

    fun putULongInt(a: Long, dw: Long, neg: Boolean)

    fun BN_dec2bn(a: Long, str: String?): Int

    fun BN_hex2bn(a: Long, str: String?): Int

    fun BN_bin2bn(s: ByteArray?, len: Int, neg: Boolean, ret: Long)

    fun litEndInts2bn(ints: IntArray?, len: Int, neg: Boolean, ret: Long)

    fun twosComp2bn(s: ByteArray?, len: Int, ret: Long)

    fun longInt(a: Long): Long

    fun BN_bn2dec(a: Long): String?

    fun BN_bn2hex(a: Long): String?

    fun BN_bn2bin(a: Long): ByteArray?

    fun bn2litEndInts(a: Long): IntArray?

    fun sign(a: Long): Int

    fun BN_set_negative(b: Long, n: Int)

    fun bitLength(a: Long): Int

    fun BN_is_bit_set(a: Long, n: Int): Boolean

    fun BN_shift(r: Long, a: Long, n: Int)

    fun BN_add_word(a: Long, w: Int)

    fun BN_mul_word(a: Long, w: Int)

    fun BN_mod_word(a: Long, w: Int): Int

    fun BN_add(r: Long, a: Long, b: Long)

    fun BN_sub(r: Long, a: Long, b: Long)

    fun BN_gcd(r: Long, a: Long, b: Long)

    fun BN_mul(r: Long, a: Long, b: Long)

    fun BN_exp(r: Long, a: Long, p: Long)

    fun BN_div(dv: Long, rem: Long, m: Long, d: Long)

    fun BN_nnmod(r: Long, a: Long, m: Long)

    fun BN_mod_exp(r: Long, a: Long, p: Long, m: Long)

    fun BN_mod_inverse(ret: Long, a: Long, n: Long)

    fun BN_generate_prime_ex(ret: Long, bits: Int, safe: Boolean, add: Long, rem: Long)

    fun BN_primality_test(candidate: Long, checks: Int, do_trial_division: Boolean): Boolean
}
