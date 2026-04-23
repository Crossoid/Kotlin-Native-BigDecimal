BigDecimal depends on the 'bignum' native implementation from the Google's
BoringSSL package.  The library can be built both for JNI (Java Native
Interface) for Android, and as a static library for Kotlin/Native (iOS, macOS, watchOS, tvOS and Linux).

The JNI approach is only experimental, mostly for testing, because in a real
app, you'll probably prefer the java.math.BigDecimal; most probably only the
iOS port is interesting for you, so follow the Kotlin/Native parts of the following.

To build the 'nativebn' lib for the Kendy's BigDecimal port:

# Pre-requirements

To be able to build BoringSSL, you need to install the following:

* cmake
* go lang

# Build BoringSSL

For Kotlin/Native, clone BoringSSL into 'boringssl' (for JNI, see below):

    git clone https://boringssl.googlesource.com/boringssl

# Configure & build BoringSSL

Gradle builds BoringSSL automatically for every Kotlin/Native target that is
built, into boringssl/build-<target>. Cleanly configure and rebuild optimized
libraries for all the targets with:

    ./gradlew rebuildBoringSsl

The task removes the CMake build directories, configures Release builds with
`-O3 -DNDEBUG` and builds only the `crypto` static library. Use
`rebuildIosBoringSsl` for the iOS targets only, or `rebuild<Target>BoringSsl`
(e.g. `rebuildIosArm64BoringSsl`) for one target.

The Apple targets are built with the active Xcode SDKs. The Linux targets are
cross-compiled with the clang and the sysroot that Kotlin/Native itself uses
for linking (the sysroot has an old glibc, so that the resulting binaries run
on older distributions too; a libcrypto.a built against the glibc of a recent
distribution would fail to link). Gradle downloads that toolchain into
~/.konan/dependencies when it is missing.

The small C++ wrapper in 'native' (KBNNative), which runs some BIGNUM
operations with a reused per-thread BN_CTX, is compiled the same way.

For JNI (only for testing), change dir to 'jni', clone BoringSSL there and:

        mkdir build && cd build
        cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -GNinja ..
        ninja

# Install

You are done, no particular installation is necessary, the 'cinterops' bits
from build.gradle.kts take care of this.

The following is only for the case you'd like to build the klib by hand or if
you wanted to build Java version of the lib (but you don't have to, in the
Kotlin/JVM, you can use the java.math.BigDecimal right away.

## Manual build of the native library - Kotlin/Native

If you are interested in details, check the following:

* https://kotlinlang.org/docs/native-libraries.html#library-search-sequence
* https://github.com/JetBrains/kotlin-native/issues/2314

The actual build:

    ~/.konan/kotlin-native-prebuilt-macos-1.4.30-M1/bin/cinterop -def boringssl.def -o boringssl \
        -target ios_arm64 \
        -compiler-option -Inative -compiler-option -Iboringssl/include \
        -libraryPath ../build/native/iosArm64 \
        -libraryPath boringssl/build-iosArm64

    ~/.konan/kotlin-native-prebuilt-macos-1.4.30-M1/bin/klib install boringssl.klib

## Manual build of the JNI library - only for testing from Java

You need to build the nativebn library:

    git clone https://android.googlesource.com/platform/libnativehelper
    git clone https://android.googlesource.com/platform/libcore

Now adapt the A path to the dir where the libcore & libnative helper is
checked out, and run:

    cd jni
    ./build-nativebn.sh

Then install it somewhere reachable, like:

    sudo cp libnativebn.so /usr/lib64/
