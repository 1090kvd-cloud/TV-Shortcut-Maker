// Top-level (project-level) build file.
// Plugins are declared here with `apply false` so that the version is defined in
// exactly one place (the version catalog) and applied inside the module scripts.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
