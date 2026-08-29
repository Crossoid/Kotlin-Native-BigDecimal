# iOS native ownership benchmark

The benchmark compares persistent BIGNUM ownership, reusable `BN_CTX` performance,
and a representative rounded `BigDecimal` division. It is compiled as an optimized
iOS Simulator test binary and remains a no-op in the regular test suite unless
`KBN_BENCHMARK=1` is set.

Build the benchmark with:

```shell
./gradlew linkBenchmarkReleaseTestIosSimulatorArm64
```

Boot an iOS Simulator, enable the benchmark environment variable in that simulator,
and run an individual workload with:

```shell
xcrun simctl bootstatus <device-id> -b
xcrun simctl spawn <device-id> launchctl setenv KBN_BENCHMARK 1
xcrun simctl spawn <device-id> \
  <repository>/build/bin/iosSimulatorArm64/benchmarkReleaseTest/benchmark.kexe \
  --ktest_filter=kendy.math.NativeOwnershipBenchmark.additionOwnership \
  --ktest_logger=SIMPLE
xcrun simctl spawn <device-id> launchctl unsetenv KBN_BENCHMARK
```

Use a fresh process for every measurement. Each result reports elapsed and
per-operation time, peak resident memory, and a checksum that keeps the measured
operations observable.
