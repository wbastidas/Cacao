plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "ec.cacaotrace.cacaotrace"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "ec.cacaotrace.app"
        // Android 8.0. Lo pide RNF-13 y ademas es el minimo de tflite_flutter.
        minSdk = 26
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    // La firma de release se lee de android/key.properties, que NO esta en el
    // repositorio. Sin ese archivo se firma con la clave de depuracion, para
    // que "flutter run --release" siga funcionando en desarrollo.
    // Ver docs/COMPILAR.md.
    signingConfigs {
        create("release") {
            val propiedades = java.util.Properties()
            val archivo = rootProject.file("key.properties")
            if (archivo.exists()) {
                archivo.inputStream().use { propiedades.load(it) }
                storeFile = propiedades.getProperty("storeFile")?.let { file(it) }
                storePassword = propiedades.getProperty("storePassword")
                keyAlias = propiedades.getProperty("keyAlias")
                keyPassword = propiedades.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            val hayClave = rootProject.file("key.properties").exists()
            signingConfig = if (hayClave) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

flutter {
    source = "../.."
}
