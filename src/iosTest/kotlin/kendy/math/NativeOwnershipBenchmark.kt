package kendy.math

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.time.TimeSource
import platform.posix.RUSAGE_SELF
import platform.posix.getrusage
import platform.posix.getenv
import platform.posix.rusage

private const val BENCHMARK_ENVIRONMENT_VARIABLE = "KBN_BENCHMARK"

@OptIn(ExperimentalForeignApi::class)
private fun benchmarkEnabled(): Boolean {
    return getenv(BENCHMARK_ENVIRONMENT_VARIABLE)?.toKString() == "1"
}

@OptIn(NativeRuntimeApi::class)
private fun collectGarbage() {
    GC.collect()
}

class NativeOwnershipBenchmark {
    private val largeLeft = BigInteger.TEN.pow(256).add(BigInteger.valueOf(123456789))
    private val largeRight = BigInteger.TEN.pow(192).add(BigInteger.valueOf(987654321))

    @Test
    fun additionOwnership() {
        if (!benchmarkEnabled()) return

        repeat(10_000) { index ->
            if (index and 1 == 0) largeLeft.add(largeRight) else largeLeft.subtract(largeRight)
        }
        collectGarbage()

        var checksum = 0L
        val start = TimeSource.Monotonic.markNow()
        repeat(300_000) { index ->
            val result = if (index and 1 == 0) {
                largeLeft.add(largeRight)
            } else {
                largeLeft.subtract(largeRight)
            }
            checksum += result.bitLength().toLong()
        }
        report("addition-ownership", 300_000, start.elapsedNow().inWholeNanoseconds, checksum)
    }

    @Test
    fun multiplicationContext() {
        if (!benchmarkEnabled()) return

        repeat(5_000) {
            largeLeft.multiply(largeRight)
        }
        collectGarbage()

        var checksum = 0L
        val start = TimeSource.Monotonic.markNow()
        repeat(100_000) {
            checksum += largeLeft.multiply(largeRight).bitLength().toLong()
        }
        report("multiplication-context", 100_000, start.elapsedNow().inWholeNanoseconds, checksum)
    }

    @Test
    fun decimalRounding() {
        if (!benchmarkEnabled()) return

        val dividend = BigDecimal(largeLeft.multiply(largeRight))
        val divisor = BigDecimal(largeRight.add(BigInteger.valueOf(37)))
        val context = MathContext(80, RoundingMode.HALF_EVEN)

        repeat(1_000) {
            dividend.divide(divisor, context)
        }
        collectGarbage()

        var checksum = 0L
        val start = TimeSource.Monotonic.markNow()
        repeat(20_000) {
            checksum += dividend.divide(divisor, context).precision().toLong()
        }
        report("decimal-rounding", 20_000, start.elapsedNow().inWholeNanoseconds, checksum)
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun report(name: String, iterations: Int, elapsedNanoseconds: Long, checksum: Long) {
        assertNotEquals(0L, checksum)
        val maximumResidentSetSize = memScoped {
            val usage = alloc<rusage>()
            check(getrusage(RUSAGE_SELF, usage.ptr) == 0)
            usage.ru_maxrss
        }
        println(
            "KBN_BENCHMARK name=$name iterations=$iterations " +
                "elapsed_ns=$elapsedNanoseconds ns_per_op=${elapsedNanoseconds / iterations} " +
                "max_rss_bytes=$maximumResidentSetSize checksum=$checksum"
        )
    }
}
