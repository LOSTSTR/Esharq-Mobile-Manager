import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    publishing
}

android {
    namespace = "app.revenge.manager"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.esharq.mobile.manager"
        minSdk = 28
        targetSdk = 34
        versionName = version.toString()
        versionCode = versionName!!.removePrefix("v").split("-").first().replace(".", "").toInt()


        buildConfigField("String", "MOD_NAME", "\"Esharq\"")
        buildConfigField("String", "MANAGER_NAME", "\"EsharqMobile\"")
        // The commit feed on the home screen reads this over the public GitHub API with no token.
        // Pointing it at the bundle repo put a "Failed to load commits" box on the first screen
        // every user sees, because that repo is private and the API answers 404 — the bundle is
        // served to members, not published, and that is deliberate. The installer's own repo is
        // public, and its changes are the ones a user would act on by reinstalling.
        buildConfigField("String", "REPO", "\"LOSTSTR/Esharq-Mobile-Manager\"")
        buildConfigField("String", "ORG_LINK", "\"https://esharq.org\"")
        buildConfigField("String", "INVITE_LINK", "\"https://discord.gg/QamdqDNEDa\"")
        buildConfigField("String", "MODDED_APP_PACKAGE_NAME", "\"org.esharq.mobile\"")
        // Where the Esharq loader is published. The installer embeds this module into Discord, and
        // it is the piece that knows how to authenticate — upstream's would ignore the receipt and
        // carry the bundled copy of the mod this fork exists to remove.
        buildConfigField(
            "String",
            "LOADER_URL",
            "\"https://github.com/LOSTSTR/Esharq-Mobile-Loader/releases/latest/download/app-release.apk\""
        )
        // Esharq gold, the same value the site and the badge embeds use, so the patched icon on the
        // home screen matches everything else that carries the name.
        buildConfigField("int", "MODDED_APP_ICON", "0xFFE3C25A")
        buildConfigField("int", "MODDED_APP_ICON_ALPHA", "0xFFF0D98A")
        buildConfigField("int", "MODDED_APP_ICON_OTHER", "0xFFB8912F")

        buildConfigField("String", "GIT_BRANCH", "\"${getCurrentBranch()}\"")
        buildConfigField("String", "GIT_COMMIT", "\"${getLatestCommit()}\"")
        buildConfigField("boolean", "GIT_LOCAL_COMMITS", "${hasLocalCommits()}")
        buildConfigField("boolean", "GIT_LOCAL_CHANGES", "${hasLocalChanges()}")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isCrunchPngs = true
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            val keystoreFile = file("keystore.jks")
            signingConfig = if (keystoreFile.exists()) {
                 signingConfigs.create("release") {
                    storeFile = keystoreFile
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEYSTORE_ENTRY_ALIAS")
                    keyPassword = System.getenv("KEYSTORE_ENTRY_PASSWORD")
                }
            } else signingConfigs["debug"]
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
        freeCompilerArgs += listOf(
            "-Xcontext-receivers",
            "-P",
            "plugin:androidx.compose.compiler.plugins.kotlin:reportsDestination=${buildDir.resolve("report").absolutePath}",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.6"
    }

    applicationVariants.all {
        outputs.all {
            this as com.android.build.gradle.internal.api.ApkVariantOutputImpl

            outputFileName = "${rootProject.name}-$version.apk"
        }
    }

    androidComponents {
        onVariants(selector().withBuildType("release")) {
            it.packaging.resources.excludes.apply {
                // Debug metadata
                add("/**/*.version")
                add("/kotlin-tooling-metadata.json")
                // Kotlin debugging (https://github.com/Kotlin/kotlinx.coroutines/issues/2274)
                add("/DebugProbesKt.bin")
            }
        }
    }

    packaging {
        resources {
            // Reflection symbol list (https://stackoverflow.com/a/41073782/13964629)
            excludes += "/**/*.kotlin_builtins"
        }
    }

    configurations {
        all {
            exclude(module = "listenablefuture")
            exclude(module = "error_prone_annotations")
        }
    }
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.bundles.accompanist)
    implementation(libs.bundles.androidx)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.compose)
    implementation(libs.bundles.koin)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.shizuku)
    implementation(libs.bundles.voyager)

    implementation(files("libs/lspatch.aar"))

    implementation(libs.aboutlibraries.core)
    implementation(libs.binaryResources) {
        exclude(module = "checker-qual")
        exclude(module = "jsr305")
        exclude(module = "guava")
    }
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.collections)
    implementation(libs.zip.android) {
        artifact {
            type = "aar"
        }
    }
}

fun getCurrentBranch(): String? =
    exec("git", "symbolic-ref", "--short", "HEAD")

fun getLatestCommit(): String? =
    exec("git", "rev-parse", "--short", "HEAD")

fun hasLocalCommits(): Boolean {
    val branch = getCurrentBranch() ?: return false
    return exec("git", "log", "origin/$branch..HEAD")?.isNotBlank() ?: false
}

fun hasLocalChanges(): Boolean =
    exec("git", "status", "-s")?.isNotEmpty() ?: false

fun exec(vararg command: String): String? {
    return try {
        val stdout = ByteArrayOutputStream()
        val errout = ByteArrayOutputStream()

        exec {
            commandLine = command.toList()
            standardOutput = stdout
            errorOutput = errout
            isIgnoreExitValue = true
        }

        if (errout.size() > 0)
            throw Error(errout.toString(Charsets.UTF_8))

        stdout.toString(Charsets.UTF_8).trim()
    } catch (e: Throwable) {
        e.printStackTrace()
        null
    }
}

// Used by gradle-semantic-release-plugin.
// Tracking: https://github.com/KengoTODA/gradle-semantic-release-plugin/issues/435.
tasks.publish {
    dependsOn("assembleRelease")
}

