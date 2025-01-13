plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    `maven-publish`
}

android {
    namespace = "io.github.cropimage"
    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "io.github.gurinovich89"
            artifactId = "cropimage"
            version = "1.1.1"

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("CropImage")
                description.set("Composable crop image component. Square crop area is fixed, image is zoomable and shiftable.")
                url.set("https://github.com/gurinovich89/cropimage")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }

                developers {
                    developer {
                        id.set("gurinovich89")
                        name.set("Alex")
                    }
                }

                issueManagement {
                    system.set("GitHub")
                    uri("https://github.com/gurinovich89/cropimage/issues")
                }

                scm {
                    connection = "scm:git:git://github.com/gurinovich89/cropimage.git"
                    developerConnection = "scm:git:ssh://github.com/gurinovich89/cropimage.git"
                    url = "https://github.com/gurinovich89/cropimage"
                }
            }
        }
    }

    repositories {
        mavenLocal()

        maven {
            name = "BuildDir"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation.android)
    implementation(libs.androidx.material3.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}