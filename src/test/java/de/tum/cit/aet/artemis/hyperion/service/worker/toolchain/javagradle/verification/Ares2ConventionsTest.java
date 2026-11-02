package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

class Ares2ConventionsTest {

    private static final String TEST_PATH = "test/de/test/StackTest.java";

    private static final String TEST = """
            package de.test;
            import org.junit.jupiter.api.Test;
            import de.tum.cit.ase.ares.api.StrictTimeout;
            import de.tum.cit.ase.ares.api.Policy;
            import de.tum.cit.ase.ares.api.jupiter.Public;
            @Public @Policy(value = "SecurityPolicy.yaml")
            class StackTest {
                @Test @StrictTimeout(1) void pushes() {}
            }
            """;

    private Map<String, String> harness() throws IOException {
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : new String[] { "build.gradle", "gradle/AresReservedPackages.gradle", "SecurityPolicy.yaml" }) {
            files.put(path,
                    new ClassPathResource("templates/java/test/gradle/projectTemplate/" + path).getContentAsString(StandardCharsets.UTF_8).replace("${packageName}", "de.test"));
        }
        return files;
    }

    private Map<String, String> candidate() throws IOException {
        Map<String, String> files = harness();
        String policy = files.get(AresSecurityPolicy.PATH);
        int start = policy.indexOf("  theFollowingClassesAreTestClasses:");
        int end = policy.indexOf("  theFollowingResourceAccessesArePermitted:");
        files.put(AresSecurityPolicy.PATH, policy.substring(0, start) + "  theFollowingClassesAreTestClasses:\n    - \"de.test.StackTest\"\n" + policy.substring(end));
        files.put(TEST_PATH, TEST);
        return files;
    }

    @Test
    void canonicalAres2HarnessAllowsOnlyItsInstructorTestListToChange() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> candidate = candidate();
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(seed, candidate, true)).isEmpty();
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(seed, candidate, false)).isEmpty();
        assertThat(ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons("de.test", seed, Map.of(), Map.of(), candidate, Map.of(), Map.of())).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "theSupervisedCodeUsesTheFollowingPackage: \"de.test\"", "regardingNetworkConnections: [ ]", "regardingCommandExecutions: [ ]" })
    void doesNotAllowTheAgentToChangePermissionsOrSupervisedPackage(String field) throws IOException {
        var files = candidate();
        files.put(AresSecurityPolicy.PATH, files.get(AresSecurityPolicy.PATH).replace(field, field + " # changed"));
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(harness(), files, true)).isNotEmpty();
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(harness(), files, false)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.test.Student", "de.test.StackTest\"\n    - \"de.test.StackTest", "de.test.StackTest\"\n    - \"org.junit.Fake" })
    void doesNotAllowStudentOrInfrastructureExemptions(String declared) throws IOException {
        var files = candidate();
        files.put(AresSecurityPolicy.PATH, files.get(AresSecurityPolicy.PATH).replace("de.test.StackTest", declared));
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(harness(), files, false)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "@Policy", "@Policy(activated = false)", "@Policy(value = \"SecurityPolicy.yaml\", activated = false)", "@Policy(value = \"OtherPolicy.yaml\")" })
    void rejectsDefaultDisabledOrAlternativePolicies(String policy) throws IOException {
        var files = candidate();
        files.put(TEST_PATH, TEST.replace("@Policy(value = \"SecurityPolicy.yaml\")", policy));
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(harness(), files, false)).isNotEmpty();
    }

    @Test
    void rejectsMethodLevelPolicyDeactivation() throws IOException {
        var files = candidate();
        files.put(TEST_PATH, TEST.replace("@Test @StrictTimeout", "@Policy(activated = false) @Test @StrictTimeout"));
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(harness(), files, false)).isNotEmpty();
    }

    @Test
    void rejectsMissingReservedPackageGuard() throws IOException {
        var files = candidate();
        files.remove("gradle/AresReservedPackages.gradle");
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(harness(), files, false)).isNotEmpty();
    }

    @Test
    void rejectsShadowedPolicy() throws IOException {
        var files = candidate();
        files.put(TEST_PATH, TEST + "\n@interface Policy { String value(); }");
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(harness(), files, false)).isNotEmpty();
    }

    @Test
    void registeringTrustedStructuralSourcesChangesOnlyTheTestClassList() throws IOException {
        var files = candidate();
        files.put("test/de/test/ClassTest.java", "package de.test; class ClassTest {}");
        String registered = AresSecurityPolicy.registerTestClasses(files);
        assertThat(registered).contains("de.test.ClassTest", "de.test.StackTest");
        assertThat(AresSecurityPolicy.onlyTestClassesChanged(files.get(AresSecurityPolicy.PATH), registered)).isTrue();
        files.put(AresSecurityPolicy.PATH, registered);
        assertThat(AresSecurityPolicy.reasons(harness(), files)).isEmpty();
    }

}
