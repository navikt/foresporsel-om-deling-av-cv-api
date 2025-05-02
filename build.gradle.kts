plugins {
    // Bruk samme Kotlin-version som gradlew. gradlew oppdateres med å kjøre denne kommandoen to (2) ganger etterhverandre:
    // ./gradlew wrapper --gradle-version latest --distribution-type all
    kotlin("jvm") version embeddedKotlinVersion
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    id("com.github.ben-manes.versions") version "0.52.0" // Gir kommando (Gradle task) for å se dependecies som kan oppgraderes: ./gradlew dependencyUpdates
    application
}

application {
    mainClass.set("AppKt")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}


repositories {
    mavenCentral()

    maven {
        url = uri("https://packages.confluent.io/maven/")
    }
    maven("https://jitpack.io")
    maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:6.6.0")

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.19.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.0")

    implementation("ch.qos.logback:logback-classic:1.5.18")
    implementation("net.logstash.logback:logstash-logback-encoder:8.1")

    val flywayVersion = "11.8.0"
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("org.postgresql:postgresql:42.7.5")
    implementation("com.zaxxer:HikariCP:6.3.0")

    implementation("no.nav:vault-jdbc:1.3.10")
    implementation("no.nav.security:token-validation-core:3.0.10")

    implementation("org.apache.kafka:kafka-clients:4.0.0")
    implementation("io.confluent:kafka-avro-serializer:7.9.0")
    implementation("org.apache.avro:avro:1.12.0")

    val shedlockVersion = "6.5.0"
    implementation("net.javacrumbs.shedlock:shedlock-core:$shedlockVersion")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc:$shedlockVersion")

    implementation("org.ehcache:ehcache:3.10.8")

    implementation("com.github.navikt:rapids-and-rivers:2025010715371736260653.d465d681c420")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:2025.01.10-08.49-9e6f64ad")

    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("org.assertj:assertj-core:3.27.3")
    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
    testImplementation("org.wiremock:wiremock:3.13.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.4.0")
}

tasks.named("dependencyUpdates", com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask::class) {
    rejectVersionIf {
        val excludedSuffixes = listOf(
            "-Beta1",
            "-Beta2",
            "-M1",
            "-beta.2",
            "-beta.1",
            "-ce",
            "-ccs",
            "-RC",
            "-RC1",
            "-RC2",
            "-alpha1",
            "-alpha01",
            "-alpha02",
            "-alpha03",
            "-alpha04"
        )
        excludedSuffixes.any { candidate.version.endsWith(it) }
    }
}
