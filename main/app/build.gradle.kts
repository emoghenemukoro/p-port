plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.pport.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pport.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // NOTE: buildConfigField third argument MUST be a valid Java literal -> include quotes.
        buildConfigField(
            "String",
            "SUPABASE_ANON_KEY",
            "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJ1YW52ZWJ6cWlsaWpmcm53Y3lkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzc1ODEzMDEsImV4cCI6MjA5MzE1NzMwMX0.utfea2BB-5zc_vP0P4aTfXbVv6wdSRZFZMeuooDL_Wc\""
        )

        // If you also want URL in BuildConfig (optional, but consistent):
        buildConfigField(
            "String",
            "SUPABASE_URL",
            "\"https://buanvebzqilijfrnwcyd.supabase.co\""
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.browser:browser:1.8.0")


    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation(libs.androidx.compose.foundation)
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")

    // Navigation (Compose)
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // DataStore (Preferences)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("io.github.jan-tennert.supabase:postgrest-kt:2.0.0")
    implementation("io.github.jan-tennert.supabase:gotrue-kt:2.0.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("org.osmdroid:osmdroid-android:6.1.16")
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
}
