plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "com.sasayaki"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "com.sasayaki"
        minSdk = 28
        targetSdk = 36

        // Releases are tagged v<date>[.<patch>]: v20261105 is 2026110500, v20261105.2 is
        // 2026110502. F-Droid rebuilds from the tag and reads these literals, so they are
        // never computed at build time; scripts/bump-version.sh (make bump) rewrites them.
        versionCode = 2026111700
        versionName = "v20261117"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${rootProject.projectDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        // Debug builds install beside the release app, so they can be tested without replacing it.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        // Release APKs are left unsigned here and signed by scripts/sign-release.sh, because
        // the key rotation proof (signing/lineage) is something AGP cannot apply.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // The dependency metadata block is encrypted for Google Play alone, and F-Droid's scanner
    // rejects APKs that carry it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Lists the app's translations so Android offers Sasayaki in its per-app language setting.
    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Version-bump hints change with every upstream release, so they would make the
        // pre-push gate fail on code that has not changed.
        disable += setOf("NewerVersionAvailable", "GradleDependency", "AndroidGradlePluginVersion")
        // Date-based codes (2026110500) look close to the limit, but leave room until 2099,
        // and installed apps already carry them, so they cannot be lowered.
        disable += "HighAppVersionCode"
    }

    composeCompiler {
        stabilityConfigurationFiles.add(project.layout.projectDirectory.file("compose-stability.conf"))
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    // Robolectric only sees the app's merged assets, so the exported Room schemas ride
    // along in debug builds for the migration tests; release builds never include them.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        // Expressive components are adopted deliberately while material3 1.5 is in beta.
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        )
    }
}

// The benchmark reads its endpoint, key and model list from the environment and skips
// itself when they are absent, so ordinary builds never touch the network. Values are
// forwarded through providers so the configuration cache stays valid.
tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "skipped", "failed")
    }
    listOf("OPENAI_ENDPOINT", "OPENAI_API_KEY", "OPENAI_MODEL", "BENCH_MODELS").forEach { key ->
        val value = providers.environmentVariable(key)
        if (value.isPresent) environment(key, value.get())
    }

    // Environment variables are not task inputs, so a benchmark run with a changed model
    // list would otherwise be skipped as UP-TO-DATE and silently report the previous
    // run's numbers. Declaring them as inputs would hash the API key into Gradle's task
    // history on disk, so force re-execution instead whenever the benchmark is wired up.
    if (providers.environmentVariable("OPENAI_ENDPOINT").isPresent) {
        outputs.upToDateWhen { false }
    }

    // Screenshot tests render through Robolectric and take far longer than the rest of
    // the suite, so they only run when asked for (make screenshots).
    if (providers.gradleProperty("screenshots").isPresent) {
        filter.includeTestsMatching("*ScreenshotTest")
        outputs.upToDateWhen { false }
    } else {
        exclude("**/*ScreenshotTest*")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    debugImplementation(libs.okhttp.logging)

    // DataStore
    implementation(libs.datastore.preferences)
    implementation(libs.androidx.security.crypto)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
