plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Template module for generated shortcuts.
 *
 * Kept as an `application` (not a library) because we need a real, installable
 * APK to use as a template. Its output is copied into the main app's assets by
 * the `copyStubTemplate` task declared in app/build.gradle.kts.
 *
 * Everything here is tuned for size: no Compose, no AndroidX, no resources
 * beyond one banner. The finished template is only a few kilobytes.
 */
android {
    namespace = "com.tvshortcut.stub"
    compileSdk = 35

    defaultConfig {
        // This placeholder ends up as the `package` attribute of the compiled
        // manifest — exactly what ShortcutApkBuilder patches. 32 chars after the
        // "com.tvshortcut.s." prefix; do not change the length.
        applicationId = "com.tvshortcut.s.PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPP"
        minSdk = 21
        // 29 on purpose: package-visibility filtering (API 30+) does not apply,
        // and v1 JAR signing stays valid for installation.
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            // No shrinking: resource names must stay predictable for the patcher.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    // Intentionally empty — the stub uses nothing but the framework.
}
