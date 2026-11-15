package de.tum.cit.aet.artemis.hyperion.runtime.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationBuildTool;

class AresTestHarnessTest {

    private static Map<String, String> current() {
        return Map.of("build.gradle", "testImplementation 'de.tum.cit.ase:ares:2.2.1'\napply from: 'gradle/AresReservedPackages.gradle'", "gradle/AresReservedPackages.gradle",
                "de/tum/cit/ase/ares/** org/junit/**", "SecurityPolicy.yaml", "trustedPackages: []");
    }

    @Test
    void currentHarnessIsAcceptedWithoutChangingItsContents() {
        assertThat(AresTestHarness.reasons(GenerationBuildTool.GRADLE, current())).isEmpty();
    }

    @Test
    void legacyDependencyCannotSupplyTheCurrentHarness() {
        var files = new HashMap<>(current());
        files.put("build.gradle", "testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'");
        assertThat(AresTestHarness.reasons(GenerationBuildTool.GRADLE, files)).anyMatch(reason -> reason.contains("legacy Ares 1"));
    }

    @Test
    void missingTrustedFilesAreRejectedIndividually() {
        for (String path : List.of("build.gradle", "gradle/AresReservedPackages.gradle", "SecurityPolicy.yaml")) {
            var files = new HashMap<>(current());
            files.remove(path);
            assertThat(AresTestHarness.reasons(GenerationBuildTool.GRADLE, files)).as(path).isNotEmpty();
        }
    }

    @Test
    void commentedDependencyAndGuardCannotSpoofAdmission() {
        var files = new HashMap<>(current());
        files.put("build.gradle", "/* " + files.get("build.gradle") + " */");
        assertThat(AresTestHarness.reasons(GenerationBuildTool.GRADLE, files)).anyMatch(reason -> reason.contains("dependency")).anyMatch(reason -> reason.contains("guard"));
    }

    @Test
    void urlInsideLiteralDoesNotHideFollowingDependency() {
        var files = new HashMap<>(current());
        files.put("build.gradle", "def repositoryUrl = 'https://example.org/repo'\n" + files.get("build.gradle"));
        assertThat(AresTestHarness.reasons(GenerationBuildTool.GRADLE, files)).isEmpty();
    }

    private static Map<String, String> currentMaven(String pomTemplate) throws Exception {
        String pom = new ClassPathResource(pomTemplate).getContentAsString(StandardCharsets.UTF_8);
        return Map.of("pom.xml", pom, "SecurityPolicy.yaml", "trustedPackages: []");
    }

    @Test
    void canonicalMavenHarnessesAreAccepted() throws Exception {
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, currentMaven("templates/java/test/maven/projectTemplate/pom.xml"))).isEmpty();
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, currentMaven("templates/java/maven_maven/test/projectTemplate/pom.xml"))).isEmpty();
    }

    @Test
    void mavenHarnessIsSelectedByBuildToolNotByFilePresence() throws Exception {
        assertThat(AresTestHarness.reasons(GenerationBuildTool.GRADLE, currentMaven("templates/java/test/maven/projectTemplate/pom.xml"))).singleElement().asString()
                .contains("Gradle harness file");
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, current())).singleElement().asString().contains("Maven harness file (pom.xml)");
    }

    @Test
    void mavenHarnessWithoutPolicyIsRejected() throws Exception {
        var files = new HashMap<>(currentMaven("templates/java/test/maven/projectTemplate/pom.xml"));
        files.remove("SecurityPolicy.yaml");
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, files)).singleElement().asString().contains("explicit SecurityPolicy.yaml");
    }

    @Test
    void mavenHarnessWithoutReservedPackageGuardIsRejected() throws Exception {
        var files = new HashMap<>(currentMaven("templates/java/test/maven/projectTemplate/pom.xml"));
        files.put("pom.xml", files.get("pom.xml").replace("verify-ares-reserved-packages-v2", "verify-nothing"));
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, files)).singleElement().asString().contains("reserved-package guard");
    }

    @Test
    void commentedOrLegacyMavenDependencyIsRejected() {
        String guard = "<id>verify-ares-reserved-packages-v2</id><include name=\"de/tum/cit/ase/ares/**\"/><include name=\"org/junit/**\"/>";
        var commented = Map.of("pom.xml", "<!-- <groupId>de.tum.cit.ase</groupId><artifactId>ares</artifactId> -->" + guard, "SecurityPolicy.yaml", "x: y");
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, commented)).singleElement().asString().contains("Ares 2 dependency");
        var legacy = Map.of("pom.xml", "<groupId>de.tum.cit.ase</groupId><artifactId>ares</artifactId><artifactId>artemis-java-test-sandbox</artifactId>" + guard,
                "SecurityPolicy.yaml", "x: y");
        assertThat(AresTestHarness.reasons(GenerationBuildTool.MAVEN, legacy)).singleElement().asString().contains("legacy Ares 1");
    }
}
