import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.nexustools.genericui.android"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":common"))
            api(project(":mobile:common"))
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.androidx.appcompat)
            api(libs.androidx.recyclerview)
            api(libs.google.material)
            api(libs.kotlinx.coroutinesAndroid)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
