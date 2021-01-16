#!/bin/bash

A=/local/android

IN=kendy_math_NativeBN.cpp
OUT=libnativebn.so

clang++ -I/usr/lib64/jvm/java-11-openjdk-11/include \
        -I/usr/lib64/jvm/java-11-openjdk-11/include/linux \
        -I$A/libnativehelper/header_only_include \
        -I./boringssl/include \
        -o "$OUT" -shared -fPIC "$IN" \
        -L./boringssl/build/crypto \
        -L./boringssl/build/ssl \
        -Wl,-Bstatic -lcrypto -lssl -Wl,-Bdynamic
