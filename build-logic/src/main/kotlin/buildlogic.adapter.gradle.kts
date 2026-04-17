import buildlogic.getVersion
import buildlogic.stringyLibs

plugins {
    `java-library`
    id("buildlogic.common")
    id("buildlogic.common-java")
    id("io.papermc.paperweight.userdev")
}

paperweight {
    injectPaperRepository = false
    reobfArtifactConfiguration = io.papermc.paperweight.userdev.ReobfArtifactConfiguration.REOBF_PRODUCTION
    // Codebook/Mache tooling is not yet consistently compatible with Java 25 across environments.
    // Keep adapter setup on Java 21 while leaving the rest of the build toolchain unchanged.
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    maven {
        name = "PaperMC"
        url = uri("https://repo.papermc.io/repository/maven-public/")
        content {
            excludeModule("io.papermc.paper", "dev-bundle")
            excludeModule("io.papermc.codebook", "codebook-cli")
        }
    }
    maven {
        name = "EngineHub Repository"
        url = uri("https://maven.enginehub.org/repo/")
        content {
            excludeModule("io.papermc.paper", "dev-bundle")
            excludeModule("io.papermc.codebook", "codebook-cli")
        }
    }
    maven {
        name = "IntellectualSites"
        url = uri("https://repo.intellectualsites.dev/repository/paper-dev-bundles/")
        content {
            includeModule("io.papermc.paper", "dev-bundle")
        }
    }
    mavenCentral()
    afterEvaluate {
        killNonEngineHubRepositories()
    }
}

dependencies {
    implementation(project(":worldedit-bukkit"))
    constraints {
        //Reduces the amount of libraries Gradle and IntelliJ need to resolve
        implementation("net.kyori:adventure-bom") {
            version { strictly(stringyLibs.getVersion("adventure").strictVersion) }
            because("Ensure a consistent version of adventure is used.")
        }
    }
}

tasks.named("assemble") {
    dependsOn("reobfJar")
}

tasks.named<Javadoc>("javadoc") {
    enabled = false
}
