plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.saiyanhabits"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.saiyanhabits"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "1.0.0"
    }

    signingConfigs {
        // A debug key that lives in the repo, so every build (any machine, any
        // session) is signed the same way and installs over the previous one
        // without wiping the app's data.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// The launcher icon's foreground is assets/art/app_icon.png, like every other
// image. This copies it into a generated resource folder for each build.
abstract class SyncAppIcon : DefaultTask() {
    @get:InputFile
    abstract val icon: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val dir = outputDir.get().asFile.resolve("mipmap-xxxhdpi")
        dir.mkdirs()
        icon.get().asFile.copyTo(dir.resolve("ic_launcher_foreground.png"), overwrite = true)
    }
}

val syncAppIcon = tasks.register<SyncAppIcon>("syncAppIcon") {
    icon.set(layout.projectDirectory.file("src/main/assets/art/app_icon.png"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addGeneratedSourceDirectory(syncAppIcon, SyncAppIcon::outputDir)
    }
}

ksp {
    // Room writes the schema of every database version here. Later milestones
    // need these files to write and test migrations.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
}
