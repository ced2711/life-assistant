plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val taskLedgerKeystorePath = providers.gradleProperty("taskledger.keystore.path")
    .orElse(providers.environmentVariable("TASKLEDGER_KEYSTORE_PATH"))
    .orNull
val taskLedgerKeystorePassword = providers.gradleProperty("taskledger.keystore.password")
    .orElse(providers.environmentVariable("TASKLEDGER_KEYSTORE_PASSWORD"))
    .orNull
val taskLedgerKeyAlias = providers.gradleProperty("taskledger.key.alias")
    .orElse(providers.environmentVariable("TASKLEDGER_KEY_ALIAS"))
    .orNull
val taskLedgerKeyPassword = providers.gradleProperty("taskledger.key.password")
    .orElse(providers.environmentVariable("TASKLEDGER_KEY_PASSWORD"))
    .orNull
val taskLedgerReleaseSigningAvailable = listOf(
    taskLedgerKeystorePath,
    taskLedgerKeystorePassword,
    taskLedgerKeyAlias,
    taskLedgerKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.ced2711.lifetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.ced2711.lifetracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "1.8.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Public GitHub App client ID used to prefill GitHub sync; not a secret.
        val gitHubClientId = providers.gradleProperty("lifeassistant.github.clientId").orNull.orEmpty()
        require(gitHubClientId.matches(Regex("[A-Za-z0-9._-]*"))) { "Invalid lifeassistant.github.clientId" }
        buildConfigField("String", "GITHUB_CLIENT_ID", "\"$gitHubClientId\"")
        vectorDrawables.useSupportLibrary = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("standard") {
            dimension = "distribution"
            buildConfigField("boolean", "HAS_INCLUDED_PERSONAL_BACKUP", "false")
        }
        create("personal") {
            dimension = "distribution"
            versionCode = 5
            versionNameSuffix = "-migration"
            buildConfigField("boolean", "HAS_INCLUDED_PERSONAL_BACKUP", "true")
        }
    }

    val taskLedgerReleaseSigning = if (taskLedgerReleaseSigningAvailable) {
        signingConfigs.create("release") {
            storeFile = file(requireNotNull(taskLedgerKeystorePath))
            storePassword = requireNotNull(taskLedgerKeystorePassword)
            keyAlias = requireNotNull(taskLedgerKeyAlias)
            keyPassword = requireNotNull(taskLedgerKeyPassword)
        }
    } else {
        null
    }

    buildTypes {
        release {
            // R8 removes unused code (notably most of the extended icon set) and resources.
            isMinifyEnabled = true
            isShrinkResources = true
            taskLedgerReleaseSigning?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // Built-in Kotlin derives its JVM target from targetCompatibility.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.merges += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("androidTest").assets.directories.add("$projectDir/schemas")
    }
}

// Copies the root legal files into each variant's assets under legal/, where the in-app license
// viewer reads them.
abstract class PrepareLegalAssets : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val legalFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val target = outputDirectory.get().dir("legal").asFile
        target.deleteRecursively()
        target.mkdirs()
        legalFiles.forEach { it.copyTo(target.resolve(it.name)) }
    }
}

androidComponents {
    onVariants { variant ->
        val prepareLegalAssets = tasks.register<PrepareLegalAssets>(
            "prepare${variant.name.replaceFirstChar(Char::uppercase)}LegalAssets",
        ) {
            legalFiles.from(
                rootProject.file("LICENSE"),
                rootProject.file("ADDITIONAL_PERMISSIONS.md"),
                rootProject.file("NOTICE"),
            )
        }
        variant.sources.assets?.addGeneratedSourceDirectory(prepareLegalAssets, PrepareLegalAssets::outputDirectory)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":cloudsync"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.window)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.services.auth)

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.compose.ui.test.junit4)
}

// A personal build without its encrypted migration payload would be misleading. Standard builds,
// including CI, remain fully reproducible without any private user material.
tasks.configureEach {
    if (name == "mergePersonalDebugAssets" || name == "mergePersonalReleaseAssets") {
        doFirst {
            check(file("src/personal/assets/personal-backup.tlb").isFile) {
                "Personal builds require the local encrypted personal-backup.tlb asset."
            }
        }
    }
}
