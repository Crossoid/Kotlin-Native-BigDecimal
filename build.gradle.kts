import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties

plugins {
    kotlin("multiplatform") version "1.9.20"
    id("com.android.library")
    id("maven-publish")
}

// For publishing; publish with:
// ./gradlew publishAllPublicationsToGitHubPackagesRepository
group = "com.crossoid"
version = "1.0.1"

repositories {
    google()
    mavenCentral()
}

kotlin {
    androidTarget() {
        publishLibraryVariants("release", "debug")
    }

    listOf(
        iosArm64(),
        iosX64(),
        macosArm64(),
        macosX64(),
        linuxX64(),
        linuxArm64(),
        watchosX64(),
        watchosArm32(),
        watchosArm64(),
        tvosX64(),
        tvosArm64()
    ).forEach {
        it.binaries {
            framework {
                baseName = "library"
            }
        }
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
        it.compilations["main"].cinterops {
            it.compilations["main"].cinterops {
                val boringssl by creating {
                    // Def-file describing the native API.
                    defFile(project.file("./bignum/boringssl.def"))
                }
            }
        }
    }

    listOf(
        iosSimulatorArm64(),
        watchosSimulatorArm64(),
        tvosSimulatorArm64()
    ).forEach {
        it.binaries {
            framework {
                baseName = "library"
            }
        }
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
        it.compilations["main"].cinterops {
            it.compilations["main"].cinterops {
                val boringssl by creating {
                    // Def-file describing the native API.
                    defFile(project.file("./bignum/boringssl-simulator.def"))
                }
            }
        }
    }

    sourceSets {
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}

android {
    compileOptions {
        sourceCompatibility(JavaVersion.VERSION_17)
        targetCompatibility(JavaVersion.VERSION_17)
    }
    compileSdk = 34
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
