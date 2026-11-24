plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.chaquopy)
}

val bridgePythonSrc = layout.buildDirectory.dir("generated/bridge-python")

val prepareBridgePython by tasks.registering(Sync::class) {
    from(rootProject.file("../src/fedleave")) {
        include("__init__.py")
        include("categories.py")
        include("transaction_effects.py")
        include("ledger.py")
        include("payperiods.py")
        include("storage.py")
        include("storage_guard.py")
        include("expirations.py")
        include("cross_year.py")
        include("parental.py")
        include("read_api.py")
        include("rule_defaults.py")
        into("fedleave")
    }
    from(rootProject.file("bridge/src")) {
        include("fedleave_probe.py")
    }
    into(bridgePythonSrc)
}

android {
    namespace = "com.westmouthbay.fedleave"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.westmouthbay.fedleave"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("androidTest") {
            assets.srcDirs(
                rootProject.file("qa/fixtures/calendar-2026"),
                rootProject.file("qa/golden/calendar-2026"),
                rootProject.file("qa/fixtures/storage"),
            )
        }
        getByName("test") {
            // The pinned calendar fixture doubles as the JVM test metadata
            // input for CalendarViewModel tests.
            resources.srcDirs(rootProject.file("qa/fixtures/calendar-2026"))
        }
    }
}

chaquopy {
    defaultConfig {
        version = "3.13"
    }
    sourceSets {
        getByName("main") {
            srcDir(bridgePythonSrc.get().asFile)
        }
    }
}

afterEvaluate {
    tasks.named("preBuild").configure { dependsOn(prepareBridgePython) }
    tasks.matching { it.name.startsWith("merge") && it.name.contains("PythonSources") }.configureEach {
        dependsOn(prepareBridgePython)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}
