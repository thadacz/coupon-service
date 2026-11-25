import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

plugins {
    java
    jacoco
    alias(libs.plugins.spring.boot)
}

group = "pl.hadacz"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

// Mockito is attached as a Java agent instead of self-attaching at runtime (JEP 451).
val mockitoAgent: Configuration by configurations.creating

dependencies {
    // Gradle's native platform support instead of the io.spring.dependency-management plugin.
    implementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.spring.boot.bom))
    developmentOnly(platform(libs.spring.boot.bom))
    mockitoAgent(platform(libs.spring.boot.bom))

    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.flyway.postgresql)
    implementation(libs.maxmind.db)
    implementation(libs.springdoc.webmvc.ui)
    runtimeOnly(libs.micrometer.prometheus)
    runtimeOnly(libs.postgresql)
    developmentOnly(libs.spring.boot.docker.compose)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.junit.platform.launcher)

    mockitoAgent(libs.mockito.core) { isTransitive = false }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-parameters"))
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-javaagent:${mockitoAgent.asPath}")
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

// The business rules live in domain and application, so that is where the coverage bar is set.
tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf(
                "pl.hadacz.coupons.coupon.domain",
                "pl.hadacz.coupons.coupon.application",
            )
            limit {
                counter = "LINE"
                minimum = "0.90".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.bootJar {
    archiveFileName = "app.jar"
}

// DB-IP "IP to Country Lite" in MaxMind DB format (CC BY 4.0, https://db-ip.com), the IP-to-country
// database. bootRun and the container image use it; tests use MaxMind's test database instead.
// DB-IP publishes a file per month; early in a month it may not be out yet, hence the fallback.
val geoDatabase = layout.buildDirectory.file("geo/dbip-country-lite.mmdb")
val downloadGeoDatabase by tasks.registering {
    group = "application"
    description = "Downloads DB-IP's IP-to-country database unless it is already there."
    val target = geoDatabase
    outputs.file(target)
    onlyIf { !target.get().asFile.exists() }
    doLast {
        val file = target.get().asFile.toPath()
        Files.createDirectories(file.parent)
        val month = YearMonth.now(ZoneOffset.UTC)
        val failures = mutableListOf<String>()
        for (candidate in listOf(month, month.minusMonths(1))) {
            val source = URI("https://download.db-ip.com/free/dbip-country-lite-$candidate.mmdb.gz")
            val partial = file.resolveSibling("${file.fileName}.part")
            try {
                GZIPInputStream(source.toURL().openStream()).use { Files.copy(it, partial, REPLACE_EXISTING) }
                Files.move(partial, file, REPLACE_EXISTING)
                logger.lifecycle("Downloaded $source")
                return@doLast
            } catch (e: IOException) {
                failures += "$source: $e"
            }
        }
        throw GradleException("Could not download the DB-IP database:\n" + failures.joinToString("\n"))
    }
}

tasks.bootRun {
    dependsOn(downloadGeoDatabase)
}
