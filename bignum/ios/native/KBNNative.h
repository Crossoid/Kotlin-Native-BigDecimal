#import <Foundation/Foundation.h>

#include <stdint.h>

NS_ASSUME_NONNULL_BEGIN

FOUNDATION_EXPORT int32_t KBNBitLength(uintptr_t value, int32_t *result);
FOUNDATION_EXPORT int32_t KBNGcd(uintptr_t result, uintptr_t first, uintptr_t second);
FOUNDATION_EXPORT int32_t KBNMultiply(uintptr_t result, uintptr_t first, uintptr_t second);
FOUNDATION_EXPORT int32_t KBNExponentiate(uintptr_t result, uintptr_t value, uintptr_t power);
FOUNDATION_EXPORT int32_t KBNDivide(
    uintptr_t quotient,
    uintptr_t remainder,
    uintptr_t dividend,
    uintptr_t divisor
);
FOUNDATION_EXPORT int32_t KBNDivideWithRemainderComparison(
    uintptr_t quotient,
    uintptr_t dividend,
    uintptr_t divisor,
    int32_t *comparison
);
FOUNDATION_EXPORT int32_t KBNNonNegativeModulo(
    uintptr_t result,
    uintptr_t value,
    uintptr_t modulus
);
FOUNDATION_EXPORT int32_t KBNModularExponentiation(
    uintptr_t result,
    uintptr_t value,
    uintptr_t power,
    uintptr_t modulus
);
FOUNDATION_EXPORT int32_t KBNModularInverse(
    uintptr_t result,
    uintptr_t value,
    uintptr_t modulus
);
FOUNDATION_EXPORT int32_t KBNPrimalityTest(
    uintptr_t candidate,
    int32_t checks,
    int32_t performTrialDivision,
    int32_t *isProbablyPrime
);

NS_ASSUME_NONNULL_END
