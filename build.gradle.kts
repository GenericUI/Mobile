import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

kotlin {
    android {
        namespace = "com.nexustools.genericui.mobile"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        withHostTest {
            isIncludeAndroidResources = true
        }

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(project(":system"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val androidMain by getting {
            dependsOn(commonMain)
            dependencies {
                api(project(":jvm"))
                api(libs.androidx.core.ktx)
                api(libs.androidx.appcompat)
                api(libs.androidx.recyclerview)
                api(libs.google.material)
                api(libs.kotlinx.coroutinesAndroid)
                implementation(libs.androidx.sqlite)
                implementation(libs.androidx.sqlite.ktx)
            }
        }

        val iosMain = maybeCreate("iosMain").apply {
            dependsOn(commonMain)
            dependencies {
                api(project(":apple"))
            }
        }

        val iosArm64Main by getting {
            dependsOn(iosMain)
        }

        val iosSimulatorArm64Main by getting {
            dependsOn(iosMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val androidHostTest by getting {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.kotlin.testJunit)
                implementation("org.robolectric:robolectric:4.16.1")
            }
        }

        val iosTest = maybeCreate("iosTest").apply {
            dependsOn(commonTest)
        }

        val iosArm64Test by getting {
            dependsOn(iosTest)
        }

        val iosSimulatorArm64Test by getting {
            dependsOn(iosTest)
        }
    }
}
