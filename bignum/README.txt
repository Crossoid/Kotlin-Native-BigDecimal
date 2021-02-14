To build the 'nativebn' lib to try the Kendy's BigDecimal port:

Pre-requirements:

* cmake
* go lang

Build BoringSSL:

git clone git@github.com:google/boringssl.git
cd boringssl
mkdir build
cd build

Configure & build BoringSSL:

* For JNI:

  cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -GNinja ..
  ninja

* For iOS Simulator:

  /Volumes/cmake-3.18.6-Darwin-x86_64/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphonesimulator -DCMAKE_OSX_ARCHITECTURES=x86_64 ..
  make -j8

* For iOS (probably - untested):

  /Volumes/cmake-3.18.6-Darwin-x86_64/CMake.app/Contents/bin/cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -DCMAKE_OSX_SYSROOT=iphoneos -DCMAKE_OSX_ARCHITECTURES=x86_64 ..

Build nativebn:

git clone https://android.googlesource.com/platform/libnativehelper
git clone https://android.googlesource.com/platform/libcore


# adapt the A path to the dir where the libcore & libnative helper is
# checked out, and run:
./build-nativebn.sh

Install:

sudo cp libnativebn.so /usr/lib64/
