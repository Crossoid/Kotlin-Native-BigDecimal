# Kotlin/Native BigDecimal (Kotlin Multiplatform for iOS)

This is a drop-in replacement for java.math.BigDecimal.  If you have a
Kotlin/JVM project, want to port it to Kotlin/Native iOS, but struggle because
you are using java.math.BigDecimal and cannot find an implementation, this is
the library you want to use.

The code is production-ready and used in the iOS port of [HiPER Scientific
Calculator](https://apps.apple.com/us/app/hiper-scientific-calculator/id1645513530).

This library has the same API as java.math.BigDecimal, all you need to do
is to add the GitHub Packages repository and the library to your build.gradle.kts,
because binary packages are now available.

    val iosArm64Main by getting {
        dependencies {
            implementation("com.crossoid:kotlin-native-bigdecimal:1.2.0")
        }
    }

Then, you can import the BigDecimal classes as if you were developing for Kotlin/JVM:

    import java.math.BigDecimal

# Release notes

Binary packages are provided via GitHub Packages, please see the
[example project](https://github.com/Crossoid/Kotlin-Native-BigDecimal-Example)
for exact details how to import the library and how to use it. The following
versions were released so far:

* 1.2.0
  - Prevented iOS memory exhaustion by deterministically releasing temporary
    BIGNUM values used by division, rounding, multiplication, and scaled arithmetic
  - Reduced allocations in BigDecimal division, rounding, precision calculation,
    trailing-zero stripping, scale alignment, and comparison hot paths
  - Added regression coverage for large values, signs, rounding boundaries, and
    scoped native ownership
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

        cd bignum/ios

        git clone git@github.com:google/boringssl.git
        cd boringssl
        cd ../../..

  Then cleanly configure and rebuild optimized device and simulator libraries:

        ./gradlew rebuildIosBoringSsl

  The task removes both CMake build directories, configures Release builds with
  `-O3 -DNDEBUG` using the active Xcode SDKs, and builds the `crypto` and `ssl`
  static libraries. Use `rebuildIosArm64BoringSsl` or
  `rebuildIosSimulatorArm64BoringSsl` when only one target is needed.

* Build the BigDecimal.klib and BigDecimal-cinterop-boringssl.klib

  Simulator:

        ./gradlew compileKotlinIosSimulatorArm64

	(resulting libraries are in: build/classes/kotlin/iosSimulatorArm64/main/)

  Device:

        ./gradlew compileKotlinIosArm64

	(resulting libraries are in: build/classes/kotlin/iosArm64/main/)

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

* The build of BoringSSL has to be done manually - would be great to extend
  the build.gradle.kts to clone / build it as part of the build just out of
  the box, and for the correct platform.

* Automate the build of the Kotlin/JVM part too - so that it can be used for
  Android development too if necessary for some reason.

* Unit tests!  There are no incorporated automated tests so far, would be great to have at
  least few as a start...

* And anything else you'd be interested in :-)

For patches, please just do PR's & I'll review them.  For bugs, please create
GitHub issues.
