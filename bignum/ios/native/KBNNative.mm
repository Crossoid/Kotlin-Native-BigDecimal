#import "KBNNative.h"

#include <limits.h>
#include <openssl/bn.h>

namespace {

constexpr int32_t contextAllocationFailed = -1;

class ThreadContext {
public:
    ~ThreadContext() {
        BN_CTX_free(value);
    }

    BN_CTX *get() {
        if (value == nullptr) {
            value = BN_CTX_new();
        }
        return value;
    }

private:
    BN_CTX *value = nullptr;
};

thread_local ThreadContext threadContext;

inline BIGNUM *toBignum(uintptr_t value) {
    return reinterpret_cast<BIGNUM *>(value);
}

inline const BIGNUM *toConstBignum(uintptr_t value) {
    return reinterpret_cast<const BIGNUM *>(value);
}

}  // namespace

int32_t KBNBitLength(uintptr_t value, int32_t *result) {
    const BIGNUM *source = toConstBignum(value);
    if (!BN_is_negative(source)) {
        *result = BN_num_bits(source);
        return 1;
    }

    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }

    BN_CTX_start(context);
    BIGNUM *magnitude = BN_CTX_get(context);
    if (magnitude == nullptr || BN_copy(magnitude, source) == nullptr) {
        BN_CTX_end(context);
        return 0;
    }

    BN_set_negative(magnitude, 0);
    *result = BN_is_pow2(magnitude) ? BN_num_bits(magnitude) - 1 : BN_num_bits(magnitude);
    BN_CTX_end(context);
    return 1;
}

int32_t KBNGcd(uintptr_t result, uintptr_t first, uintptr_t second) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_gcd(
        toBignum(result),
        toConstBignum(first),
        toConstBignum(second),
        context
    );
}

int32_t KBNMultiply(uintptr_t result, uintptr_t first, uintptr_t second) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_mul(
        toBignum(result),
        toConstBignum(first),
        toConstBignum(second),
        context
    );
}

int32_t KBNExponentiate(uintptr_t result, uintptr_t value, uintptr_t power) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_exp(
        toBignum(result),
        toConstBignum(value),
        toConstBignum(power),
        context
    );
}

int32_t KBNDivide(
    uintptr_t quotient,
    uintptr_t remainder,
    uintptr_t dividend,
    uintptr_t divisor
) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_div(
        toBignum(quotient),
        toBignum(remainder),
        toConstBignum(dividend),
        toConstBignum(divisor),
        context
    );
}

int32_t KBNDivideWithRemainderComparison(
    uintptr_t quotient,
    uintptr_t dividend,
    uintptr_t divisor,
    int32_t *comparison
) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }

    BN_CTX_start(context);
    BIGNUM *remainder = BN_CTX_get(context);
    if (remainder == nullptr) {
        BN_CTX_end(context);
        return contextAllocationFailed;
    }
    if (!BN_div(
            toBignum(quotient),
            remainder,
            toConstBignum(dividend),
            toConstBignum(divisor),
            context
        )) {
        BN_CTX_end(context);
        return 0;
    }

    if (BN_is_zero(remainder)) {
        *comparison = INT32_MIN;
    } else {
        if (!BN_lshift(remainder, remainder, 1)) {
            BN_CTX_end(context);
            return 0;
        }
        *comparison = BN_ucmp(remainder, toConstBignum(divisor));
    }

    BN_CTX_end(context);
    return 1;
}

int32_t KBNNonNegativeModulo(uintptr_t result, uintptr_t value, uintptr_t modulus) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_nnmod(
        toBignum(result),
        toConstBignum(value),
        toConstBignum(modulus),
        context
    );
}

int32_t KBNModularExponentiation(
    uintptr_t result,
    uintptr_t value,
    uintptr_t power,
    uintptr_t modulus
) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_mod_exp(
        toBignum(result),
        toConstBignum(value),
        toConstBignum(power),
        toConstBignum(modulus),
        context
    );
}

int32_t KBNModularInverse(uintptr_t result, uintptr_t value, uintptr_t modulus) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }
    return BN_mod_inverse(
        toBignum(result),
        toConstBignum(value),
        toConstBignum(modulus),
        context
    ) != nullptr;
}

int32_t KBNPrimalityTest(
    uintptr_t candidate,
    int32_t checks,
    int32_t performTrialDivision,
    int32_t *isProbablyPrime
) {
    BN_CTX *context = threadContext.get();
    if (context == nullptr) {
        return contextAllocationFailed;
    }

    int nativeResult = 0;
    int32_t success = BN_primality_test(
        &nativeResult,
        toConstBignum(candidate),
        checks,
        context,
        performTrialDivision,
        nullptr
    );
    *isProbablyPrime = nativeResult;
    return success;
}
