import de.marcphilipp.gradle.nexus.NexusPublishPlugin
import java.time.Duration

val version: String by project

plugins {
    id("com.github.ben-manes.versions") version "0.39.0"
    id("de.marcphilipp.nexus-publish") version "0.4.0"
    jacoco
}

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
    }
}

subprojects {
    group = "com.apurebase"
    version = version

    apply<NexusPublishPlugin>()

    nexusPublishing {
        repositories {
            sonatype()
        }
        clientTimeout.set(Duration.parse("PT10M")) // 10 minutes
    }
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
}
