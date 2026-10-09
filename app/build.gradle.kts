import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "nl.exitinflex.rittenregistratie"
    compileSdk = 35

    defaultConfig {
        applicationId = "nl.exitinflex.rittenregistratie"
        minSdk = 26
        targetSdk = 35
        // Elke build in GitHub Actions krijgt een hoger nummer, zodat een nieuwe versie
        // altijd als update over de oude heen gaat.
        val bouwnummer = System.getenv("VERSIECODE")?.toIntOrNull() ?: 1
        versionCode = bouwnummer
        versionName = "1.0.$bouwnummer"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("nl")
    }

    // Eén vaste sleutel voor alle builds, zodat de app over een eerdere versie heen
    // te installeren is. In GitHub Actions komt hij uit een geheim; staat hij er
    // niet (bijvoorbeeld bij een eigen build), dan gebruikt Gradle de gewone
    // debug-sleutel van die machine.
    val vasteSleutel = file("rittenregistratie-debug.keystore")
    signingConfigs {
        getByName("debug") {
            if (vasteSleutel.exists()) {
                storeFile = vasteSleutel
                storePassword = System.getenv("SLEUTEL_WACHTWOORD") ?: "rittenregistratie"
                keyAlias = "rittenregistratie"
                keyPassword = System.getenv("SLEUTEL_WACHTWOORD") ?: "rittenregistratie"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            // Geen shrinking: dit is een privé-app zonder distributiedruk, en een
            // kapotte release door een ontbrekende keep-regel kost meer dan de MB's opleveren.
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

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":kern"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.services.location)
    ksp(libs.androidx.room.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
