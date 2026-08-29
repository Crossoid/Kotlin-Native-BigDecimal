import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform") version "2.2.21"
    id("com.android.kotlin.multiplatform.library") version "9.3.1"
    id("maven-publish")
}

// For publishing; publish with:
// ./gradlew publishAllPublicationsToGitHubPackagesRepository
group = "com.crossoid"
version = "1.2.0"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "com.crossoid.bigdecimal"
        compileSdk = 36

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    iosArm64 {
        binaries {
            framework {
                baseName = "library"
            }
        }
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
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
    iosSimulatorArm64 {
        binaries {
            framework {
                baseName = "library"
            }
        }
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
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
    applyDefaultHierarchyTemplate()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            setUrl("https://maven.pkg.github.com/crossoid/Kotlin-Native-BigDecimal")
            credentials {
                username = gradleLocalProperties(rootDir, providers).getProperty("github_user")
                password = gradleLocalProperties(rootDir, providers).getProperty("github_token")
            }
        }
    }
}
