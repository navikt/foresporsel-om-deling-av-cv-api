plugins {
    kotlin("jvm") version "1.6.21"
    id("com.github.johnrengelman.shadow") version "7.1.0"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.2.0"
    application
}

application {3
    mainClass.set("AppKt")
}

repositories {
    mavenCentral()

    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:4.1.1")

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.0")

    implementation("ch.qos.logback:logback-classic:1.2.7")
    implementation("net.logstash.logback:logstash-logback-encoder:7.0")

    implementation("org.flywaydb:flyway-core:8.0.2")
    implementation("org.postgresql:postgresql:42.3.1")
    implementation("com.zaxxer:HikariCP:5.0.0")
    implementation("no.nav:vault-jdbc:1.3.7")
    implementation("no.nav.security:token-validation-core:1.3.9")

    implementation("org.apache.kafka:kafka-clients:2.8.0")
    implementation("io.confluent:kafka-avro-serializer:6.0.1")
    implementation("org.apache.avro:avro:1.11.0")

    val shedlockVersion = "4.29.0"
    implementation("net.javacrumbs.shedlock:shedlock-core:$shedlockVersion")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc:$shedlockVersion")

    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:1.4.200")
    testImplementation("org.assertj:assertj-core:3.21.0")
    testImplementation("no.nav.security:mock-oauth2-server:0.3.6")
    testImplementation("com.github.tomakehurst:wiremock:2.27.2")
}