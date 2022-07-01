val version: String by project

plugins {
    kotlin("multiplatform") version "1.7.0" apply false
    kotlin("jvm") version "1.7.0" apply false
    kotlin("plugin.serialization") version "1.7.0" apply false

    id("com.github.ben-manes.versions") version "0.39.0"
    jacoco
    `maven-publish`
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

    if (!name.endsWith("annotations")) {
        apply(plugin = "java")
        configure<JavaPluginExtension> {
            withJavadocJar()
            withSourcesJar()

            toolchain {
                languageVersion.set(JavaLanguageVersion.of(8))
            }
        }
    }

    apply(plugin = "maven-publish")

    publishing {
        repositories {
            if (System.getenv("CI")?.toBoolean() == true) {
                maven {
                    name = "GithubPackages"
                    url = uri("https://maven.pkg.github.com/dvdandroid/KGraphQL")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR")
                        password = System.getenv("GITHUB_TOKEN")
                    }
                }
            } else {
                mavenLocal()
            }
        }
        if (!name.endsWith("annotations")) {
            publications {
                create<MavenPublication>("mavenJava") {
                    from(components["java"])
                }
            }
        }
    }
}

tasks {
    wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
}
