plugins {
    id("org.jetbrains.kotlin.jvm") version "2.1.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    constraints {
        implementation("org.apache.commons:commons-text:1.13.0")
    }

    implementation("org.apache.commons:commons-text")
    implementation(project(":utilities"))

    testImplementation("org.junit.jupiter:junit-jupiter:5.12.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

application {
    mainClass = "dev.trly.sandbox.AppKt"
}
