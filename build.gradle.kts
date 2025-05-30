plugins {
    // Bruk samme Kotlin-version som gradlew. gradlew oppdateres med å kjøre denne kommandoen to (2) ganger etterhverandre:
    // ./gradlew wrapper --gradle-version latest --distribution-type all
    kotlin("jvm") version embeddedKotlinVersion

    id("com.gradleup.shadow") version "8.3.6"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
    id("com.github.ben-manes.versions") version "0.43.0"
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

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    mergeServiceFiles() // Nødvendig for å få Flyway versjon >= 10 til å funke sammen med shadowJar. Se bug https://github.com/flyway/flyway/issues/3811
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:4.6.7") // Kan ikke oppdateres før https://github.com/wiremock/wiremock/pull/1942 er released

    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.0")

    implementation("ch.qos.logback:logback-classic:1.4.4")
    implementation("net.logstash.logback:logstash-logback-encoder:7.2")

    implementation("org.flywaydb:flyway-core:10.22.0")
    runtimeOnly("org.flywaydb:flyway-database-postgresql:10.22.0")
    implementation("org.postgresql:postgresql:42.5.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("no.nav:vault-jdbc:1.3.10")
    implementation("no.nav.security:token-validation-core:3.0.10")

    implementation("org.apache.kafka:kafka-clients:3.3.1")
    implementation("io.confluent:kafka-avro-serializer:7.3.0")
    implementation("org.apache.avro:avro:1.11.1")

    val shedlockVersion = "4.42.0"
    implementation("net.javacrumbs.shedlock:shedlock-core:$shedlockVersion")
    implementation("net.javacrumbs.shedlock:shedlock-provider-jdbc:$shedlockVersion")

    implementation("org.ehcache:ehcache:3.10.8")

    implementation("com.github.navikt:rapids-and-rivers:2025010715371736260653.d465d681c420")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:2025.01.10-08.49-9e6f64ad")

    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:2.1.214")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("no.nav.security:mock-oauth2-server:0.5.6")
    testImplementation("com.github.tomakehurst:wiremock-jre8:2.35.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.0.0")
}
