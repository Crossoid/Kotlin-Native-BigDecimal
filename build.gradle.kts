import com.android.build.gradle.internal.cxx.configure.gradleLocalProperties
import org.gradle.api.Task
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.util.Properties

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

val boringSslSourceDirectory = layout.projectDirectory.dir("bignum/boringssl")
val boringSslBuildInputs = fileTree(boringSslSourceDirectory) {
    exclude(".git/**", "build/**", "build-*/**", "install/**")
}
val boringsslHeaders = boringSslSourceDirectory.dir("include")

val nativeWrapperDirectory = layout.projectDirectory.dir("bignum/native")
val nativeWrapperSource = nativeWrapperDirectory.file("KBNNative.cpp")
val nativeWrapperHeader = nativeWrapperDirectory.file("KBNNative.h")

/**
 * The Kotlin/Native distribution and its konan.properties, which describe the
 * toolchains (LLVM, sysroots) and minimal OS versions Kotlin/Native uses for
 * each target. The native libraries are built with the same settings, so that
 * they link with the Kotlin code.
 */
val kotlinNativeHost: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        else -> "x64"
    }
    when {
        os.contains("mac") -> "macos_$arch"
        os.contains("linux") -> "linux_$arch"
        else -> "mingw_$arch"
    }
}
val konanDataDirectory: File = file(
    providers.gradleProperty("kotlin.native.konanDataDir").orNull
        ?: System.getenv("KONAN_DATA_DIR")
        ?: "${System.getProperty("user.home")}/.konan"
)
val kotlinNativeHome: File = providers.gradleProperty("kotlin.native.home").orNull?.let(::file)
    ?: konanDataDirectory.resolve(
        "kotlin-native-prebuilt-" +
            kotlinNativeHost.replace("_", "-").replace("arm64", "aarch64").replace("x64", "x86_64") +
            "-" + getKotlinPluginVersion()
    )

val konanProperties: Properties by lazy {
    Properties().apply {
        kotlinNativeHome.resolve("konan/konan.properties").inputStream().use(::load)
    }
}

/** Resolve a konan.properties value, including its $references. */
fun konanProperty(key: String): String {
    val value = konanProperties.getProperty(key) ?: error("$key not found in konan.properties")
    return value.trim().let { if (it.startsWith("$")) konanProperty(it.substring(1)) else it }
}

/**
 * How to build the native libraries for one Kotlin/Native target.
 */
sealed class NativeToolchain {
    /** Apple targets, built with Xcode. */
    data class Apple(
        val sdk: String,
        val cmakeSystemName: String?,
        val architecture: String,
        val os: String,
        val simulator: Boolean
    ) : NativeToolchain()

    /** Linux targets, cross-compiled with the Kotlin/Native toolchain. */
    data class Linux(val triple: String) : NativeToolchain()
}

fun nativeToolchainOf(target: KotlinNativeTarget): NativeToolchain = when (target.konanTarget.name) {
    "ios_arm64" -> NativeToolchain.Apple("iphoneos", "iOS", "arm64", "ios", false)
    "ios_x64" -> NativeToolchain.Apple("iphonesimulator", "iOS", "x86_64", "ios", true)
    "ios_simulator_arm64" -> NativeToolchain.Apple("iphonesimulator", "iOS", "arm64", "ios", true)
    "macos_arm64" -> NativeToolchain.Apple("macosx", null, "arm64", "macos", false)
    "macos_x64" -> NativeToolchain.Apple("macosx", null, "x86_64", "macos", false)
    "watchos_arm32" -> NativeToolchain.Apple("watchos", "watchOS", "armv7k", "watchos", false)
    "watchos_arm64" -> NativeToolchain.Apple("watchos", "watchOS", "arm64_32", "watchos", false)
    "watchos_device_arm64" -> NativeToolchain.Apple("watchos", "watchOS", "arm64", "watchos", false)
    "watchos_x64" -> NativeToolchain.Apple("watchsimulator", "watchOS", "x86_64", "watchos", true)
    "watchos_simulator_arm64" -> NativeToolchain.Apple("watchsimulator", "watchOS", "arm64", "watchos", true)
    "tvos_arm64" -> NativeToolchain.Apple("appletvos", "tvOS", "arm64", "tvos", false)
    "tvos_x64" -> NativeToolchain.Apple("appletvsimulator", "tvOS", "x86_64", "tvos", true)
    "tvos_simulator_arm64" -> NativeToolchain.Apple("appletvsimulator", "tvOS", "arm64", "tvos", true)
    "linux_x64" -> NativeToolchain.Linux("x86_64-unknown-linux-gnu")
    "linux_arm64" -> NativeToolchain.Linux("aarch64-unknown-linux-gnu")
    else -> error("No native toolchain configured for ${target.name}")
}

// Kotlin/Native distribution is downloaded by this task (if missing), the
// konan.properties are only available once it has run.
val kotlinNativeDistribution = tasks.matching { it.name == "downloadKotlinNativeDistribution" }

/**
 * Kotlin/Native downloads the Linux toolchain only once it needs it, so build
 * a trivial program for the target to get it before building BoringSSL.
 */
fun registerKotlinNativeDependenciesTask(
    target: KotlinNativeTarget,
    dependencies: Provider<List<File>>
): TaskProvider<Exec> {
    val capitalizedTargetName = target.name.replaceFirstChar { it.uppercase() }
    val outputDirectory = layout.buildDirectory.dir("native/${target.name}/dependencies")

    return tasks.register<Exec>("download${capitalizedTargetName}KotlinNativeDependencies") {
        dependsOn(kotlinNativeDistribution)
        onlyIf { dependencies.get().any { !it.isDirectory } }

        doFirst {
            outputDirectory.get().asFile.mkdirs()
            outputDirectory.get().file("main.kt").asFile.writeText("fun main() {}\n")
            commandLine(
                kotlinNativeHome.resolve("bin/kotlinc-native").absolutePath,
                "-target", target.konanTarget.name,
                "-o", outputDirectory.get().file("main").asFile.absolutePath,
                outputDirectory.get().file("main.kt").asFile.absolutePath
            )
        }
    }
}

data class LinuxToolchain(
    val llvm: File,
    val gccToolchain: File,
    val triple: String
) {
    val sysroot: File get() = gccToolchain.resolve("$triple/sysroot")
    val flags: List<String>
        get() = listOf(
            "--target=$triple",
            "--gcc-toolchain=${gccToolchain.absolutePath}",
            "--sysroot=${sysroot.absolutePath}"
        )
}

fun linuxToolchainOf(target: KotlinNativeTarget, toolchain: NativeToolchain.Linux): LinuxToolchain {
    val dependencies = konanDataDirectory.resolve("dependencies")
    return LinuxToolchain(
        llvm = dependencies.resolve(konanProperty("llvmHome.$kotlinNativeHost")),
        gccToolchain = dependencies.resolve(konanProperty("gccToolchain.${target.konanTarget.name}")),
        triple = toolchain.triple
    )
}

/**
 * The oldest OS versions that the libc++ of current Xcode versions supports
 * (older ones fail with "The selected platform is no longer supported by
 * libc++"); BoringSSL is C++, so it cannot target anything older.
 */
val minimalAppleOsVersions = mapOf(
    "ios" to "15.0",
    "macos" to "11.0",
    "tvos" to "15.0",
    "watchos" to "8.0"
)

/** The deployment target: the newer of Kotlin/Native's and libc++'s minimum. */
fun appleDeploymentTarget(target: KotlinNativeTarget, toolchain: NativeToolchain.Apple): String {
    val versions = listOf(
        konanProperty("osVersionMin.${target.konanTarget.name}"),
        minimalAppleOsVersions.getValue(toolchain.os)
    )
    return versions.maxWith(compareBy<String>(
        { it.split(".")[0].toInt() },
        { it.split(".").getOrNull(1)?.toInt() ?: 0 }
    ))
}

fun appleTargetTriple(target: KotlinNativeTarget, toolchain: NativeToolchain.Apple): String {
    val minimalVersion = appleDeploymentTarget(target, toolchain)
    val simulator = if (toolchain.simulator) "-simulator" else ""
    return "${toolchain.architecture}-apple-${toolchain.os}$minimalVersion$simulator"
}

data class NativeLibraryTasks(
    val boringSslBuildDirectory: Directory,
    val boringSslBuild: TaskProvider<Exec>,
    val boringSslRebuild: TaskProvider<Task>,
    val wrapperDirectory: Provider<Directory>,
    val wrapperArchive: TaskProvider<Exec>
)

/**
 * The commands to build the native libraries with; resolved lazily, because
 * they depend on konan.properties.
 */
data class NativeCommands(
    val cmakeArguments: List<String>,
    val compile: List<String>,
    val archive: List<String>
)

fun nativeCommandsOf(target: KotlinNativeTarget, toolchain: NativeToolchain): NativeCommands {
    val cmakeArguments = mutableListOf(
        "-DCMAKE_BUILD_TYPE=Release",
        // Only libcrypto is needed, skip BoringSSL's tests and benchmarks.
        "-DBUILD_TESTING=OFF",
        "-DCMAKE_POSITION_INDEPENDENT_CODE=ON",
        "-DCMAKE_C_FLAGS_RELEASE=-O3 -DNDEBUG",
        "-DCMAKE_CXX_FLAGS_RELEASE=-O3 -DNDEBUG",
        "-DCMAKE_ASM_FLAGS_RELEASE=-O3 -DNDEBUG"
    )

    return when (toolchain) {
        is NativeToolchain.Apple -> {
            cmakeArguments += listOfNotNull(
                toolchain.cmakeSystemName?.let { "-DCMAKE_SYSTEM_NAME=$it" },
                "-DCMAKE_OSX_SYSROOT=${toolchain.sdk}",
                "-DCMAKE_OSX_ARCHITECTURES=${toolchain.architecture}",
                "-DCMAKE_OSX_DEPLOYMENT_TARGET=${appleDeploymentTarget(target, toolchain)}",
                "-DCMAKE_MACOSX_BUNDLE=OFF",
                // Recent Xcode versions can still compile, but no longer link
                // armv7k; only compile CMake's checks, the result is a static
                // library anyway.
                "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY"
            )
            NativeCommands(
                cmakeArguments,
                compile = listOf(
                    "xcrun", "--sdk", toolchain.sdk, "clang++",
                    "-target", appleTargetTriple(target, toolchain)
                ),
                archive = listOf("xcrun", "ar", "rcs")
            )
        }

        is NativeToolchain.Linux -> {
            val linux = linuxToolchainOf(target, toolchain)
            val flags = linux.flags.joinToString(" ")
            val llvmAr = linux.llvm.resolve("bin/llvm-ar").absolutePath
            cmakeArguments += listOf(
                "-DCMAKE_SYSTEM_NAME=Linux",
                "-DCMAKE_SYSTEM_PROCESSOR=${toolchain.triple.substringBefore("-")}",
                "-DCMAKE_C_COMPILER=${linux.llvm.resolve("bin/clang").absolutePath}",
                "-DCMAKE_CXX_COMPILER=${linux.llvm.resolve("bin/clang++").absolutePath}",
                "-DCMAKE_C_FLAGS=$flags",
                "-DCMAKE_CXX_FLAGS=$flags",
                "-DCMAKE_ASM_FLAGS=$flags",
                "-DCMAKE_EXE_LINKER_FLAGS=-fuse-ld=lld",
                // llvm-ar already writes the symbol index; the host ranlib
                // (e.g. the macOS one) would break the ELF archive.
                "-DCMAKE_AR=$llvmAr",
                "-DCMAKE_RANLIB=true"
            )
            NativeCommands(
                cmakeArguments,
                compile = listOf(linux.llvm.resolve("bin/clang++").absolutePath) + linux.flags + "-fPIC",
                archive = listOf(llvmAr, "rcs")
            )
        }
    }
}

fun registerNativeLibraryTasks(target: KotlinNativeTarget): NativeLibraryTasks {
    val capitalizedTargetName = target.name.replaceFirstChar { it.uppercase() }
    val toolchain = nativeToolchainOf(target)
    val commands = provider { nativeCommandsOf(target, toolchain) }

    val buildDirectory = boringSslSourceDirectory.dir("build-${target.name}")
    val cryptoLibrary = buildDirectory.file("libcrypto.a")

    val wrapperDirectory = layout.buildDirectory.dir("native/${target.name}")
    val wrapperObject = wrapperDirectory.map { it.file("KBNNative.o") }
    val wrapperArchive = wrapperDirectory.map { it.file("libkbnnative.a") }

    val toolchainDependencies: Any = when (toolchain) {
        is NativeToolchain.Apple -> kotlinNativeDistribution
        is NativeToolchain.Linux -> registerKotlinNativeDependenciesTask(target, provider {
            val linux = linuxToolchainOf(target, toolchain)
            listOf(linux.llvm, linux.gccToolchain)
        })
    }

    val cleanTask = tasks.register<Delete>("clean${capitalizedTargetName}BoringSsl") {
        group = "build"
        delete(buildDirectory)
    }

    val configureTask = tasks.register<Exec>("configure${capitalizedTargetName}BoringSsl") {
        group = "build"
        dependsOn(toolchainDependencies)
        inputs.files(boringSslBuildInputs)
        inputs.property("cmakeArguments", commands.map { it.cmakeArguments })
        outputs.file(buildDirectory.file("CMakeCache.txt"))

        doFirst {
            require(boringSslSourceDirectory.file("CMakeLists.txt").asFile.isFile) {
                "Clone BoringSSL into ${boringSslSourceDirectory.asFile} before building"
            }
            commandLine(
                listOf(
                    "cmake",
                    "-S", boringSslSourceDirectory.asFile.absolutePath,
                    "-B", buildDirectory.asFile.absolutePath
                ) + commands.get().cmakeArguments
            )
        }
    }

    val buildTask = tasks.register<Exec>("build${capitalizedTargetName}BoringSsl") {
        group = "build"
        dependsOn(configureTask)
        inputs.files(boringSslBuildInputs)
        inputs.file(buildDirectory.file("CMakeCache.txt"))
        outputs.file(cryptoLibrary)

        commandLine(
            "cmake",
            "--build", buildDirectory.asFile.absolutePath,
            "--config", "Release",
            "--target", "crypto",
            "--parallel"
        )
    }

    val rebuildTask = tasks.register("rebuild${capitalizedTargetName}BoringSsl") {
        group = "build"
        description = "Cleanly rebuilds the Release BoringSSL library for ${target.name}."
        dependsOn(cleanTask, buildTask)
    }
    configureTask.configure {
        mustRunAfter(cleanTask)
    }

    val compileWrapperTask = tasks.register<Exec>("compile${capitalizedTargetName}BignumWrapper") {
        dependsOn(toolchainDependencies)
        inputs.files(nativeWrapperSource, nativeWrapperHeader)
        inputs.dir(boringsslHeaders)
        inputs.property("compileCommand", commands.map { it.compile })
        outputs.file(wrapperObject)

        doFirst {
            wrapperDirectory.get().asFile.mkdirs()
            commandLine(
                commands.get().compile + listOf(
                    "-std=c++17",
                    "-O3",
                    "-DNDEBUG",
                    "-I${boringsslHeaders.asFile.absolutePath}",
                    "-c", nativeWrapperSource.asFile.absolutePath,
                    "-o", wrapperObject.get().asFile.absolutePath
                )
            )
        }
    }

    val archiveWrapperTask = tasks.register<Exec>("archive${capitalizedTargetName}BignumWrapper") {
        dependsOn(compileWrapperTask)
        inputs.file(wrapperObject)
        outputs.file(wrapperArchive)

        doFirst {
            // ar would otherwise add to an existing archive
            wrapperArchive.get().asFile.delete()
            commandLine(
                commands.get().archive + listOf(
                    wrapperArchive.get().asFile.absolutePath,
                    wrapperObject.get().asFile.absolutePath
                )
            )
        }
    }

    return NativeLibraryTasks(buildDirectory, buildTask, rebuildTask, wrapperDirectory, archiveWrapperTask)
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

    val appleTargets = listOf(
        iosArm64(),
        iosX64(),
        iosSimulatorArm64(),
        macosArm64(),
        macosX64(),
        watchosArm32(),
        watchosArm64(),
        watchosDeviceArm64(),
        watchosX64(),
        watchosSimulatorArm64(),
        tvosArm64(),
        tvosX64(),
        tvosSimulatorArm64(),
    )
    val linuxTargets = listOf(
        linuxX64(),
        linuxArm64(),
    )

    // Xcode 14+ cannot link armv7k anymore, so only the klib is built for
    // watchosArm32 (linking is up to the consumer, with an older Xcode).
    appleTargets.filter { it.name != "watchosArm32" }.forEach {
        it.binaries {
            framework {
                baseName = "library"
            }
        }
    }

    iosSimulatorArm64 {
        binaries {
            test("benchmark", listOf(NativeBuildType.RELEASE))
        }
    }

    val nativeLibraryTasks = (appleTargets + linuxTargets).associateWith { registerNativeLibraryTasks(it) }

    nativeLibraryTasks.forEach { (target, nativeLibraries) ->
        // Build a native interop from the boringssl library; details here:
        // https://kotlinlang.org/docs/mpp-dsl-reference.html#cinterops
        // The boringssl provides the BIGNUM implementation
        target.compilations["main"].cinterops {
            val boringssl by creating {
                // Def-file describing the native API.
                defFile(project.file("bignum/boringssl.def"))

                includeDirs(nativeWrapperDirectory, boringsslHeaders)
                extraOpts(
                    "-libraryPath", nativeLibraries.wrapperDirectory.get().asFile.absolutePath,
                    "-libraryPath", nativeLibraries.boringSslBuildDirectory.asFile.absolutePath
                )

                tasks.named(interopProcessingTaskName) {
                    dependsOn(nativeLibraries.wrapperArchive, nativeLibraries.boringSslBuild)
                    inputs.files(
                        nativeLibraries.wrapperArchive,
                        nativeLibraries.boringSslBuildDirectory.file("libcrypto.a")
                    )
                }
            }
        }
    }

    tasks.register("rebuildBoringSsl") {
        group = "build"
        description = "Cleanly rebuilds the Release BoringSSL libraries for all native targets."
        dependsOn(nativeLibraryTasks.values.map { it.boringSslRebuild })
    }
    tasks.register("rebuildIosBoringSsl") {
        group = "build"
        description = "Cleanly rebuilds the Release BoringSSL libraries for iOS devices and simulators."
        dependsOn(nativeLibraryTasks.filterKeys { it.konanTarget.family.name == "IOS" }.values.map { it.boringSslRebuild })
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
