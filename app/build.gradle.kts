plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }
android {
    namespace="com.pratham.thebookapp"
    compileSdk=35
    defaultConfig {
        applicationId="com.pratham.thebookapp"
        minSdk=26
        targetSdk=35
        versionCode=2
        versionName="0.2.0"
    }
    buildFeatures {
        compose=true
        buildConfig=true
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_17
        targetCompatibility=JavaVersion.VERSION_17
    }
    kotlin { jvmToolchain(17) }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
