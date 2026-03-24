plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kotlin)
}

android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "co.pilot.sample"

    defaultConfig {
        applicationId = "co.pilot.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = libs.versions.jdk.get()
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    sourceSets {
        get("main").java.srcDir("src/main/kotlin")
        get("androidTest").java.srcDir("src/androidTest/kotlin")
    }
}

dependencies {
    implementation(libs.composeActivity)
    implementation(libs.composeMaterial3)
    implementation(libs.composeUi)
    implementation(libs.navigationCompose)
    implementation(libs.composeUiToolingPreview)
    debugImplementation(libs.composeUiTooling)

    androidTestImplementation(project(":pilot"))
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.uiTestJunit)
    androidTestImplementation(libs.androidxTestRunner)
    androidTestImplementation(libs.androidxTestRules)
    androidTestImplementation(libs.espressoCore)
}
