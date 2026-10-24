package de.tum.cit.aet.artemis.hyperion.runtime.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AresTestHarnessTest {

    private static Map<String, String> current() {
        return Map.of("build.gradle", "testImplementation 'de.tum.cit.ase:ares:2.2.1'\napply from: 'gradle/AresReservedPackages.gradle'", "gradle/AresReservedPackages.gradle",
                "de/tum/cit/ase/ares/** org/junit/**", "SecurityPolicy.yaml", "trustedPackages: []");
    }

    @Test
    void currentHarnessIsAcceptedWithoutChangingItsContents() {
        assertThat(AresTestHarness.reasons(current())).isEmpty();
    }

    @Test
    void legacyDependencyCannotSupplyTheCurrentHarness() {
        var files = new HashMap<>(current());
        files.put("build.gradle", "testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0'");
        assertThat(AresTestHarness.reasons(files)).anyMatch(reason -> reason.contains("legacy Ares 1"));
    }

    @Test
    void missingTrustedFilesAreRejectedIndividually() {
        for (String path : AresTestHarness.PATHS) {
            var files = new HashMap<>(current());
            files.remove(path);
            assertThat(AresTestHarness.reasons(files)).as(path).isNotEmpty();
        }
    }

    @Test
    void commentedDependencyAndGuardCannotSpoofAdmission() {
        var files = new HashMap<>(current());
        files.put("build.gradle", "/* " + files.get("build.gradle") + " */");
        assertThat(AresTestHarness.reasons(files)).anyMatch(reason -> reason.contains("dependency")).anyMatch(reason -> reason.contains("guard"));
    }

    @Test
    void urlInsideLiteralDoesNotHideFollowingDependency() {
        var files = new HashMap<>(current());
        files.put("build.gradle", "def repositoryUrl = 'https://example.org/repo'\n" + files.get("build.gradle"));
        assertThat(AresTestHarness.reasons(files)).isEmpty();
    }
}
