import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.detekt)
    `maven-publish`
}

@Suppress("UnstableApiUsage")
android {
    compileSdk = libs.versions.compileSdk.get().toInt()
    namespace = "co.pilot.android"

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = libs.versions.jdk.get()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    sourceSets {
        get("main").java.srcDir("src/main/kotlin")
        get("test").java.srcDir("src/test/kotlin")
    }
}

detekt {
    source.setFrom(files(projectDir))
    config.setFrom(files("${rootProject.projectDir}/config/detekt.yml"))
    parallel = true
    autoCorrect = true
    buildUponDefaultConfig = true
}

tasks.withType<Detekt>().configureEach {
    jvmTarget = libs.versions.jdk.get()
    reports {
        html.required.set(true)
        txt.required.set(true)
        xml.required.set(true)
    }
}

dependencies {
    detektPlugins(libs.detektFormatting)

    // Core
    implementation(libs.kotlinCoroutinesCore)
    implementation(libs.kotlinSerializationJson)

    // Networking (for AI backends)
    implementation(libs.okHttp)
    implementation(libs.okHttpInterceptor)

    // Compose UI Test (for ScreenReader + ActionExecutor)
    api(libs.uiTestJunit)
    api(libs.composeUi)

    // JUnit (for TestRule)
    api(libs.junit)

    // Coroutines test (for PilotRouteTest.runYamlRoute)
    api(libs.coroutines.test)

    // AndroidX Test (for InstrumentationRegistry in PilotRouteTest + diagnostics)
    api(libs.androidxTestRunner)

    // YAML parsing
    implementation(libs.snakeyaml)

    // Logging
    implementation(libs.timber)

    // Unit tests
    testImplementation(libs.bundles.unit.testing)
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "co.pilot"
                artifactId = "pilot-android"
                version = "0.2.0"

                pom {
                    name.set("Pilot")
                    description.set("AI-powered natural language integration testing for Android")
                }
            }
        }
    }
}
