# Kotline/Native BigDecimal

This is a drop-in replacement for java.math.BigDecimal.  If you are have a
Kotlin/JVM project, want to port it to Kotlin/Native, but struggle because
you are using java.math.BigDecimal and cannot find an implementation, this is
the library you want to use.

This library (BigDecimal.klib) has the same API as java.math.BigDecimal, all
you need to do is to change:

    import java.math.BigDecimal

to

    import kendy.math.BigDecimal

and add it to the dependencies in your project.

Of course, if you are using [KMM](https://kotlinlang.org/lp/mobile/), you can
create the appropriate
[expect/actual](https://kotlinlang.org/docs/mpp-connect-to-apis.html) declarations,
and use java.math.BigDecimal in the Kotlin/JVM part of the project, and
kendy.math.BigDecimal in the Kotlin/Native part.

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

So far I've tested this only with the iOS Simulator.  To build for that, do:

* Install dependencies for building BoringSSL

  You need [CMake](https://cmake.org/) and [Go](https://golang.org/).

* Build BoringSSL

  See bignum/README.md for details, but mostly down to:

        cd bignum/ios

        git clone git@github.com:google/boringssl.git
        cd boringssl
        mkdir build-x86_64
        cd build-x86_64

        /Applications/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphonesimulator -DCMAKE_OSX_ARCHITECTURES=x86_64 ..
        make -j8

        cd ../../../..

* Build the BigDecimal.klib and libs/BigDecimal-cinterop-boringssl.klib

        ./gradlew compileKotlinIosX64

  The resulting libraries are in:

        build/classes/kotlin/iosX64/main/

* Intercorporate the resulting klibs into your project

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

  If you want to use the Kotlin/Native BigDecimal in a KMM project, you
  probably want to use an expect/actual combination; like:

  commonMain/kotlin/your/project/BigDecimal.kt:

        package your.project
        expect class BigDecimal

  androidMain/kotlin/your/projec/BigDecimal.kt:

        package your.project
        actual typealias BigDecimal = java.math.BigDecimal

  iosMain/kotlin/your/projec/BigDecimal.kt:

        package your.project
        actual typealias BigDecimal = kendy.math.BigDecimal

# Contributing

I don't plan to work too extensively on the code unless if find bugs I need to
fix (as said, the library is mostly a port from the Android Open Source
Project anyway), but still there are many things to improve.

I'll be excited to incorporate your patches if you want to contribute!  Here
are some ideas what to improve if you want to help:

* I am sure there are bugs; I've probably fixed the most obvious ones, but
  I've tested the code only minimally so far.  Any fixes much appreciated!

* The code builds with various warnings - I'll appreciate patches to fix
  those.

* The build of BoringSSL has to be done manually - would be great to extend
  the build.gradle.kts to clone / build it as part of the build just out of
  the box, and for the correct platform.

* Automate the build of the Kotlin/JVM part too - so that it can be used for
  Android development too, without the need for expect/actual.

* Unit tests!  There are no automated tests so far, would be great to have at
  least few as a start...

* And anything else you'd be interested in :-)

For patches, please just do PR's & I'll review them.  For bugs, pleasecreate
GH issues; though no promises when I get to fixing them.
