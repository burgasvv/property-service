
@file:Suppress("VulnerableLibrariesLocal")

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
}

group = "org.burgas"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(24)
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:1.5.28")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-host-common:3.4.0")
    implementation("io.ktor:ktor-server-status-pages:3.4.0")
    implementation("io.ktor:ktor-server-csrf:3.4.0")
    implementation("io.ktor:ktor-server-cors:3.4.0")
    implementation("io.ktor:ktor-server-sessions:3.4.0")
    implementation("org.postgresql:postgresql:42.7.9")
    implementation("org.jetbrains.exposed:exposed-core:0.61.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.61.0")
    implementation("org.jetbrains.exposed:exposed-dao:0.61.0")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:0.61.0")
    implementation("redis.clients:jedis:7.2.1")
    implementation("org.mindrot:jbcrypt:0.4")
    implementation("io.ktor:ktor-server-auth:3.4.0")
    implementation("io.ktor:ktor-server-auth:3.4.0")
    implementation("io.github.flaxoos:ktor-server-kafka:2.2.1")
    implementation("io.ktor:ktor-client-core:3.4.0")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.3.10")
}
repositories {
    mavenCentral()
    maven {
        url = uri("https://packages.confluent.io/maven")
        name = "confluence"
    }
}