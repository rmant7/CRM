plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val testKeystore = rootProject.file("signing/croman-test.keystore")
val releaseKeystorePath = System.getenv("CROMAN_KEYSTORE")
val releaseStorePassword = System.getenv("CROMAN_STORE_PASSWORD")
val releaseKeyPassword = System.getenv("CROMAN_KEY_PASSWORD")
val releaseKeyAlias = System.getenv("CROMAN_KEY_ALIAS")
val hasReleaseSecrets = !releaseKeystorePath.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank()

android {
    namespace = "com.intelliverse.croman"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.intelliverse.croman"
        minSdk = 23
        targetSdk = 36
        versionCode = 12
        versionName = "0.6.0"
        testInstrumentationRunner = "com.intelliverse.croman.CromanDeviceRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("test") {
            storeFile = testKeystore
            storePassword = "croman-test"
            keyAlias = "croman"
            keyPassword = "croman-test"
            enableV1Signing = true
            enableV2Signing = true
        }
        if (hasReleaseSecrets) {
            create("private") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            if (hasReleaseSecrets) {
                signingConfig = signingConfigs.getByName("private")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
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

    lint {
        abortOnError = true
        checkOnly += "NewApi"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
}

tasks.matching {
    it.name == "generateDebugUnitTestAssets" || it.name == "generateDebugAndroidTestAssets"
}.configureEach {
    dependsOn("kspDebugKotlin")
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        // Compose UI tests need a host activity that exists only in the debug manifest.
        variant.enableUnitTest = false
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Debug and release KSP both export the same schema file. Running them together
// truncates that file and Room then fails with "Empty schema file".
tasks.matching { it.name == "kspReleaseKotlin" }.configureEach {
    mustRunAfter(tasks.matching { it.name == "kspDebugKotlin" })
}

val validateSigningCroman = tasks.register("validateSigningCroman") {
    doLast {
        if (!testKeystore.isFile) {
            throw GradleException(
                "CRoMan test keystore is missing at $testKeystore. " +
                    "Refusing to fall back to an auto-generated debug key.",
            )
        }
    }
}
val requireReleaseSecrets = tasks.register("requireReleaseSecrets") {
    doLast {
        if (!hasReleaseSecrets) {
            throw GradleException(
                "Release signing secrets are missing. A Release build will not use the public test key.",
            )
        }
    }
}
tasks.matching { it.name == "assembleDebug" || it.name == "packageDebug" }.configureEach {
    dependsOn(validateSigningCroman)
}
tasks.matching { it.name == "assembleRelease" || it.name == "packageRelease" || it.name == "bundleRelease" }.configureEach {
    dependsOn(requireReleaseSecrets)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.xerial:sqlite-jdbc:3.47.2.0")
    testImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.room:room-testing:2.6.1")

    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
