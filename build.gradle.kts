import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
}

allprojects {
    group = "com.roundroll.flex"
    version = "1.0-SNAPSHOT"
}

subprojects {
    apply(plugin = "java-library")

    repositories {
        mavenCentral()
    }

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(26)
        }
    }

    dependencies {
        "testImplementation"(platform("org.junit:junit-bom:5.14.4"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core:3.27.7")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
