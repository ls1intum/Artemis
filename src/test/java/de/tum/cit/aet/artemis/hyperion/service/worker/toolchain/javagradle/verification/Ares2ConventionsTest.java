package de.tum.cit.aet.artemis.hyperion.service.worker.toolchain.javagradle.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
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
        assertThat(AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), candidate, Map.of(), null)).isEqualTo(candidate.get(AresSecurityPolicy.PATH));
        assertThat(ExerciseIntegrityGate.harnessTamperingReasons(seed, candidate, true)).isEmpty();
        assertThat(ExerciseIntegrityGate.javaAresConventionReasons(seed, candidate, false)).isEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, candidate, Map.of(), null)).isEmpty();
        assertThat(ExerciseIntegrityGate.javaGeneratedSourceLayoutReasons("de.test", seed, Map.of(), Map.of(), candidate, Map.of(), Map.of())).isEmpty();
    }

    /** Derivation reads only the seed and the test sources, so a policy the agent rewrote, even dropping comments or entries, is replaced by exactly the seeded form. */
    @Test
    void derivationRoundTripsFromTheSeedWhateverTheLivePolicyContains() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> rewritten = candidate();
        rewritten.put(AresSecurityPolicy.PATH, "theFollowingClassesAreTestClasses: [de.test.StackTest]\n");

        String derived = AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), rewritten, Map.of(), null);

        assertThat(derived).isEqualTo(candidate().get(AresSecurityPolicy.PATH)).contains("# Ares 2 security policy", "importTheFollowingPackage: \"org.apache.commons.lang3\"");
        assertThat(AresSecurityPolicy.derive(derived, rewritten, Map.of(), null)).isEqualTo(derived);
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, rewritten, Map.of(), null)).singleElement().asString().contains("differs from the policy Artemis derives",
                "de.test.StackTest");
    }

    @Test
    void aPolicyAddedToASeedWithoutOneIsRejectedWhileAnUnseededSnapshotUsesItsOwn() throws IOException {
        Map<String, String> seedWithoutPolicy = harness();
        seedWithoutPolicy.remove(AresSecurityPolicy.PATH);
        Map<String, String> candidate = candidate();

        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seedWithoutPolicy, candidate, Map.of(), null)).singleElement().asString().contains("has no SecurityPolicy.yaml");
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(Map.of(), candidate, Map.of(), null)).isEmpty();
    }

    private static final Map<String, String> SOLUTION_WITH_STUDENT_TESTS = Map.of("src/de/test/Stack.java", "package de.test;\npublic class Stack {}",
            "src/de/test/StackUsageTest.java", """
                    package de.test;
                    import org.junit.jupiter.api.Test;
                    class StackUsageTest {
                        @Test void pushesOnTop() {}
                    }
                    """);

    private static final String SPEC_ASSIGNING_STUDENT_TESTS = """
            ## Design
            | Type | Role | Template status |
            |---|---|---|
            | Stack | supplied stack | given |
            | StackUsageTest | tests students write | student-creates |
            """;

    /** Test methods the approved contract does not assign to students are no reason to open the JUnit import; they are reported instead. */
    @Test
    void junitTestsTheApprovedSpecificationDoesNotAssignToStudentsDeriveNoPermission() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> tests = candidate();
        String unassigned = SPEC_ASSIGNING_STUDENT_TESTS.replace("| StackUsageTest | tests students write | student-creates |\n", "");

        for (String spec : java.util.Arrays.asList(null, unassigned)) {
            assertThat(AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), tests, SOLUTION_WITH_STUDENT_TESTS, spec)).isEqualTo(tests.get(AresSecurityPolicy.PATH));
            assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, tests, SOLUTION_WITH_STUDENT_TESTS, spec)).singleElement().asString().contains("[StackUsageTest]",
                    "'student-creates' or 'stubbed'", "remove these test methods");
        }
        String stubbed = SPEC_ASSIGNING_STUDENT_TESTS.replace("| StackUsageTest | tests students write | student-creates |",
                "| StackUsageTest | tests students complete | stubbed |");
        assertThat(AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), tests, SOLUTION_WITH_STUDENT_TESTS, stubbed)).contains("org.junit.jupiter");
    }

    @Test
    void adaptationRetainsOnlyTheLearnerTestOwnershipOfTheTrustedSeed() throws IOException {
        Map<String, String> originalHarness = harness();
        Map<String, String> tests = new LinkedHashMap<>(candidate());
        tests.put(AresSecurityPolicy.PATH,
                AresSecurityPolicy.derive(originalHarness.get(AresSecurityPolicy.PATH), tests, SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS));
        Set<String> baseline = AresSecurityPolicy.trustedStudentTests(tests, SOLUTION_WITH_STUDENT_TESTS);

        assertThat(baseline).containsExactly("StackUsageTest");
        assertThat(AresSecurityPolicy.trustedStudentTests(Map.of(AresSecurityPolicy.PATH, tests.get(AresSecurityPolicy.PATH).replace("\n", "\r\n")), SOLUTION_WITH_STUDENT_TESTS))
                .isEqualTo(baseline);
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(tests, tests, SOLUTION_WITH_STUDENT_TESTS, null, baseline)).isEmpty();
        Map<String, String> augmented = new LinkedHashMap<>(SOLUTION_WITH_STUDENT_TESTS);
        augmented.put("src/de/test/UnassignedTest.java", "package de.test; class UnassignedTest { @org.junit.jupiter.api.Test void check() {} }");
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(tests, tests, augmented, null, baseline)).singleElement().asString().contains("UnassignedTest");
        assertThat(AresSecurityPolicy.trustedStudentTests(originalHarness, SOLUTION_WITH_STUDENT_TESTS)).isEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(originalHarness, tests, SOLUTION_WITH_STUDENT_TESTS, null, Set.of())).isNotEmpty();
    }

    /** Ares rejects JUnit imports in supervised code, so an exercise whose reference solution has students write tests needs exactly that one permission. */
    @Test
    void studentWrittenJUnitTestsInTheReferenceSolutionPermitOnlyTheJUnitImport() throws IOException {
        Map<String, String> seed = harness();
        Map<String, String> tests = candidate();

        String derived = AresSecurityPolicy.derive(seed.get(AresSecurityPolicy.PATH), tests, SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS);

        assertThat(derived).contains(
                "    regardingPackageImports:\n      - importTheFollowingPackage: \"org.junit.jupiter\"\n" + "      - importTheFollowingPackage: \"org.apache.commons.lang3\"\n");
        assertThat(derived.replace("      - importTheFollowingPackage: \"org.junit.jupiter\"\n", "")).isEqualTo(tests.get(AresSecurityPolicy.PATH));
        assertThat(AresSecurityPolicy.derive(derived, tests, SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS)).isEqualTo(derived);
        assertThat(AresSecurityPolicy.registerTestClasses(Map.of(AresSecurityPolicy.PATH, derived, TEST_PATH, TEST))).isEqualTo(derived);

        Map<String, String> produced = new LinkedHashMap<>(tests);
        produced.put(AresSecurityPolicy.PATH, derived);
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, produced, SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS)).isEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, tests, SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS)).singleElement().asString()
                .contains("permits the JUnit import of the student-written tests");
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(seed, produced, Map.of("src/de/test/Stack.java", "package de.test;\npublic class Stack {}"),
                SPEC_ASSIGNING_STUDENT_TESTS)).as("the permission is derived, never accepted without student-written tests").isNotEmpty();
    }

    @Test
    void productionCodeAndTestsOutsideTheStudentSourceTreeDeriveNoPermission() throws IOException {
        String seedPolicy = harness().get(AresSecurityPolicy.PATH);
        Map<String, String> tests = candidate();

        assertThat(
                AresSecurityPolicy.derive(seedPolicy, tests, Map.of("src/de/test/Stack.java", "package de.test;\nimport org.junit.jupiter.api.Test;\npublic class Stack {}"), null))
                .isEqualTo(tests.get(AresSecurityPolicy.PATH));
        assertThat(
                AresSecurityPolicy.derive(seedPolicy, tests, Map.of("test/de/test/StackUsageTest.java", SOLUTION_WITH_STUDENT_TESTS.get("src/de/test/StackUsageTest.java")), null))
                .isEqualTo(tests.get(AresSecurityPolicy.PATH));
    }

    @Test
    void anEmptyInlinePackageImportListBecomesABlockListWithTheJUnitPermission() throws IOException {
        String seedPolicy = harness().get(AresSecurityPolicy.PATH).replaceAll("(?m)^    regardingPackageImports:\\n(?:      - [^\\n]*\\n)*", "    regardingPackageImports: [ ]\n");

        String derived = AresSecurityPolicy.derive(seedPolicy, candidate(), SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS);

        assertThat(derived).contains("    regardingPackageImports:\n      - importTheFollowingPackage: \"org.junit.jupiter\"\n    regardingTimeouts: [ ]\n")
                .doesNotContain("regardingPackageImports: [ ]");
    }

    /** An unterminated header followed by a long blank run is scanned once, not once per split of the run around the optional empty list. */
    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aLongBlankRunAfterAnUnterminatedPackageImportHeaderIsScannedInLinearTime() throws IOException {
        String seedPolicy = harness().get(AresSecurityPolicy.PATH) + "    regardingPackageImports:" + " ".repeat(100_000);
        Map<String, String> tests = candidate();

        String derived = AresSecurityPolicy.derive(seedPolicy, tests, SOLUTION_WITH_STUDENT_TESTS, SPEC_ASSIGNING_STUDENT_TESTS);

        assertThat(derived).contains("    regardingPackageImports:\n      - importTheFollowingPackage: \"org.junit.jupiter\"\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "theSupervisedCodeUsesTheFollowingPackage: \"de.test\"", "regardingNetworkConnections: [ ]", "regardingCommandExecutions: [ ]",
            "# Docs: https://ls1intum.github.io/Ares2/instructor/policy-reference" })
    void rejectsAnyChangeOutsideTheServerOwnedTestClassList(String field) throws IOException {
        var files = candidate();
        files.put(AresSecurityPolicy.PATH, files.get(AresSecurityPolicy.PATH).replace(field, field + " # changed"));
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(harness(), files, Map.of(), null)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "de.test.Student", "de.test.StackTest\"\n    - \"de.test.StackTest", "de.test.StackTest\"\n    - \"org.junit.Fake" })
    void doesNotAllowStudentOrInfrastructureExemptions(String declared) throws IOException {
        var files = candidate();
        files.put(AresSecurityPolicy.PATH, files.get(AresSecurityPolicy.PATH).replace("de.test.StackTest", declared));
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(harness(), files, Map.of(), null)).isNotEmpty();
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(Map.of(), files, Map.of(), null)).as("without a seed the produced list is still checked").isNotEmpty();
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
        assertThat(ExerciseIntegrityGate.securityPolicyReasons(harness(), files, Map.of(), null)).isEmpty();
    }

}
