// androidApp : l'application Android (docs/spec/distribution.md § Android).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Version : gradle.properties, source unique (la CI de release vérifie le tag).
val kairosVersionName = providers.gradleProperty("kairos.versionName").get()
val kairosVersionCode = providers.gradleProperty("kairos.versionCode").get().toInt()
// Préversion (X.Y.Z-alpha.N / -beta.N) : application distincte « Kairos Preview »,
// installable à côté de la version stable. `-Pkairos.preview=false` force
// l'identifiant définitif : la vérification F-Droid d'une bêta construit
// com.skohscripts.kairos, comme le fera la recette (docs/spec/publication.md).
val isPreview = providers.gradleProperty("kairos.preview").orNull?.toBooleanStrict() ?: kairosVersionName.contains('-')

android {
    namespace = "com.skohscripts.kairos.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.skohscripts.kairos"
        minSdk = 26
        targetSdk = 36
        versionCode = kairosVersionCode
        versionName = kairosVersionName
        if (isPreview) applicationIdSuffix = ".preview"
        resValue("string", "app_name", if (isPreview) "Kairos Preview" else "Kairos")
    }

    // Keystore de release fourni par la CI (toujours la même
    // clé, pour que chaque version s'installe par-dessus la précédente). Sans keystore,
    // assembleRelease produit un APK non signé (build local, F-Droid).
    signingConfigs {
        create("release") {
            System.getenv("KAIROS_KEYSTORE_FILE")?.let { path ->
                storeFile = file(path)
                storePassword = System.getenv("KAIROS_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KAIROS_KEY_ALIAS")
                keyPassword = System.getenv("KAIROS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("KAIROS_KEYSTORE_FILE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        resValues = true
    }

    // F-Droid et builds reproductibles : pas de bloc de dépendances chiffré par
    // Google dans l'APK (illisible par F-Droid).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    packaging {
        resources.excludes += listOf("DebugProbesKt.bin", "META-INF/*.version")
    }
}

dependencies {
    implementation(project(":ui"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.sqldelight.android.driver)
}
