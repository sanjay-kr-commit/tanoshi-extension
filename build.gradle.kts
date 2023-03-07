plugins {
    kotlin("jvm") version "1.7.10"
    id("java-gradle-plugin")
    id("maven-publish")
}

group = "tanoshi.extension.default"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenCentral()
    google()
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.sanjay-kr-commit:tanoshi-source-api:0.2")
    implementation("com.squareup.okhttp:okhttp:2.7.5")
    implementation("org.jsoup:jsoup:1.15.3")
    testImplementation(kotlin("test"))
}
