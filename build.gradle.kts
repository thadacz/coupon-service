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
    implementation(libs.spring.boot.starter.restclient)
    implementation(libs.spring.boot.starter.cache)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.flyway.postgresql)
    implementation(libs.caffeine)
    implementation(libs.springdoc.webmvc.ui)
    runtimeOnly(libs.micrometer.prometheus)
    runtimeOnly(libs.postgresql)
    developmentOnly(libs.spring.boot.docker.compose)

    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.boot.starter.data.jpa.test)
    testImplementation(libs.spring.boot.resttestclient)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.wiremock)
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
