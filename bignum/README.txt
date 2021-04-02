BigDecimal depends on the 'bignum' native implementation from the Google's
'boringssl' package.  The library can be built both for JNI (Java Native
Interface) for Android, and as a static library for iOS.

The JNI approach is only experimental, mostly for testing, because in a real
app, you'll probably prefer the java.math.BigDecimal; most probably only the
iOS port is interesting for you, so follow the 'iOS' parts of the following.

To build the 'nativebn' lib for the Kendy's BigDecimal port:

= Pre-requirements =

* cmake
* go lang

= Build BoringSSL =

Changed dir to 'ios' (or to 'jni' - if you want to build the JNI version).

cd ios

git clone git@github.com:google/boringssl.git
cd boringssl
mkdir build-x86_64
cd build-x86_64

= Configure & build BoringSSL =

* For JNI:

  cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -GNinja ..
  ninja

* For iOS Simulator:

  /Applications/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphonesimulator -DCMAKE_OSX_ARCHITECTURES=x86_64 ..
  make -j8

* For iOS (probably - untested):

  /Applications/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphoneos -DCMAKE_OSX_ARCHITECTURES=x86_64 ..

= Build nativebn - JNI =

git clone https://android.googlesource.com/platform/libnativehelper
git clone https://android.googlesource.com/platform/libcore

# adapt the A path to the dir where the libcore & libnative helper is
# checked out, and run:
./build-nativebn.sh

== Install - JNI ==

sudo cp libnativebn.so /usr/lib64/

== Install - iOS ==

No particular installation is necessary, the 'cinterops' bits from
build.gradle.kts take care of this.  The following is only for the
case you'd like to build the klib by hand.

= Build the native boringssl library - iOS =

# https://kotlinlang.org/docs/native-libraries.html#library-search-sequence
# https://github.com/JetBrains/kotlin-native/issues/2314

cd ../..

~/.konan/kotlin-native-prebuilt-macos-1.4.30-M1/bin/cinterop -def boringssl.def -o boringssl

~/.konan/kotlin-native-prebuilt-macos-1.4.30-M1/bin/klib install boringssl.klib
