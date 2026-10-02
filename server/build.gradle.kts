import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "io.github.chamsser"
version = "0.3.9-dev"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

val externalBuildRoot = providers.environmentVariable("GYMVI_SERVER_BUILD_ROOT")
if (externalBuildRoot.isPresent) {
    layout.buildDirectory = file(externalBuildRoot.get())
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_17
    }
}

tasks.test {
    useJUnitPlatform {
        excludeTags("postgis")
    }
}

tasks.register<Test>("postgisTest") {
    description = "Runs tests that require a real PostgreSQL/PostGIS database."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("postgis")
    }
    shouldRunAfter(tasks.test)
}

tasks.register<JavaExec>("publishFacilities") {
    description = "Atomically publishes a pipeline Facility snapshot to PostGIS."
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "io.github.chamsser.gymvi.publish.FacilityPublishCommandKt"
    doFirst {
        args(
            "--metadata",
            providers.gradleProperty("metadata").get(),
            "--normalized",
            providers.gradleProperty("normalized").get(),
            "--qa-report",
            providers.gradleProperty("qaReport").get(),
        )
    }
}

tasks.register<JavaExec>("publishPrograms") {
    description = "Atomically publishes a joined pipeline program snapshot to PostGIS."
    group = "application"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "io.github.chamsser.gymvi.publish.ProgramPublishCommandKt"
    doFirst {
        args(
            "--metadata",
            providers.gradleProperty("metadata").get(),
            "--normalized",
            providers.gradleProperty("normalized").get(),
            "--qa-report",
            providers.gradleProperty("qaReport").get(),
            "--joins",
            providers.gradleProperty("joins").get(),
            "--join-report",
            providers.gradleProperty("joinReport").get(),
        )
    }
}

springBoot {
    mainClass = "io.github.chamsser.gymvi.GymviServerApplicationKt"
    buildInfo()
}
