plugins {
    application
    id("com.diffplug.spotless") version "7.2.1"
}

group = "io.github.kristyancarvalho.brmgen"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.7")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.20.0"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.27.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "io.github.kristyancarvalho.brmgen.Brmgen"
    applicationName = "brmgen"
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = application.mainClass.get()
        attributes["Implementation-Version"] = project.version
    }
}

spotless {
    java {
        googleJavaFormat("1.28.0")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint("1.7.1")
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("misc") {
        target("*.md", "*.yml", "*.yaml", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}
