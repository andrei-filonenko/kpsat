import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
}

val arrowVersion = "1.2.4"

dependencies {
    // Arrow for functional programming
    api("io.arrow-kt:arrow-core:$arrowVersion")
    api("io.arrow-kt:arrow-fx-coroutines:$arrowVersion")
    
    // Immutable collections
    api("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.7")
    
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

