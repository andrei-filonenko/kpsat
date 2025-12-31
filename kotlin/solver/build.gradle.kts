import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = freeCompilerArgs + "-Xcontext-receivers"
    }
}

dependencies {
    api(project(":dsl"))
    
    // OR-Tools CP-SAT (internal implementation detail)
    implementation("com.google.ortools:ortools-java:9.11.4210")
    
    // Arrow (exposed via dsl module)
    implementation("io.arrow-kt:arrow-core-jvm:1.2.4")
    
    // Jackson for YAML parsing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.18.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.18.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")
    
    // kotlinx.html for type-safe HTML generation
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0")
    
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

// Create fat JAR for notebook usage
tasks.shadowJar {
    archiveBaseName.set("solver-all")
    archiveClassifier.set("")
    mergeServiceFiles()
}

// Integration test source set - tests that require OR-Tools
sourceSets {
    create("testIntegration") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val testIntegrationImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

val testIntegrationRuntimeOnly by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

dependencies {
    testIntegrationImplementation(kotlin("test"))
    testIntegrationImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

// Task to run integration tests
val testIntegration by tasks.registering(Test::class) {
    description = "Runs integration tests that require OR-Tools."
    group = "verification"
    
    testClassesDirs = sourceSets["testIntegration"].output.classesDirs
    classpath = sourceSets["testIntegration"].runtimeClasspath
    
    useJUnitPlatform()
    
    shouldRunAfter(tasks.test)
}

// Include integration tests in check task
tasks.check {
    dependsOn(testIntegration)
}

// Slow test source set - for expensive tests that replicate full notebook models
sourceSets {
    create("testSlow") {
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

val testSlowImplementation by configurations.getting {
    extendsFrom(configurations.implementation.get())
}

val testSlowRuntimeOnly by configurations.getting {
    extendsFrom(configurations.runtimeOnly.get())
}

dependencies {
    testSlowImplementation(kotlin("test"))
    testSlowImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
}

// Task to run slow tests (not part of regular build)
val slowTest by tasks.registering(Test::class) {
    description = "Runs slow tests that replicate full notebook models. Not run by default."
    group = "verification"
    
    testClassesDirs = sourceSets["testSlow"].output.classesDirs
    classpath = sourceSets["testSlow"].runtimeClasspath
    
    useJUnitPlatform()
    
    // Allow longer timeout for slow tests
    systemProperty("junit.jupiter.execution.timeout.default", "10m")
    
    // Large models need more memory
    minHeapSize = "512m"
    maxHeapSize = "4g"
}
