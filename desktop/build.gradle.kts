import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Properties
import javax.imageio.ImageIO

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin {
            // Shared desktop code, the Windows- and Linux-only parts, and model code shared with Android.
            srcDirs("src/main/kotlin", "windows/src/main/kotlin", "linux/src/main/kotlin", "../android/src/main/java")
            include("com/ced2711/lifetracker/desktop/**")
            include("com/ced2711/lifetracker/domain/model/AppModels.kt")
            include("com/ced2711/lifetracker/domain/model/AppIdentity.kt")
            include("com/ced2711/lifetracker/domain/model/TodoOrganization.kt")
            include("com/ced2711/lifetracker/domain/model/VaultModels.kt")
            include("com/ced2711/lifetracker/domain/model/ConfessionModels.kt")
            include("com/ced2711/lifetracker/domain/date/SmartDateParser.kt")
            include("com/ced2711/lifetracker/domain/recurrence/RecurrenceEngine.kt")
            include("com/ced2711/lifetracker/domain/format/UserFormatting.kt")
            include("com/ced2711/lifetracker/ui/theme/TaskLedgerTheme.kt")
            include("com/ced2711/lifetracker/ui/localization/UiLocalization.kt")
            include("com/ced2711/lifetracker/data/MonotonicTimestamps.kt")
            include("com/ced2711/lifetracker/data/local/Entities.kt")
            include("com/ced2711/lifetracker/data/backup/BackupModels.kt")
            include("com/ced2711/lifetracker/data/backup/BackupCodec.kt")
            include("com/ced2711/lifetracker/data/backup/BackupCrypto.kt")
            include("com/ced2711/lifetracker/data/backup/BackupMerge.kt")
            include("com/ced2711/lifetracker/data/backup/AttachmentStager.kt")
        }
        resources.srcDir(layout.buildDirectory.dir("generated/legal-resources"))
        resources.srcDir(layout.buildDirectory.dir("generated/cloud-defaults"))
    }
}

dependencies {
    implementation(project(":cloudsync"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.common)
    implementation(libs.jna.platform)

    testImplementation(libs.junit)
}

val prepareLegalResources = tasks.register<Sync>("prepareLegalResources") {
    from(rootProject.file("LICENSE"), rootProject.file("ADDITIONAL_PERMISSIONS.md"), rootProject.file("NOTICE"))
    into(layout.buildDirectory.dir("generated/legal-resources/legal"))
}
// Built-in cloud sign-in clients, so people only press "Connect". The values come from the
// developer's personal Gradle properties and never from the repository; a build without them
// falls back to entering the client details in Settings.
val cloudDefaultsDirectory = layout.buildDirectory.dir("generated/cloud-defaults")
val generateCloudDefaults = tasks.register("generateCloudDefaults") {
    val values = mapOf(
        "google.desktop.clientId" to providers.gradleProperty("lifeassistant.google.desktopClientId"),
        "google.desktop.clientSecret" to providers.gradleProperty("lifeassistant.google.desktopClientSecret"),
        "github.clientId" to providers.gradleProperty("lifeassistant.github.clientId"),
    ).mapValues { (_, value) -> value.orElse("") }
    values.forEach { (key, value) -> inputs.property(key, value) }
    val target = cloudDefaultsDirectory.map { it.file("life-assistant-cloud.properties") }
    outputs.file(target)
    doLast {
        val properties = Properties()
        values.forEach { (key, value) -> properties.setProperty(key, value.get().trim()) }
        target.get().asFile.apply { parentFile.mkdirs() }.outputStream().use { properties.store(it, null) }
    }
}
tasks.named("processResources") { dependsOn(prepareLegalResources, generateCloudDefaults) }

val windowsIconFile = layout.buildDirectory.file("generated/icons/life-assistant.ico")
val linuxIconFile = layout.buildDirectory.file("generated/icons/life-assistant.png")
val generateWindowsIcon = tasks.register("generateWindowsIcon") {
    outputs.files(windowsIconFile, linuxIconFile)
    doLast {
        val target = windowsIconFile.get().asFile
        target.parentFile.mkdirs()
        val size = 256
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.color = Color(24, 26, 26)
            graphics.fillRoundRect(8, 8, 240, 240, 58, 58)
            graphics.color = Color.WHITE
            graphics.stroke = BasicStroke(30f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            graphics.draw(
                Path2D.Float().apply {
                    moveTo(65f, 132f)
                    lineTo(111f, 177f)
                    lineTo(193f, 82f)
                },
            )
        } finally {
            graphics.dispose()
        }
        val png = ByteArrayOutputStream().use { bytes ->
            check(ImageIO.write(image, "png", bytes))
            bytes.toByteArray()
        }
        linuxIconFile.get().asFile.writeBytes(png)
        DataOutputStream(target.outputStream().buffered()).use { output ->
            fun writeShortLe(value: Int) {
                output.writeByte(value and 0xff)
                output.writeByte((value ushr 8) and 0xff)
            }
            fun writeIntLe(value: Int) {
                output.writeByte(value and 0xff)
                output.writeByte((value ushr 8) and 0xff)
                output.writeByte((value ushr 16) and 0xff)
                output.writeByte((value ushr 24) and 0xff)
            }
            writeShortLe(0)
            writeShortLe(1)
            writeShortLe(1)
            output.writeByte(0)
            output.writeByte(0)
            output.writeByte(0)
            output.writeByte(0)
            writeShortLe(1)
            writeShortLe(32)
            writeIntLe(png.size)
            writeIntLe(22)
            output.write(png)
        }
        png.fill(0)
    }
}

compose.desktop {
    application {
        mainClass = "com.ced2711.lifetracker.desktop.MainKt"
        buildTypes.release.proguard {
            // Drops unused Compose code, notably most of the extended icon set.
            isEnabled.set(true)
            obfuscate.set(false)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
        nativeDistributions {
            // Each format is built on its own operating system: Windows locally, Linux in GitHub Actions.
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Life Assistant"
            packageVersion = "1.9.0"
            description = "Private life planning, ledger, calendar, notes, and vault"
            vendor = "ced2711"
            copyright = "Copyright 2026 ced2711"
            licenseFile.set(rootProject.file("LICENSE"))
            modules("java.net.http", "jdk.httpserver")
            windows {
                iconFile.set(windowsIconFile)
                menu = true
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // Preserve the default install location and upgrade family of Life Tracker.
                installationPath = "Life Tracker"
                upgradeUuid = "4f5dcd57-85a1-4cb5-9e14-a0d5c8b8940b"
            }
            linux {
                iconFile.set(linuxIconFile)
                packageName = "life-assistant"
                debMaintainer = "ced2711 <ced2711@users.noreply.github.com>"
                menuGroup = "Office"
                appCategory = "Office"
                shortcut = true
            }
        }
    }
}

tasks.matching {
    it.name.startsWith("package") || it.name in setOf("createDistributable", "createReleaseDistributable", "runDistributable")
}.configureEach {
    dependsOn(generateWindowsIcon)
}

tasks.register<JavaExec>("prepareSmokeData") {
    group = "verification"
    description = "Creates isolated encrypted desktop fixture data for packaged-app smoke testing."
    dependsOn(tasks.testClasses)
    mainClass.set("com.ced2711.lifetracker.desktop.SmokeFixtureKt")
    classpath = sourceSets.test.get().runtimeClasspath
    doFirst {
        val output = providers.gradleProperty("smokeDataDir").orNull
            ?: layout.buildDirectory.dir("smoke-appdata").get().asFile.absolutePath
        args(output)
    }
}

tasks.register<JavaExec>("renderScreens") {
    group = "verification"
    description = "Renders every desktop page from prepared smoke data into PNG files for layout review."
    dependsOn(tasks.testClasses)
    mainClass.set("com.ced2711.lifetracker.desktop.DesktopScreenshotsKt")
    classpath = sourceSets.test.get().runtimeClasspath
    doFirst {
        val appData = providers.gradleProperty("smokeDataDir").get()
        environment("APPDATA", appData)
        args(
            providers.gradleProperty("screensDir").orNull ?: layout.buildDirectory.dir("screens").get().asFile.absolutePath,
            providers.gradleProperty("screenWidth").orNull ?: "1600",
            providers.gradleProperty("screenHeight").orNull ?: "1000",
            providers.gradleProperty("screenLanguage").orNull ?: "en",
            providers.gradleProperty("screenTheme").orNull ?: "dark",
        )
    }
}
