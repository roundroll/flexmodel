description = "Cucumber integration tests for the Flex model"

dependencies {
    testImplementation(project(":flex"))
    testImplementation(platform("io.cucumber:cucumber-bom:7.34.9"))
    testImplementation("io.cucumber:cucumber-java")
    testImplementation("io.cucumber:cucumber-junit-platform-engine")
    testImplementation("org.junit.platform:junit-platform-suite")
}
