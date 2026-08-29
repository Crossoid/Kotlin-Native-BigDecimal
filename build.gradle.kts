import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
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
version = "1.2.1"

repositories {
    google()
    mavenCentral()
}

val boringSslSourceDirectory = layout.projectDirectory.dir("bignum/ios/boringssl")
val boringSslBuildInputs = fileTree(boringSslSourceDirectory) {
    exclude(".git/**", "build/**", "build-*/**", "install/**")
}

data class BoringSslTasks(
    val build: TaskProvider<Exec>,
    val rebuild: TaskProvider<Task>
)

fun registerBoringSslTasks(
    targetName: String,
    buildDirectoryName: String,
    sdk: String
): BoringSslTasks {
    val capitalizedTargetName = targetName.replaceFirstChar { it.uppercase() }
    val buildDirectory = boringSslSourceDirectory.dir(buildDirectoryName)
    val cryptoLibrary = buildDirectory.file("crypto/libcrypto.a")
    val sslLibrary = buildDirectory.file("ssl/libssl.a")

    val cleanTask = tasks.register<Delete>("clean${capitalizedTargetName}BoringSsl") {
        group = "build"
        delete(buildDirectory)
    }

    val configureTask = tasks.register<Exec>("configure${capitalizedTargetName}BoringSsl") {
        group = "build"
        inputs.files(boringSslBuildInputs)
        inputs.property("sdk", sdk)
        outputs.file(buildDirectory.file("CMakeCache.txt"))

        doFirst {
            require(boringSslSourceDirectory.file("CMakeLists.txt").asFile.isFile) {
                "Clone BoringSSL into ${boringSslSourceDirectory.asFile} before building"
            }
        }

        commandLine(
            "cmake",
            "-S", boringSslSourceDirectory.asFile.absolutePath,
            "-B", buildDirectory.asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
            "-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG",
            "-DCMAKE_CXX_FLAGS_RELEASE=-O3 -DNDEBUG",
            "-DCMAKE_ASM_FLAGS_RELEASE=-O3 -DNDEBUG",
            "-DCMAKE_OSX_SYSROOT=$sdk",
            "-DCMAKE_OSX_ARCHITECTURES=arm64",
            "-DCMAKE_OSX_DEPLOYMENT_TARGET=13.2"
        )
    }

    val buildTask = tasks.register<Exec>("build${capitalizedTargetName}BoringSsl") {
        group = "build"
        dependsOn(configureTask)
        inputs.files(boringSslBuildInputs)
        inputs.file(buildDirectory.file("CMakeCache.txt"))
        outputs.files(cryptoLibrary, sslLibrary)

        commandLine(
            "cmake",
            "--build", buildDirectory.asFile.absolutePath,
            "--config", "Release",
            "--target", "crypto", "ssl",
            "--parallel"
        )
    }

    val rebuildTask = tasks.register("rebuild${capitalizedTargetName}BoringSsl") {
        group = "build"
        description = "Cleanly rebuilds the Release BoringSSL libraries for $targetName."
        dependsOn(cleanTask, buildTask)
    }
    configureTask.configure {
        mustRunAfter(cleanTask)
    }

    return BoringSslTasks(buildTask, rebuildTask)
}

val iosArm64BoringSsl = registerBoringSslTasks("iosArm64", "build-arm64", "iphoneos")
val iosSimulatorArm64BoringSsl = registerBoringSslTasks(
    "iosSimulatorArm64",
    "build-arm64-simulator",
    "iphonesimulator"
)

tasks.register("rebuildIosBoringSsl") {
    group = "build"
    description = "Cleanly rebuilds the Release BoringSSL libraries for iOS devices and simulators."
    dependsOn(iosArm64BoringSsl.rebuild, iosSimulatorArm64BoringSsl.rebuild)
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
    dependsOn(archiveIosArm64BignumWrapper, iosArm64BoringSsl.build)
    inputs.files(
        archiveIosArm64BignumWrapper,
        boringSslSourceDirectory.file("build-arm64/crypto/libcrypto.a"),
        boringSslSourceDirectory.file("build-arm64/ssl/libssl.a")
    )
}
tasks.named("cinteropBoringsslIosSimulatorArm64") {
    dependsOn(archiveIosSimulatorArm64BignumWrapper, iosSimulatorArm64BoringSsl.build)
    inputs.files(
        archiveIosSimulatorArm64BignumWrapper,
        boringSslSourceDirectory.file("build-arm64-simulator/crypto/libcrypto.a"),
        boringSslSourceDirectory.file("build-arm64-simulator/ssl/libssl.a")
    )
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
