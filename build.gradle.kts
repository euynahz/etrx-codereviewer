plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.etrx"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.21.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.21.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.21.0")
    // 生成高质量 unified diff 的可靠库
    implementation("io.github.java-diff-utils:java-diff-utils:4.0")
}

intellij {
    version.set("2024.1")
    type.set("IC")
    plugins.set(listOf("com.intellij.java", "Git4Idea", "org.intellij.plugins.markdown"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        untilBuild.set("252.*")
    }

    // Local compatibility verification using the IntelliJ Plugin Verifier
    // See https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html
    runPluginVerifier {
        // If 'pluginVerifierIdeVersions' is defined in gradle.properties, it will be used automatically.
        // Provide sensible defaults to allow running out-of-the-box.
        ideVersions.set(listOf("IC-2024.1", "IU-2024.1", "IC-2024.3"))
    }

    // The plugin tries to check the latest Gradle IntelliJ Plugin version via network.
    // Disable this task to make builds work in restricted/offline environments.
    named("initializeIntelliJPlugin") {
        this.enabled = false
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    test {
        enabled = false
        // 如需恢复测试，删除本行并使用: useJUnitPlatform()
        // useJUnitPlatform()
    }
}