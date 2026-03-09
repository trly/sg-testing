plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.httpcomponents.client5:httpclient5:5.4.4")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

application {
    mainClass = "com.example.sgtesting.App"
}
