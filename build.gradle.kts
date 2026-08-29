import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

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

val nativeWrapperSource = layout.projectDirectory.file("bignum/ios/native/KBNNative.mm")
val nativeWrapperHeader = layout.projectDirectory.file("bignum/ios/native/KBNNative.h")
val boringsslHeaders = layout.projectDirectory.dir("bignum/ios/boringssl/include")

fun registerNativeWrapperTasks(
    targetName: String,
    sdk: String,
    target: String
): TaskProvider<Exec> {
    val capitalizedTargetName = targetName.replaceFirstChar { it.uppercase() }
    val outputDirectory = layout.buildDirectory.dir("native/$targetName")
    val objectFile = outputDirectory.map { it.file("KBNNative.o") }
    val archiveFile = outputDirectory.map { it.file("libkbnnative.a") }

    val compileTask = tasks.register<Exec>("compile${capitalizedTargetName}BignumWrapper") {
        inputs.files(nativeWrapperSource, nativeWrapperHeader)
        inputs.dir(boringsslHeaders)
        outputs.file(objectFile)

        doFirst {
            outputDirectory.get().asFile.mkdirs()
        }

        commandLine(
            "xcrun", "--sdk", sdk, "clang++",
            "-target", target,
            "-std=c++17",
            "-O3",
            "-DNDEBUG",
            "-I${boringsslHeaders.asFile.absolutePath}",
            "-c", nativeWrapperSource.asFile.absolutePath,
            "-o", objectFile.get().asFile.absolutePath
        )
    }

    return tasks.register<Exec>("archive${capitalizedTargetName}BignumWrapper") {
        dependsOn(compileTask)
        inputs.file(objectFile)
        outputs.file(archiveFile)

        commandLine(
            "xcrun", "ar", "rcs",
            archiveFile.get().asFile.absolutePath,
            objectFile.get().asFile.absolutePath
        )
    }
}

val archiveIosArm64BignumWrapper = registerNativeWrapperTasks(
    "iosArm64",
    "iphoneos",
    "arm64-apple-ios13.2"
)
val archiveIosSimulatorArm64BignumWrapper = registerNativeWrapperTasks(
    "iosSimulatorArm64",
    "iphonesimulator",
    "arm64-apple-ios13.2-simulator"
)

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
            test("benchmark", listOf(NativeBuildType.RELEASE))
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

tasks.named("cinteropBoringsslIosArm64") {
    dependsOn(archiveIosArm64BignumWrapper)
    inputs.files(archiveIosArm64BignumWrapper)
}
tasks.named("cinteropBoringsslIosSimulatorArm64") {
    dependsOn(archiveIosSimulatorArm64BignumWrapper)
    inputs.files(archiveIosSimulatorArm64BignumWrapper)
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
