plugins {
    kotlin("jvm") version "2.0.21" apply false
    id("org.jetbrains.dokka") version "1.9.20"
}

allprojects {
    group = "io.github.andreifilonenko"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")

    configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
        jvmToolchain(21)
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    tasks.dokkaHtml {
        outputDirectory.set(file("../../docs/${project.name}"))
    }
}

