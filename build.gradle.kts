plugins {
    alias(libs.plugins.android.application) apply false
    // Use the version from the version catalog
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.get() apply false
    id("com.google.gms.google-services") version "4.4.2" apply false

}

