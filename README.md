# Kotlin/Native BigDecimal (Kotlin Multiplatform for iOS, macOS, watchOS, tvOS and Linux)

This is a drop-in replacement for java.math.BigDecimal.  If you have a
Kotlin/JVM project, want to port it to Kotlin/Native iOS, but struggle because
you are using java.math.BigDecimal and cannot find an implementation, this is
the library you want to use.

The following Kotlin/Native targets are supported:

* iOS: `iosArm64`, `iosX64`, `iosSimulatorArm64`
* macOS: `macosArm64`, `macosX64`
* watchOS: `watchosArm64`, `watchosArm32`, `watchosDeviceArm64`, `watchosX64`,
  `watchosSimulatorArm64` (for `watchosArm32`, only the klib is built - Xcode
  14+ cannot link armv7k anymore)
* tvOS: `tvosArm64`, `tvosX64`, `tvosSimulatorArm64`
* Linux: `linuxX64`, `linuxArm64`

The code is production-ready, optimized for performance, and used in the iOS port of [HiPER Scientific
Calculator](https://apps.apple.com/us/app/hiper-scientific-calculator/id1645513530).

This library has the same API as java.math.BigDecimal, all you need to do
is to add the GitHub Packages repository and the library to your build.gradle.kts,
because binary packages are now available.

    val iosArm64Main by getting {
        dependencies {
            implementation("com.crossoid:kotlin-native-bigdecimal:1.2.1")
        }
    }

Then, you can import the BigDecimal classes as if you were developing for Kotlin/JVM:

    import java.math.BigDecimal

# Release notes

Binary packages are provided via GitHub Packages, please see the
[example project](https://github.com/Crossoid/Kotlin-Native-BigDecimal-Example)
for exact details how to import the library and how to use it. The following
versions were released so far:

* 1.2.1
  - Matched Java BigInteger's power overflow limit
  - Improved native arithmetic performance and memory use
  - Migrated the build to Gradle 9 and the Android-KMP library plugin
  - Automated optimized BoringSSL rebuilds
* 1.2.0
  - Fixed iOS memory exhaustion caused by temporary BIGNUM leaks
  - Reduced BigDecimal arithmetic and comparison allocations
  - Added native ownership and rounding regression coverage
* 1.1.1
  - Fixed leaking the native BIGNUM owned by every BigInt on iOS
  - Restored safe BigInteger parsing and formatting for radices 2 through 36
  - Made lazy BigInteger representation initialization thread-safe
  - Added an explicit native byte-order guard
* 1.1.0
  - Fixed a serious memory leak
  - Changed nullable BigDecimal? returns to non-null BigDecimal
* 1.0.1
  - Added unary plus and unary minus for BigDecimal
* 1.0
  - First packages released

# How does it work

The library is a port of java.math.BigDecimal from the Android Open Source
Project that I've converted from Java to Kotlin using the Android Studio's
Java -> Kotlin converter, fixed and/or adapted various places, and
implemented the native part.

For the native part, I've rewritten the JNI version to Kotlin/Native using the
[cinterops](https://kotlinlang.org/docs/native-c-interop.html).

The BigDecimal as a whole builds on top of the Google's
[BoringSSL](https://boringssl.googlesource.com/boringssl/) BIGNUM
implementation, so you will need to build a BoringSSL static library for all
this to work.  If you are interested in the details, see bignum/README.md; or
just continue reading how to build it all.

# Building

I've tested this both with the iOS Simulator and a device.  To build, do:

* Install dependencies for building BoringSSL

  You need [CMake](https://cmake.org/) and [Go](https://golang.org/).

* Build BoringSSL

  Clone BoringSSL into the expected source directory:

        git clone https://boringssl.googlesource.com/boringssl bignum/boringssl

  BoringSSL (and the small native wrapper in bignum/native) is then built
  automatically for every target that you build, into
  bignum/boringssl/build-<target>. To cleanly configure and rebuild the
  optimized libraries, run:

        ./gradlew rebuildBoringSsl

  The task removes the CMake build directories, configures Release builds with
  `-O3 -DNDEBUG` and builds the `crypto` static library. Use
  `rebuildIosBoringSsl` for the iOS targets only, or `rebuild<Target>BoringSsl`
  (e.g. `rebuildIosArm64BoringSsl`) when only one target is needed.

  Apple targets are built with the active Xcode SDKs, so they have to be built
  on macOS. Linux targets are cross-compiled with the clang and the sysroot
  that Kotlin/Native itself links with (downloaded automatically into
  ~/.konan), so they can be built on macOS too.

* Build the BigDecimal.klib and BigDecimal-cinterop-boringssl.klib

  Run the compile task of the target you want, e.g.:

        ./gradlew compileKotlinIosSimulatorArm64
        ./gradlew compileKotlinIosArm64
        ./gradlew compileKotlinMacosArm64
        ./gradlew compileKotlinLinuxX64

	(resulting libraries are in: build/classes/kotlin/<target>/main/)

* Run the tests

  Run the test task of the target you want, e.g.:

        ./gradlew macosArm64Test

* Incorporate the resulting klibs into your project

  You can copy them to some convenient location, and then update your
  build.gradle.kts:

        val iosMain by getting {
            dependencies {
                implementation(files("libs/BigDecimal.klib"))
                implementation(files("libs/BigDecimal-cinterop-boringssl.klib"))
            }
        }

  Then change the imports of java.math.BigDecimal to kendy.math.BigDecimal and
  you are done.

# Contributing

I use this code in production in the iOS port of [HiPER Scientific
Calculator](https://apps.apple.com/us/app/hiper-scientific-calculator/id1645513530)
where it undergoes over 2000 unit tests, so I am pretty sure it is stable and
produces good results.

As such, it does not need too much work, apart from making it more generally
usable as a drop-in replacement for the Java BigDecimal (like convenience classes,
extension functions, etc.). But if I find bugs, I will fix them.

I'll be excited to incorporate your patches if you want to contribute!  Here
are some ideas what to improve if you want to help:

* The code builds with various warnings - I'll appreciate patches to fix
  those.

* The BoringSSL sources have to be cloned manually - would be great to extend
  the build.gradle.kts to clone them as part of the build just out of the box.

* Automate the build of the Kotlin/JVM part too - so that it can be used for
  Android development too if necessary for some reason.

* Unit tests!  There are no incorporated automated tests so far, would be great to have at
  least few as a start...

* And anything else you'd be interested in :-)

For patches, please just do PR's & I'll review them.  For bugs, please create
GitHub issues.
