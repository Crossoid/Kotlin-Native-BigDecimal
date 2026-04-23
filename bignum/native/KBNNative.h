#ifndef KBN_NATIVE_H
#define KBN_NATIVE_H

#include <stdint.h>

#ifdef __cplusplus
#define KBN_EXPORT extern "C"
#else
#define KBN_EXPORT extern
#endif

KBN_EXPORT int32_t KBNBitLength(uintptr_t value);
KBN_EXPORT int32_t KBNGcd(uintptr_t result, uintptr_t first, uintptr_t second);
KBN_EXPORT int32_t KBNMultiply(uintptr_t result, uintptr_t first, uintptr_t second);
KBN_EXPORT int32_t KBNExponentiate(uintptr_t result, uintptr_t value, uintptr_t power);
KBN_EXPORT int32_t KBNDivide(
    uintptr_t quotient,
    uintptr_t remainder,
    uintptr_t dividend,
    uintptr_t divisor
);
KBN_EXPORT int32_t KBNDivideWithRemainderComparison(
    uintptr_t quotient,
    uintptr_t dividend,
    uintptr_t divisor
);
KBN_EXPORT int32_t KBNNonNegativeModulo(
    uintptr_t result,
    uintptr_t value,
    uintptr_t modulus
);
KBN_EXPORT int32_t KBNModularExponentiation(
    uintptr_t result,
    uintptr_t value,
    uintptr_t power,
    uintptr_t modulus
);
KBN_EXPORT int32_t KBNModularInverse(
    uintptr_t result,
    uintptr_t value,
    uintptr_t modulus
);
KBN_EXPORT int32_t KBNPrimalityTest(
    uintptr_t candidate,
    int32_t checks,
    int32_t performTrialDivision,
    int32_t *isProbablyPrime
);

#endif
