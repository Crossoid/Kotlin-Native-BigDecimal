import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    kotlin("multiplatform") version "1.8.10"
    id("com.android.library")
    id("maven-publish")
}

// For publishing; publish with:
// ./gradlew publishAllPublicationsToGitHubPackagesRepository
group = "com.crossoid"
version = "1.0"

repositories {
    google()
    mavenCentral()
}

kotlin {
    android() {
        publishLibraryVariants("release", "debug")
    }
    ios {
        binaries {
            framework {
                baseName = "library"
            }
        }
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
        compilations["main"].cinterops {
            compilations["main"].cinterops {
                val boringssl by creating {
                    // Def-file describing the native API.
                    defFile(project.file("./bignum/ios/boringssl.def"))

                    // Package to place the Kotlin API generated.
                    packageName("boringssl")

                    // Options to be passed to compiler by cinterop tool.
                    compilerOpts("-I./bignum/ios/boringssl/include -L./bignum/ios/boringssl/build-arm64/crypto -L./bignum/ios/boringssl/build-arm64/ssl")

                    // Directories for header search (an analogue of the -I<path> compiler option).
                    //includeDirs.allHeaders("path1", "path2")

                    // A shortcut for includeDirs.allHeaders.
                    //includeDirs("include/directory", "another/directory")
                }
            }
        }
    }
    iosSimulatorArm64() {
        binaries {
            framework {
                baseName = "library"
            }
        }
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
        compilations["main"].cinterops {
            compilations["main"].cinterops {
                val boringssl by creating {
                    // Def-file describing the native API.
                    defFile(project.file("./bignum/ios/boringssl-simulator.def"))

                    // Package to place the Kotlin API generated.
                    packageName("boringssl")

                    // Options to be passed to compiler by cinterop tool.
                    compilerOpts("-I./bignum/ios/boringssl/include -L./bignum/ios/boringssl/build-arm64-simulator/crypto -L./bignum/ios/boringssl/build-arm64-simulator/ssl")

                    // Directories for header search (an analogue of the -I<path> compiler option).
                    //includeDirs.allHeaders("path1", "path2")

                    // A shortcut for includeDirs.allHeaders.
                    //includeDirs("include/directory", "another/directory")
                }
            }
        }
    }
    sourceSets {
        val commonMain by getting
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("com.google.android.material:material:1.8.0")
            }
        }
        val iosMain by getting
        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }
        val iosTest by getting
    }
}

android {
    compileSdkVersion(32)
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            setUrl("https://maven.pkg.github.com/crossoid/Kotlin-Native-BigDecimal")
            credentials {
                username = gradleLocalProperties(rootDir).getProperty("github_user")
                password = gradleLocalProperties(rootDir).getProperty("github_token")
            }
        }
    }
}
