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
    void theServerDerivedPolicyPassesEveryGate() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> candidate = candidate();
        assertThat(AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), candidate, Map.of())).isEqualTo(candidate.get(AresSecurityPolicy.PATH));
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(seed, candidate, true)).isEmpty();
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(seed, candidate, false)).isEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, candidate, Map.of())).isEmpty();
        assertThat(ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons("de.test", seed, Map.of(), Map.of(), candidate, Map.of(), Map.of())).isEmpty();
    }

    /** Derivation reads only the seed and the test sources, so a policy the agent rewrote, even dropping comments or entries, is replaced by exactly the seeded form. */
    @Test
    void derivationRoundTripsFromTheSeedWhateverTheLivePolicyContains() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> rewritten = candidate();
        rewritten.put(AresSecurityPolicy.PATH, "theFollowingClassesAreTestClasses: [de.test.StackTest]\n");

        String derived = AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), rewritten, Map.of());

        assertThat(derived).isEqualTo(candidate().get(AresSecurityPolicy.PATH)).contains("# Ares 2 security policy", "importTheFollowingPackage: \"org.apache.commons.lang3\"");
        assertThat(AresSecurityPolicy.derive(derived, rewritten, Map.of())).isEqualTo(derived);
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, rewritten, Map.of())).singleElement().asString().contains("differs from the policy Artemis derives",
                "de.test.StackTest");
    }

    private static final Map<String, String> SOLUTION_WITH_STUDENT_TESTS = Map.of("src/de/test/Stack.java", "package de.test;\npublic class Stack {}",
            "src/de/test/StackUsageTest.java", """
                    package de.test;
                    import org.junit.jupiter.api.Test;
                    class StackUsageTest {
                        @Test void pushesOnTop() {}
                    }
                    """);

    /** Ares rejects JUnit imports in supervised code, so an exercise whose reference solution has students write tests needs exactly that one permission. */
    @Test
    void studentWrittenJUnitTestsInTheReferenceSolutionPermitOnlyTheJUnitImport() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> tests = candidate();

        String derived = AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), tests, SOLUTION_WITH_STUDENT_TESTS);

        assertThat(derived).contains(
                "    regardingPackageImports:\n      - importTheFollowingPackage: \"org.junit.jupiter\"\n" + "      - importTheFollowingPackage: \"org.apache.commons.lang3\"\n");
        assertThat(derived.replace("      - importTheFollowingPackage: \"org.junit.jupiter\"\n", "")).isEqualTo(tests.get(AresSecurityPolicy.PATH));
        assertThat(AresSecurityPolicy.derive(derived, tests, SOLUTION_WITH_STUDENT_TESTS)).isEqualTo(derived);
        assertThat(AresSecurityPolicy.registerTestClasses(Map.of(AresSecurityPolicy.PATH, derived, TEST_PATH, TEST))).isEqualTo(derived);

        Map<String, String> produced = new LinkedHashMap<>(tests);
        produced.put(AresSecurityPolicy.PATH, derived);
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, produced, SOLUTION_WITH_STUDENT_TESTS)).isEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, tests, SOLUTION_WITH_STUDENT_TESTS)).singleElement().asString()
                .contains("permits the JUnit import of the student-written tests");
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, produced, Map.of("src/de/test/Stack.java", "package de.test;\npublic class Stack {}")))
                .as("the permission is derived, never accepted without student-written tests").isNotEmpty();
    }

    @Test
    void productionCodeAndTestsOutsideTheStudentSourceTreeDeriveNoPermission() throws IOException {
        String seedPolicy = harness().get(AresSecurityPolicy.PATH);
        Map<String, String> tests = candidate();

        assertThat(AresSecurityPolicy.derive(seedPolicy, tests, Map.of("src/de/test/Stack.java", "package de.test;\nimport org.junit.jupiter.api.Test;\npublic class Stack {}")))
                .isEqualTo(tests.get(AresSecurityPolicy.PATH));
        assertThat(AresSecurityPolicy.derive(seedPolicy, tests, Map.of("test/de/test/StackUsageTest.java", SOLUTION_WITH_STUDENT_TESTS.get("src/de/test/StackUsageTest.java"))))
                .isEqualTo(tests.get(AresSecurityPolicy.PATH));
    }

    @Test
    void anEmptyInlinePackageImportListBecomesABlockListWithTheJUnitPermission() throws IOException {
        String seedPolicy = harness().get(AresSecurityPolicy.PATH).replaceAll("(?m)^    regardingPackageImports:\\n(?:      - [^\\n]*\\n)*", "    regardingPackageImports: [ ]\n");

        String derived = AresSecurityPolicy.derive(seedPolicy, candidate(), SOLUTION_WITH_STUDENT_TESTS);

        assertThat(derived).contains("    regardingPackageImports:\n      - importTheFollowingPackage: \"org.junit.jupiter\"\n    regardingTimeouts: [ ]\n")
                .doesNotContain("regardingPackageImports: [ ]");
    }

    @ParameterizedTest
    @ValueSource(strings = { "theSupervisedCodeUsesTheFollowingPackage: \"de.test\"", "regardingNetworkConnections: [ ]", "regardingCommandExecutions: [ ]",
            "# Docs: https://ls1intum.github.io/Ares2/instructor/policy-reference" })
    void rejectsAnyChangeOutsideTheServerOwnedTestClassList(String field) throws IOException {
        var files = candidate();
        files.put(AresSecurityPolicy.PATH, files.get(AresSecurityPolicy.PATH).replace(field, field + " # changed"));
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(harness(), files, Map.of())).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.test.Student", "de.test.StackTest\"\n    - \"de.test.StackTest", "de.test.StackTest\"\n    - \"org.junit.Fake" })
    void doesNotAllowStudentOrInfrastructureExemptions(String declared) throws IOException {
        var files = candidate();
        files.put(AresSecurityPolicy.PATH, files.get(AresSecurityPolicy.PATH).replace("de.test.StackTest", declared));
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(harness(), files, Map.of())).isNotEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(Map.of(), files, Map.of())).as("without a seed the produced list is still checked").isNotEmpty();
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
        files.put(AresSecurityPolicy.PATH, registered);
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(harness(), files, Map.of())).isEmpty();
    }

}
