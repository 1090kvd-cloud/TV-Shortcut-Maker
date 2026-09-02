plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose) // Kotlin 2.x Compose compiler plugin
}

android {
    namespace = "com.tvshortcut.maker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tvshortcut.maker"
        // API 23 keeps the app installable on older TV boxes.
        // Pinned-shortcut APIs (API 26+) are guarded at runtime.
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // No instrumentation tests shipped, but keep the runner declared.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // apksig relies on java.util.function / java.nio APIs that are missing
        // below API 24; desugaring back-fills them so minSdk 23 keeps working.
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            // Compose for TV is still partially experimental in 1.0.0 — opt in globally
            // so we do not have to annotate every single composable.
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

/**
 * Bundles the compiled :stub APK into the main app's assets as
 * `stub-template.apk`, so ShortcutApkBuilder can use it as a template at runtime.
 *
 * The debug variant of the stub is used on purpose: it is re-signed with our own
 * bundled key before installation anyway, so its original signature is irrelevant.
 */
val stubAssetsDir = layout.buildDirectory.dir("generated/stubAssets")

val copyStubTemplate by tasks.registering(Copy::class) {
    dependsOn(":stub:assembleDebug")
    from(project(":stub").layout.buildDirectory.file("outputs/apk/debug/stub-debug.apk"))
    into(stubAssetsDir.map { it.dir("assets") })
    rename { "stub-template.apk" }
}

android.sourceSets.getByName("main").assets.srcDir(stubAssetsDir.map { it.dir("assets") })

// Make sure the copy happens before assets are merged, for every variant.
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(copyStubTemplate) }

dependencies {
    // Signing of generated shortcut APKs
    implementation(libs.apksig)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose) // collectAsStateWithLifecycle()
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose — the BOM aligns every artifact below to one consistent version
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Compose for TV
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tv.foundation)

    // Colour extraction for generated banners
    implementation(libs.androidx.palette)

    testImplementation(libs.junit)
}
