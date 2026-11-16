plugins {
    id("com.android.application")
}

android {
    namespace = "com.dailycode.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dailycode.app"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Signed with the debug key so you can install it directly on your own phone.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
