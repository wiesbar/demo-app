import io.gitlab.arturbosch.detekt.Detekt
import java.time.Duration

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jooq:jooq-codegen:3.19.31")
        classpath("org.flywaydb:flyway-core:11.14.1")
        classpath("org.flywaydb:flyway-database-postgresql:11.14.1")
        classpath("org.postgresql:postgresql:42.7.10")
        classpath("org.testcontainers:postgresql:1.21.3")
    }
}

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

val jooqGeneratedDir = layout.buildDirectory.dir("generated-src/jooq")

sourceSets {
    main {
        java.srcDir(jooqGeneratedDir)
    }
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
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation(libs.kotlinLogging)
    implementation(libs.caffeine)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    developmentOnly(platform(libs.spring.boot.bom))
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    integrationTestImplementation(platform(libs.spring.boot.bom))
    integrationTestImplementation(platform(libs.testcontainers.bom))
    integrationTestImplementation(libs.testcontainers.elasticsearch)
    integrationTestImplementation(libs.testcontainers.postgresql)
    integrationTestImplementation("org.springframework.boot:spring-boot-starter-jooq")
    integrationTestImplementation(libs.flyway.core)
    integrationTestRuntimeOnly(libs.flyway.database.postgresql)
    integrationTestRuntimeOnly(libs.postgresql)

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
    // Docker 29+ rejects the docker-java default API version; pin a supported one for Testcontainers.
    systemProperty("api.version", "1.44")
}

tasks.named<Test>("test") {
    timeout.set(Duration.ofMinutes(3))
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
    timeout.set(Duration.ofMinutes(10))
}

tasks.named("check") {
    dependsOn("integrationTest")
}

val jooqCodegen by tasks.registering {
    description = "Generates jOOQ classes from the Flyway migrations against a throwaway Postgres container."
    group = "build"
    val migrationsDir = layout.projectDirectory.dir("src/main/resources/db/migration")
    val outputDir = jooqGeneratedDir
    inputs.dir(migrationsDir)
    outputs.dir(outputDir)
    doLast {
        System.setProperty("api.version", "1.44")
        org.testcontainers.containers.PostgreSQLContainer("postgres:16-alpine").use { container ->
            container.start()
            org.flywaydb.core.Flyway
                .configure()
                .dataSource(container.jdbcUrl, container.username, container.password)
                .locations("filesystem:${migrationsDir.asFile.absolutePath}")
                .load()
                .migrate()
            val configuration =
                org.jooq.meta.jaxb
                    .Configuration()
                    .withJdbc(
                        org.jooq.meta.jaxb
                            .Jdbc()
                            .withDriver("org.postgresql.Driver")
                            .withUrl(container.jdbcUrl)
                            .withUser(container.username)
                            .withPassword(container.password),
                    ).withGenerator(
                        org.jooq.meta.jaxb
                            .Generator()
                            .withDatabase(
                                org.jooq.meta.jaxb
                                    .Database()
                                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                                    .withInputSchema("public")
                                    .withIncludes("otp_entries|otp_rate_limits|flyway_schema_history")
                                    .withExcludes("flyway_schema_history"),
                            ).withTarget(
                                org.jooq.meta.jaxb
                                    .Target()
                                    .withPackageName("example.otp.jooq")
                                    .withDirectory(outputDir.get().asFile.absolutePath),
                            ),
                    )
            org.jooq.codegen.GenerationTool
                .generate(configuration)
        }
    }
}

tasks.named("compileKotlin") {
    dependsOn(jooqCodegen)
}
tasks.named("compileJava") {
    dependsOn(jooqCodegen)
}
tasks.named("compileIntegrationTestKotlin") {
    dependsOn(jooqCodegen)
}

ktlint {
    filter {
        exclude {
            it.file.path
                .replace('\\', '/')
                .contains("/generated-src/jooq/")
        }
    }
}

tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn(jooqCodegen)
}
tasks.named("runKtlintFormatOverMainSourceSet") {
    dependsOn(jooqCodegen)
}

kover {
    reports {
        filters {
            excludes {
                packages("example.otp.jooq")
            }
        }
        verify {
            rule {
                minBound(95)
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.from(files("detekt-config.yml"))
}

tasks.withType<Detekt>().configureEach {
    if (name == "detekt") {
        dependsOn(jooqCodegen)
        setSource(
            files(
                sourceSets["main"].kotlin,
                sourceSets["test"].kotlin,
                sourceSets["integrationTest"].kotlin,
            ),
        )
        exclude {
            it.file.path
                .replace('\\', '/')
                .contains("/generated-src/jooq/")
        }
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
