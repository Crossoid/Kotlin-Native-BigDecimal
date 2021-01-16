To build the 'nativebn' lib to try the Kendy's BigDecimal port:

Build BoringSSL:

git clone git@github.com:google/boringssl.git
cd boringssl
mkdir build
cd build

cmake -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-fPIC -GNinja ..
ninja

Build nativebn:

git clone https://android.googlesource.com/platform/libnativehelper
git clone https://android.googlesource.com/platform/libcore


# adapt the A path to the dir where the libcore & libnative helper is
# checked out, and run:
./build-nativebn.sh

Install:

sudo cp libnativebn.so /usr/lib64/
