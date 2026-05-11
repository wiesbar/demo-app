import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.kover)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

group = "example"
version = "0.0.1-SNAPSHOT"
description = "demo-application"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets.main.get().output + sourceSets.test.get().output
        runtimeClasspath += output + compileClasspath
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-data-elasticsearch")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    integrationTestImplementation(platform(libs.spring.boot.bom))
    integrationTestImplementation(platform(libs.testcontainers.bom))
    integrationTestImplementation(libs.testcontainers.elasticsearch)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.kotest.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.datatest)
    testImplementation(libs.kotest.extensions.spring)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
            "-Xjvm-default=all",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

kotlin.target.compilations {
    val main = getByName("main")
    val test = getByName("test")
    val integrationTest = getByName("integrationTest")
    integrationTest.associateWith(main)
    integrationTest.associateWith(test)
}

tasks.register<Test>("integrationTest") {
    description = "Runs integration tests."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.test)
}

tasks.named("check") {
    dependsOn("integrationTest")
}

kover {
    currentProject {
        sources {
            excludedSourceSets.addAll("integrationTest")
        }
    }
}

tasks.withType<Detekt>().configureEach {
    if (name == "detekt") {
        setSource(
            files(
                sourceSets["main"].kotlin,
                sourceSets["test"].kotlin,
                sourceSets["integrationTest"].kotlin,
            ),
        )
    }
}

configurations.matching { it.name == "detekt" }.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(
                libs.versions.kotlin.detekt.pin
                    .get(),
            )
        }
    }
}
