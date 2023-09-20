# Kotline/Native BigDecimal

This is a drop-in replacement for java.math.BigDecimal.  If you have a
Kotlin/JVM project, want to port it to Kotlin/Native, but struggle because
you are using java.math.BigDecimal and cannot find an implementation, this is
the library you want to use.

The code is production-ready and used in the iOS port of [HiPER Scientific
Calculator](https://apps.apple.com/us/app/hiper-scientific-calculator/id1645513530).

This library (BigDecimal.klib) has the same API as java.math.BigDecimal, all
you need to do is to add the library to your build.gradle.kts like:

    val iosArm64Main by getting {
        dependencies {
            implementation(files("libs/iosArm64/BigDecimal.klib"))
            implementation(files("libs/iosArm64/BigDecimal-cinterop-boringssl.klib"))
        }
    }

And then, import the BigDecimal classes as if you were developing for Kotlin/JVM:

    import java.math.BigDecimal

Please read below how to build the actual BigDecimal.klib and BigDecimal-cineterop-boringssl.klib,
I am not providing binary builds yet (sorry about that!).

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

  See bignum/README.md for details, but mostly down to:

        cd bignum/ios

        git clone git@github.com:google/boringssl.git
        cd boringssl
        mkdir build-arm64
        cd build-arm64

  Simulator:

        /Applications/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphonesimulator -DCMAKE_OSX_ARCHITECTURES=arm64 ..
        make -j8

  Device:

        /Applications/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphoneos -DCMAKE_OSX_ARCHITECTURES=arm64 ..
        make -j8

        cd ../../../..

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
