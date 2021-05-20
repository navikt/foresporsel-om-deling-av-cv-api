plugins {
    kotlin("jvm") version "1.5.0"
    id("com.github.johnrengelman.shadow") version "6.1.0"
    application
}

application {
    mainClassName = "AppKt"
}

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.javalin:javalin:3.13.7")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("net.logstash.logback:logstash-logback-encoder:6.3")

    implementation("org.flywaydb:flyway-core:7.9.1")
    implementation("org.postgresql:postgresql:42.2.20")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("no.nav:vault-jdbc:1.3.7")
    implementation("no.nav.security:token-validation-core:1.3.7")

    testImplementation(kotlin("test"))
    testImplementation("com.h2database:h2:1.4.200")
    testImplementation("org.assertj:assertj-core:3.19.0")
}
