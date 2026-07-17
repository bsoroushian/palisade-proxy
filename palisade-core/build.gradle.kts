import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// 1. Buildscript (Must be at the very top)
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.mindrot:jbcrypt:0.4")
    }
}

// 2. Plugins Block
plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    application
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
}

// 3. Project Metadata
group = "com.palisade.core"
version = "1.0.0"

// 4. Dependency Versions
val ktorVersion = "3.5.0"
val logbackVersion = "1.5.16"
val lettuceVersion = "7.5.1.RELEASE"
val micrometerVersion = "1.14.4"
val junitVersion = "5.11.0"

// 5. Repositories
repositories {
    mavenCentral()
}

// 6. Dependencies Block
dependencies {
    // Ktor Server Core & Engine
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")

    // Ktor Features & Configuration
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-config-yaml:$ktorVersion")

    // Security & Authentication
    implementation("io.ktor:ktor-server-auth-jvm:$ktorVersion")
    implementation("io.ktor:ktor-network-tls-certificates:$ktorVersion")
    implementation("org.mindrot:jbcrypt:0.4")

    // Serialization & Content Negotiation
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    // Ktor HTTP Client (Proxy Forwarding)
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")

    // Redis (Rate Limiting / State)
    implementation("io.lettuce:lettuce-core:$lettuceVersion")

    // Monitoring & Metrics
    implementation("io.ktor:ktor-server-metrics-micrometer:$ktorVersion")
    implementation("io.micrometer:micrometer-registry-prometheus-simpleclient:$micrometerVersion")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logbackVersion")

    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.ktor:ktor-client-mock:$ktorVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
}

// 7. Application & Toolchain Configuration
application {
    mainClass.set("com.palisade.core.ApplicationKt")
}

kotlin {
    jvmToolchain(25)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_25)
    }
}

// 8. Test Tasks Configuration
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// 9. Custom Utility Tasks
tasks.register("generateAdminHash") {
    group = "security"
    description = "Generates a 12-round BCrypt hash for the superadmin password."

    doLast {
        val rawPassword = project.findProperty("pass")?.toString()

        if (rawPassword.isNullOrBlank()) {
            println("\n[ERROR] No password provided!")
            println("Usage: ./gradlew generateAdminHash -Ppass=\"your_password_here\"")
            throw GradleException("Missing password argument.")
        }

        val salt = org.mindrot.jbcrypt.BCrypt.gensalt(12)
        val computedHash = org.mindrot.jbcrypt.BCrypt.hashpw(rawPassword, salt)

        println("\n==============================================================")
        println("[SUCCESS] BCrypt Hash Generated Successfully (Work Factor: 12)")
        println("==============================================================")
        println("Raw Password:  $rawPassword")
        println("BCrypt Hash:   $computedHash")
        println("==============================================================")
        println("Copy the hash string above and inject it into your CI/CD pipeline")
        println("or Kubernetes Secrets via the PALISADE_SUPERADMIN_PASS_HASH env variable.")
        println("==============================================================\n")
    }
}
