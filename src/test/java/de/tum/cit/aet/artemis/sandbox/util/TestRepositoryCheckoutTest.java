package de.tum.cit.aet.artemis.sandbox.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.sandbox.domain.AresApiUse;
import de.tum.cit.aet.artemis.sandbox.domain.BuildFiles;
import de.tum.cit.aet.artemis.sandbox.domain.BuildTool;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileContent;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxState;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReason;
import de.tum.cit.aet.artemis.sandbox.domain.UnsupportedReasonType;

/**
 * Tests how {@link TestRepositoryCheckout} reads and rewrites the Ares setup in a checkout of an exercise-tests repository.
 */
class TestRepositoryCheckoutTest {

    private static final String TEST_CLASS = """
            package %s;

            import org.junit.jupiter.api.Test;

            import de.tum.cit.ase.ares.api.jupiter.Public;

            @Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class %s {

                @Test
                void runs() {
                }
            }
            """;

    private static final String POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
              theSupervisedCodeUsesTheFollowingPackage: "de.example"
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
                regardingNetworkConnections: [ ]
                regardingCommandExecutions: [ ]
                regardingThreadCreations: [ ]
                regardingPackageImports: [ ]
                regardingTimeouts: [ ]
            """;

    @TempDir
    private Path root;

    private void writeBuildFile() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
    }

    private void writeSource(String relativePath, String source) throws IOException {
        FileUtils.writeStringToFile(root.resolve(relativePath).toFile(), source, StandardCharsets.UTF_8);
    }

    private void writeTestClass(String relativePath, String packageName, String className) throws IOException {
        FileUtils.writeStringToFile(root.resolve(relativePath).toFile(), TEST_CLASS.formatted(packageName, className), StandardCharsets.UTF_8);
    }

    private void assertUnsupported(UnsupportedReasonType type, String location) {
        SandboxState state = TestRepositoryCheckout.evaluate(root);
        assertThat(state.status()).isEqualTo(SandboxStatus.UNSUPPORTED);
        assertThat(state.unsupportedReasons()).contains(new UnsupportedReason(location, type));
    }

    private PolicyFileProblem onlyPolicyFileProblem() {
        return TestRepositoryCheckout.evaluate(root).policyFiles().getFirst().problem();
    }

    private static SandboxState evaluate(Map<String, String> sources, BuildFiles buildFiles) {
        return TestRepositoryFiles.evaluate(sources, _ -> PolicyFileContent.of(POLICY), buildFiles);
    }

    @Test
    void reportsThePartsOfAresTheTestsUseBesidesTheSandbox() throws IOException {
        writeBuildFile();
        writeSource(TestRepositoryFiles.POLICY_FILE_NAME, POLICY);
        writeSource("test/de/example/SortingTest.java", """
                package de.example;

                import static de.tum.cit.ase.ares.api.util.ReflectionTestUtils.*;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.StrictTimeout;
                import de.tum.cit.ase.ares.api.jupiter.*;
                import de.tum.cit.ase.ares.api.structural.*;

                // de.tum.cit.ase.ares.api.InAComment is no use
                @Public
                @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
                @StrictTimeout(5)
                class SortingTest {

                    private static final String TEXT = "de.tum.cit.ase.ares.api.InAString";

                    @Test
                    void runs() {
                        de.tum.cit.ase.ares.api.structural.ClassTestProvider provider = null;
                    }
                }
                """);
        writeTestClass("test/de/example/OnlySandboxTest.java", "de.example", "OnlySandboxTest");

        assertThat(TestRepositoryCheckout.evaluate(root).aresApiUses()).containsExactly(new AresApiUse("test/de/example/SortingTest.java", "StrictTimeout"),
                new AresApiUse("test/de/example/SortingTest.java", "structural.*"), new AresApiUse("test/de/example/SortingTest.java", "structural.ClassTestProvider"),
                new AresApiUse("test/de/example/SortingTest.java", "util.ReflectionTestUtils"));
    }

    @Test
    void scansSourcePackagesNamedLikeBuildOutput() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
        writeTestClass("test/de/example/build/BuildTest.java", "de.example.build", "BuildTest");
        writeTestClass("test/de/example/target/TargetTest.java", "de.example.target", "TargetTest");

        assertThat(TestRepositoryCheckout.findTestClasses(root)).containsExactly("de.example.build.BuildTest", "de.example.target.TargetTest");
        assertThat(TestRepositoryCheckout.setPolicyAnnotationsActive(root, false)).isEqualTo(2);
        assertThat(Files.readString(root.resolve("test/de/example/build/BuildTest.java"))).contains(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
    }

    @Test
    void skipsBuildOutputOfTheProjectAndItsModules() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(root.resolve("module/pom.xml").toFile(), "<project/>", StandardCharsets.UTF_8);
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeTestClass("build/generated/de/example/GeneratedTest.java", "de.example", "GeneratedTest");
        writeTestClass("module/target/generated/de/example/ModuleGeneratedTest.java", "de.example", "ModuleGeneratedTest");

        assertThat(TestRepositoryCheckout.findTestClasses(root)).containsExactly("de.example.SortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")",
            "@de.tum.cit.ase.ares.api.Policy(value = \"custom.yaml\", withinPath = \"src, test\")", "@de.tum.cit.ase.ares.api.Policy" })
    void aDeactivateActivateCycleRestoresTheAnnotationExactly(String annotation) throws IOException {
        Path testFile = root.resolve("test/de/example/SortingTest.java");
        String original = "package de.example;\n\n" + annotation + "\nclass SortingTest {\n}\n";
        FileUtils.writeStringToFile(testFile.toFile(), original, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.setPolicyAnnotationsActive(root, false)).isEqualTo(1);
        assertThat(Files.readString(testFile)).contains("activated = false").contains(annotation.contains("custom.yaml") ? "value = \"custom.yaml\"" : "class SortingTest");
        assertThat(TestRepositoryCheckout.setPolicyAnnotationsActive(root, false)).isZero();

        assertThat(TestRepositoryCheckout.setPolicyAnnotationsActive(root, true)).isEqualTo(1);
        assertThat(Files.readString(testFile)).isEqualTo(original);
        assertThat(TestRepositoryCheckout.setPolicyAnnotationsActive(root, true)).isZero();
    }

    @Test
    void keepsTheCustomPolicyOfAnImportedAnnotation() throws IOException {
        Path testFile = root.resolve("test/de/example/SortingTest.java");
        String original = "package de.example;\n\nimport de.tum.cit.ase.ares.api.Policy;\n\n@Policy(\"custom.yaml\")\nclass SortingTest {\n}\n";
        FileUtils.writeStringToFile(testFile.toFile(), original, StandardCharsets.UTF_8);

        TestRepositoryCheckout.setPolicyAnnotationsActive(root, false);
        assertThat(Files.readString(testFile)).contains("@Policy(value = \"custom.yaml\", activated = false)");

        TestRepositoryCheckout.setPolicyAnnotationsActive(root, true);
        assertThat(Files.readString(testFile)).contains("@Policy(value = \"custom.yaml\")");
    }

    @Test
    void aRelativePathToTheRootPolicyUsesThePolicyFile() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("value = \"SecurityPolicy.yaml\"", "\"./SecurityPolicy.yaml\""));

        SandboxState missing = TestRepositoryCheckout.evaluate(root);
        assertThat(missing.status()).isEqualTo(SandboxStatus.MISCONFIGURED);
        assertThat(missing.policyFiles()).singleElement().satisfies(policyFile -> {
            assertThat(policyFile.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.MISSING);
            assertThat(policyFile.referencedBy()).containsExactly("de.example.SortingTest");
        });

        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void findsThePolicyBelowARootThatIsRelativeOrNotNormalized() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        Path notNormalized = Files.createDirectories(root.resolve("module")).resolve("..").resolve(".");
        Path relative = Path.of("").toAbsolutePath().relativize(root).resolve("module").resolve("..");

        assertThat(TestRepositoryCheckout.evaluate(notNormalized).status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(TestRepositoryCheckout.evaluate(relative).status()).isEqualTo(SandboxStatus.CONFIGURED);

        TestRepositoryCheckout.deletePolicy(root);
        assertThat(TestRepositoryCheckout.evaluate(notNormalized).status()).isEqualTo(SandboxStatus.MISCONFIGURED);
    }

    @Test
    void findsSymbolicLinksButNotTheGitDirectory() throws IOException {
        Path outside = Files.createDirectories(root.resolve("outside"));
        Path repository = Files.createDirectories(root.resolve("repository"));
        writeTestClass("repository/test/de/example/SortingTest.java", "de.example", "SortingTest");
        FileUtils.writeStringToFile(outside.resolve("Other.java").toFile(), "class Other {\n}\n", StandardCharsets.UTF_8);
        Files.createDirectories(repository.resolve(".git"));
        try {
            Files.createSymbolicLink(repository.resolve("test/de/example/Other.java"), outside.resolve("Other.java"));
            Files.createSymbolicLink(repository.resolve("linked"), outside);
            Files.createSymbolicLink(repository.resolve(".git/ignored"), outside);
        }
        catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "The file system does not support symbolic links");
        }

        assertThat(TestRepositoryCheckout.findSymbolicLinks(repository)).containsExactly("linked", "test/de/example/Other.java");
        assertThat(TestRepositoryCheckout.findTestClasses(repository)).containsExactly("de.example.SortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "import org.junit.jupiter.params.ParameterizedTest;|@ParameterizedTest", "|@org.junit.jupiter.params.ParameterizedTest",
            "import org.junit.jupiter.api.RepeatedTest;|@RepeatedTest(3)", "|@org.junit.jupiter.api.TestFactory", "import org.junit.jupiter.api.TestTemplate;|@TestTemplate",
            "import org.junit.jupiter.api.Test;|@Test" })
    void recognizesEveryKindOfJUnitTest(String importAndAnnotation) throws IOException {
        String[] parts = importAndAnnotation.split("\\|");
        FileUtils.writeStringToFile(root.resolve("test/de/example/SortingTest.java").toFile(), """
                package de.example;

                %s

                class SortingTest {

                    %s
                    void sorts(int size) {
                    }
                }
                """.formatted(parts[0], parts[1]), StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(root.resolve("test/de/example/SortingTestHelper.java").toFile(), """
                package de.example;

                import org.junit.jupiter.api.TestInstance;

                // Not a test class: @TestInstance configures a test class, and @Testing is no JUnit annotation.
                @TestInstance(TestInstance.Lifecycle.PER_CLASS)
                class SortingTestHelper {

                    @Testing
                    void helps() {
                    }
                }
                """, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.findTestClasses(root)).containsExactly("de.example.SortingTest");
    }

    @Test
    void reportsTheSourcesWithAnnotationsThatCannotBeEvaluatedAsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/ConcatenatedTest.java",
                TEST_CLASS.formatted("de.example", "ConcatenatedTest").replace("value = \"SecurityPolicy.yaml\"", "value = \"Security\" + \"Policy.yaml\""));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The state of such a repository is unknown, so it is never reported as active or inactive.
        assertUnsupported(UnsupportedReasonType.UNEVALUABLE_ARGUMENTS, "test/de/example/ConcatenatedTest.java");
    }

    @Test
    void aTestClassThatSwitchesItsPolicyOffDoesNotMakeTheFrameworkInactive() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/ClassTest.java",
                TEST_CLASS.formatted("de.example", "ClassTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        SandboxState state = TestRepositoryCheckout.evaluate(root);
        assertThat(state.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(state.disabledTestClasses()).containsExactly("de.example.ClassTest");

        TestRepositoryCheckout.setPolicyAnnotationsActive(root, false);
        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.INACTIVE);
    }

    @Test
    void reportsTestClassesThatThePolicyDoesNotList() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeTestClass("test/de/example/NewTest.java", "de.example", "NewTest");
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        SandboxState state = TestRepositoryCheckout.evaluate(root);
        assertThat(state.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(state.policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.missingTestClasses()).containsExactly("de.example.NewTest"));

        assertThat(TestRepositoryCheckout.addTestClassesToPolicy(root, "SecurityPolicy.yaml", List.of("de.example.NewTest"))).isTrue();
        assertThat(TestRepositoryCheckout.evaluate(root).policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.missingTestClasses()).isEmpty());
        assertThat(Files.readString(root.resolve("SecurityPolicy.yaml")))
                .isEqualTo(POLICY.replace("    - \"de.example.SortingTest\"\n", "    - \"de.example.SortingTest\"\n    - \"de.example.NewTest\"\n"));
    }

    @Test
    void addsTestClassesToThePolicyFileAtTheCanonicalPath() throws IOException {
        writeBuildFile();
        TestRepositoryCheckout.writePolicy(root, "policies/SecurityPolicy.yaml", POLICY);

        // The annotation may separate the names with a backslash; the evaluation reads policies/SecurityPolicy.yaml, so that file gets the class.
        assertThat(TestRepositoryCheckout.addTestClassesToPolicy(root, "policies\\SecurityPolicy.yaml", List.of("de.example.NewTest"))).isTrue();
        assertThat(Files.readString(root.resolve("policies/SecurityPolicy.yaml")))
                .isEqualTo(POLICY.replace("    - \"de.example.SortingTest\"\n", "    - \"de.example.SortingTest\"\n    - \"de.example.NewTest\"\n"));
        assertThat(TestRepositoryCheckout.addTestClassesToPolicy(root, "../SecurityPolicy.yaml", List.of("de.example.NewTest"))).isFalse();
    }

    @Test
    void namesTestClassesAfterTheirDeclarationAndNotTheirFile() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/Helpers.java", TEST_CLASS.formatted("de.example", "ActualTest"));

        assertThat(TestRepositoryCheckout.findTestClasses(root)).containsExactly("de.example.ActualTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "import de.tum.cit.ase.ares.api.jupiter . Public;", "import de . tum.cit.ase.ares.api.jupiter /* Ares */ . *;" })
    void anImportWithWhitespaceAroundItsDotsRegistersAres(String aresImport) throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("import de.tum.cit.ase.ares.api.jupiter.Public;", aresImport));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void aClassLiteralOfTheSecurityExtensionOutsideExtendWithRegistersNothing() throws IOException {
        writeBuildFile();
        String extensionField = "    private static final Class<?> EXTENSION = de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class;\n\n";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n", extensionField + "    @Test\n"));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void anExtendWithImportedFromElsewhereRegistersNothing() throws IOException {
        writeBuildFile();
        String registration = "import example.metadata.ExtendWith;\n\n@ExtendWith(de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class)\n@Public";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("@Public", registration));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @ParameterizedTest
    @ValueSource(strings = { "import example.metadata.Test;", "import static example.Markers.Test;" })
    void aHelperWhoseTestAnnotationIsImportedFromElsewhereIsNoTestClass(String testImport) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        // @Test is example.metadata.Test or the member type example.Markers.Test here, so the helper has no JUnit test and needs neither Ares nor a policy.
        writeSource("test/de/example/Helper.java", "package de.example;\n\n" + testImport + "\n\nclass Helper {\n\n    @Test\n    void describes() {\n    }\n}\n");
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void anAresMarkerRightAfterAModifierOrAnotherAnnotationCounts() throws IOException {
        writeBuildFile();
        String policy = SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION;
        // Java needs no whitespace before an @, so final@Public is the modifier final and the annotation @Public.
        String header = "@Public\n" + policy + "\nclass";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace(header, policy + "@Public\nfinal class"));
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace(header, policy + "\nfinal@Public class"));
        String listed = "    - \"de.example.SortingTest\"\n";
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY.replace(listed, listed + "    - \"de.example.OtherTest\"\n"));

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void annotationsWithWhitespaceAfterTheAtSignCount() throws IOException {
        writeBuildFile();
        String spaced = TEST_CLASS.formatted("de.example", "SortingTest").replace("@Public", "@ /* Ares */ Public").replace("    @Test", "    @ Test");
        writeSource("test/de/example/SortingTest.java", spaced.replace("@de.tum.cit.ase.ares.api.Policy", "@ de.tum.cit.ase.ares.api.Policy"));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);

        // A qualified test annotation written with whitespace makes a test class too, which then needs a policy.
        // Without @Public, only the JUnit annotation makes OtherTest a test class.
        String withoutPolicy = TEST_CLASS.formatted("de.example", "OtherTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n", "").replace("@Public\n", "");
        writeSource("test/de/example/OtherTest.java", withoutPolicy.replace("    @Test", "    @ org . junit.jupiter.api.Test"));

        assertUnsupported(UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.OtherTest");
    }

    @Test
    void aNestedTypeShadowsAMarkerOnlyWithinItsBody() throws IOException {
        writeBuildFile();
        // Helper.Public is in scope only inside SortingTest's body, so the @Public on SortingTest itself is still the Ares marker.
        writeSource("test/de/example/SortingTest.java",
                TEST_CLASS.formatted("de.example", "SortingTest").replace("import de.tum.cit.ase.ares.api.jupiter.Public;", "import de.tum.cit.ase.ares.api.jupiter.*;")
                        .replace("    @Test\n", "    static class Helper {\n\n        @interface Public {\n        }\n    }\n\n    @Test\n"));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void anUnrelatedClassWithTheNameOfATestClassInAnotherPackageIsNoTestClass() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/helpers/SortingTest.java", "package de.helpers;\n\nclass SortingTest {\n}\n");
        // Java resolves the superclass in the own package first, even though the wildcard import offers the test class of the same name.
        writeSource("test/de/helpers/SortingTestHelper.java", "package de.helpers;\n\nimport de.example.*;\n\nclass SortingTestHelper extends SortingTest {\n}\n");
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void namesThatOnlyResembleAnUnsupportedRegistrationDoNotCount() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java",
                TEST_CLASS.formatted("de.example", "SortingTest")
                        .replace("import org.junit.jupiter.api.Test;\n", "import org.junit.jupiter.api.Test;\nimport de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension;\n")
                        .replace("@Public\n", "@Public\n@de.other.JupiterAresTest\n"));
        TestRepositoryCheckout.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void anInvalidPathOfAnActivePolicyIsMisconfiguredButNeverRead() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("SecurityPolicy.yaml", "../outside.yaml"));

        SandboxState state = TestRepositoryCheckout.evaluate(root);
        assertThat(state.status()).isEqualTo(SandboxStatus.MISCONFIGURED);
        assertThat(state.policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.INVALID_PATH));
        assertThatIllegalArgumentException().isThrownBy(() -> TestRepositoryCheckout.writePolicy(root, "../outside.yaml", POLICY));

        // Ares does not read the policy of a switched off annotation, so its path does not matter.
        TestRepositoryCheckout.setPolicyAnnotationsActive(root, false);
        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.INACTIVE);
    }

    @Test
    void reportsPolicyFilesThatAreDirectoriesTooLargeOrNoPolicy() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");

        Files.createDirectories(root.resolve("SecurityPolicy.yaml"));
        assertThat(onlyPolicyFileProblem()).isEqualTo(PolicyFileProblem.INVALID_PATH);
        Files.delete(root.resolve("SecurityPolicy.yaml"));

        FileUtils.writeStringToFile(root.resolve("SecurityPolicy.yaml").toFile(), "a".repeat(SecurityPolicyYaml.MAX_POLICY_SIZE + 1), StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(PolicyFileProblem.TOO_LARGE);

        FileUtils.writeStringToFile(root.resolve("SecurityPolicy.yaml").toFile(), "key: [unclosed", StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(PolicyFileProblem.UNPARSEABLE);

        FileUtils.writeStringToFile(root.resolve("SecurityPolicy.yaml").toFile(), "thisPolicyFileCompliesToThePolicyVersion: 1\n", StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(PolicyFileProblem.UNPARSEABLE);

        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.3.0'\n}\n", StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(PolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND);
        assertThat(TestRepositoryCheckout.evaluate(root).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void extractsThePermissionsUpToTheNextKeyOfTheSameLevel() {
        String template = """
                regardingTheSupervisedCode:
                  theFollowingResourceAccessesArePermitted:
                    # a comment stays
                    regardingTimeouts:

                      - timeout: 1
                  theFollowingClassesAreTestClasses: [ ]
                """;

        assertThat(TestRepositoryCheckout.extractResourcePermissions(template))
                .contains("  theFollowingResourceAccessesArePermitted:\n    # a comment stays\n    regardingTimeouts:\n\n      - timeout: 1\n");
    }

    @Test
    void extractsThePermissionsUpToTheEndOfTheFile() {
        assertThat(TestRepositoryCheckout.extractResourcePermissions("a:\n  theFollowingResourceAccessesArePermitted:\r\n    regardingTimeouts: [ ]"))
                .contains("  theFollowingResourceAccessesArePermitted:\n    regardingTimeouts: [ ]\n");
    }

    @Test
    void findsNoPermissionsInAPolicyWithoutThem() {
        assertThat(TestRepositoryCheckout.extractResourcePermissions("thisPolicyFileCompliesToThePolicyVersion: 1\n")).isEmpty();
    }

    @Test
    void neverReadsAPolicyBehindASymbolicLink() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("SecurityPolicy.yaml", "policies/SecurityPolicy.yaml"));
        Path outside = Files.createDirectories(root.resolve("elsewhere"));
        FileUtils.writeStringToFile(outside.resolve("SecurityPolicy.yaml").toFile(), POLICY, StandardCharsets.UTF_8);
        try {
            Files.createSymbolicLink(root.resolve("policies"), outside);
        }
        catch (UnsupportedOperationException | IOException e) {
            assumeTrue(false, "The file system does not support symbolic links");
        }

        assertThat(onlyPolicyFileProblem()).isEqualTo(PolicyFileProblem.INVALID_PATH);
        assertThatIllegalArgumentException().isThrownBy(() -> TestRepositoryCheckout.writePolicy(root, "policies/SecurityPolicy.yaml", POLICY));
    }

    @Test
    void readsAndWritesTheAresVersionOfThePomAndNotOneOfAProfile() throws IOException {
        String pom = """
                <project>
                  <profiles>
                    <profile>
                      <id>next</id>
                      <properties>
                        <ares.version>2.3.0</ares.version>
                      </properties>
                    </profile>
                  </profiles>
                  <properties>
                    <ares.version>2.2.1</ares.version>
                  </properties>
                </project>
                """;
        // A profile applies its properties only while it is active, so the pin of the POM itself is the version the build uses.
        assertThat(TestRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("<ares.version>2.2.1</ares.version>", "<ares.version>2.4.0</ares.version>"));
        assertThat(TestRepositoryFiles.parseAresVersion(null, "<profiles><profile><properties><ares.version>2.3.0</ares.version></properties></profile></profiles>")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "<!-- Profiles go into <profiles> -->\n", "<!-- </profiles> -->\n" })
    void aProfileTagInACommentNeitherStartsNorEndsTheProfiles(String comment) throws IOException {
        String pom = """
                <project>
                  %s
                  <properties>
                    <ares.version>2.2.1</ares.version>
                  </properties>
                  <profiles>
                    <!-- </profiles> -->
                    <profile>
                      <properties>
                        <ares.version>2.3.0</ares.version>
                      </properties>
                    </profile>
                  </profiles>
                </project>
                """.formatted(comment.strip());
        assertThat(TestRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("<ares.version>2.2.1</ares.version>", "<ares.version>2.4.0</ares.version>"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "def helper() {\n    def aresVersion = '2.3.0'\n    return aresVersion\n}\n", "tasks.register('notes') {\n    String aresVersion = \"2.3.0\"\n}\n",
            "def describe = {\n    final aresVersion = '2.3.0'\n}\n" })
    void aLocalVariableOfAGradleMethodOrClosureIsNoPin(String helper) throws IOException {
        String gradleBuild = helper + "ext { aresVersion = '2.2.1' }\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(helper + "ext { aresVersion = '2.4.0' }\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext { aresVersion = '2.2' + '.1' }\n", "ext { aresVersion = \"${base}.1\" }\n", "ext.aresVersion = '2.2.1'.trim()\n",
            "ext { aresVersion = '2.2.1' }\next.aresVersion = \"${base}.1\"\n", "ext { aresVersion = '2.2.' /* comment */ + '1' }\n",
            "ext.aresVersion = '2.2.' /* first */ /* multi\nline */ + '1'\n" })
    void aGradleExpressionIsNoPin(String gradleBuild) throws IOException {
        // Artemis could neither read the version of an expression nor replace it without changing what the build computes.
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).isEmpty();
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isFalse();
        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild);
        assertThat(TestRepositoryFiles.parseAresVersion(null, "<properties><ares.version>${base.version}</ares.version></properties>")).isEmpty();
        // Next to a literal pin, an assignment without a readable value leaves the version the build uses unknown.
        assertThat(TestRepositoryFiles.parseAresVersion(null,
                "<properties><ares.version>2.2.1</ares.version></properties><properties><ares.version>${base.version}</ares.version></properties>")).isEmpty();
        assertThat(TestRepositoryFiles.parseAresVersion("ext { aresVersion = '2.2.1' }\nif (aresVersion == '2.2.1') { println 'Ares 2' }\n", null)).contains("2.2.1");
    }

    @Test
    void pinsOfDifferentVersionsPinNone() throws IOException {
        // The later assignment wins in Gradle, which the reader cannot tell in general, so neither version is read or replaced.
        String gradleBuild = "project.ext.aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.2.1'\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).isEmpty();
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);
        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isFalse();

        // Pins of the same version leave no doubt, and a spaced Maven tag is a pin that is replaced as written.
        assertThat(TestRepositoryFiles.parseAresVersion("project.ext.aresVersion = '2.2.1'\nproject.ext.aresVersion = '2.2.1'\n", null)).contains("2.2.1");
        String pom = "<properties><ares.version >2.2.1</ares.version\n></properties>";
        assertThat(TestRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);
        Files.delete(root.resolve("build.gradle"));
        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();
        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("2.2.1", "2.4.0"));
    }

    @Test
    void aGradleBuildThatAssignsTheAresVersionDecidesItEvenNextToAPom() throws IOException {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest"));
        String gradleBuild = "project.ext.aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.2.1'\n";
        String pom = "<properties><ares.version>2.2.1</ares.version></properties>";
        // The Gradle build uses one of its own pins, so the pin of the POM next to it neither tells nor changes the version of the exercise.
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, pom)).isEmpty();
        var reason = new UnsupportedReason("build.gradle", UnsupportedReasonType.NO_ARES_VERSION_PINNED);
        assertThat(evaluate(sources, new BuildFiles(gradleBuild, pom)).unsupportedReasons()).containsExactly(reason);
        String profilePom = "<profiles><profile><properties><ares.version>2.3.0</ares.version></properties></profile></profiles>";
        assertThat(evaluate(sources, new BuildFiles(gradleBuild, profilePom)).unsupportedReasons()).containsExactly(reason);
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.detectBuildTool(root)).isEmpty();
        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isFalse();
        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild);
        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom);

        assertThat(TestRepositoryFiles.parseAresVersion("ext { aresVersion = \"${base}.1\" }\n", pom)).isEmpty();
        // A Gradle build that does not assign the version where the build reads it leaves it to the POM.
        assertThat(TestRepositoryFiles.parseAresVersion("plugins { id 'java' }\n", pom)).contains("2.2.1");
        assertThat(TestRepositoryFiles.parseAresVersion("// aresVersion = '2.3.0'\n", pom)).contains("2.2.1");
    }

    @Test
    void readsAndWritesOnlyTheAresVersionItselfAndNotALongerName() throws IOException {
        String gradleBuild = "def previous_aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.2.1'\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo("def previous_aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.4.0'\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "def example = /aresVersion = '2.3.0'/\n", "def example = $/aresVersion = '2.3.0'/$\n", "def example() {\n    return /aresVersion = '2.3.0'/\n}\n" })
    void ignoresAresVersionsInGradleSlashyStrings(String example) throws IOException {
        assertThat(TestRepositoryFiles.parseAresVersion(example + "ext { aresVersion = '2.2.1' }\n", null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), example + "ext { aresVersion = '2.2.1' }\n", StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(example + "ext { aresVersion = '2.4.0' }\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "def half = total / 2\n", "def half = n++ / 2\n", "def half = n-- / 2\n", "def half = this / 2\n", "def half = (total) / 2\n",
            "def half = values[0] / 2\n", "def half = { total } / 2\n", "def half = \"total\" / 2\n" })
    void readsAndWritesAnAresVersionAfterADivision(String division) throws IOException {
        String gradleBuild = division + "ext { aresVersion = '2.2.1' }\ndef third = total / 3\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild.replace("2.2.1", "2.4.0"));
    }

    @Test
    void aGeneratedPolicyListsTestClassesWithUnicodeNamesCompletely() throws IOException {
        writeBuildFile();
        // The file name stays ASCII, so the test does not depend on how the file system encodes names; Java only requires it for public classes.
        writeTestClass("test/de/buecher/SortingTest.java", "de.bücher", "SortingÜTest");

        String policy = TestRepositoryCheckout.generateDefaultPolicy(root, BuildTool.GRADLE, Optional.of("de.bücher"), "");

        assertThat(policy).contains("    - \"de.bücher.SortingÜTest\"\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "def notes = \"\"\"\naresVersion = '9.9.9'\n\"\"\"\n", "def notes = '''aresVersion = \"9.9.9\"'''\n", "def quote = 'it\\'s aresVersion = \"9.9.9\"'\n",
            "def path = $/ $$ $/ aresVersion = '9.9.9' /$\n", "def pattern = / \\/ aresVersion = '9.9.9' /\n" })
    void ignoresAresVersionsInGradleStringsWithEscapesAndTripleQuotes(String example) throws IOException {
        String gradleBuild = example + "ext { aresVersion = '2.2.1' }\n";
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild.replace("2.2.1", "2.4.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext { aresVersion = '2.2.1' }\n/* aresVersion = '9.9.9'", "ext { aresVersion = '2.2.1' }\n// aresVersion = '9.9.9'",
            "ext { aresVersion = '2.2.1' }\ndef notes = 'aresVersion = \"9.9.9\"", "def notes = 'unclosed\next { aresVersion = '2.2.1' }\n",
            "def broken = + /\next { aresVersion = '2.2.1' }\n", "ext { aresVersion = '2.2.1' }\napply" })
    void readsTheAresVersionNextToCommentsAndStringsThatAreNotClosed(String gradleBuild) throws IOException {
        assertThat(TestRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild.replace("2.2.1", "2.4.0"));
    }

    @Test
    void pinsTheVersionInAMavenBuildOutsideAnUnclosedComment() throws IOException {
        String pom = "<ares.version>2.2.1</ares.version>\n<!-- <ares.version>9.9.9</ares.version>";
        assertThat(TestRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("2.2.1", "2.4.0"));
    }

    @Test
    void pinsNoVersionWithoutABuildFileThatPinsOne() {
        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isFalse();
    }

    @Test
    void aTestClassWithTwoPoliciesIsUnsupportedAndHasNoAnnotationState() {
        String source = TEST_CLASS.formatted("de.example", "SortingTest").replace("class SortingTest",
                "@de.tum.cit.ase.ares.api.Policy(value = \"Other.yaml\")\nclass SortingTest");
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", source);

        assertThat(evaluate(sources, new BuildFiles("ext {\n    aresVersion = '2.2.1'\n}\n", null)).unsupportedReasons())
                .containsExactly(new UnsupportedReason("de.example.SortingTest", UnsupportedReasonType.UNEVALUABLE_ARGUMENTS));
        assertThat(TestRepositoryFiles.annotationStates(sources)).isEmpty();
    }

    @Test
    void readsNoPolicyOutsideTheRootAndNoPolicyThatIsNoUtf8() throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        FileUtils.writeByteArrayToFile(root.resolve(TestRepositoryFiles.POLICY_FILE_NAME).toFile(), new byte[] { (byte) 0xC3, (byte) 0x28 });

        assertThat(TestRepositoryCheckout.readPolicyFile(absoluteRoot, "../outside.yaml").problem()).isEqualTo(PolicyFileProblem.INVALID_PATH);
        assertThat(TestRepositoryCheckout.readPolicyFile(absoluteRoot, TestRepositoryFiles.POLICY_FILE_NAME).problem()).isEqualTo(PolicyFileProblem.UNPARSEABLE);
        assertThat(TestRepositoryCheckout.addTestClassesToPolicy(root, "missing.yaml", List.of("de.example.SortingTest"))).isFalse();
    }

    @Test
    void aJavaSourceThatIsNoUtf8FailsTheEvaluation() throws IOException {
        writeBuildFile();
        FileUtils.writeByteArrayToFile(root.resolve("test/SortingTest.java").toFile(), new byte[] { (byte) 0xC3, (byte) 0x28 });

        assertThatThrownBy(() -> TestRepositoryCheckout.evaluate(root)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void findsNoSourcesInARepositoryThatDoesNotExist() {
        assertThat(TestRepositoryCheckout.javaSources(root.resolve("missing"))).isEmpty();
        assertThat(TestRepositoryFiles.isJavaSourcePath("test//de/example/SortingTest.java", _ -> false)).isTrue();
    }

    @Test
    void aPolicyBelowAFileIsAnInvalidPathAndOneBelowAMissingDirectoryIsMissing() throws IOException {
        FileUtils.writeStringToFile(root.resolve("notes.txt").toFile(), "notes", StandardCharsets.UTF_8);
        Path absoluteRoot = root.toAbsolutePath().normalize();

        // No file can be below notes.txt, so repairing the policy could not write it there.
        assertThat(TestRepositoryCheckout.readPolicyFile(absoluteRoot, "notes.txt/SecurityPolicy.yaml").problem()).isEqualTo(PolicyFileProblem.INVALID_PATH);
        assertThat(TestRepositoryCheckout.readPolicyFile(absoluteRoot, "notes.txt/nested/SecurityPolicy.yaml").problem()).isEqualTo(PolicyFileProblem.INVALID_PATH);
        assertThat(TestRepositoryCheckout.readPolicyFile(absoluteRoot, "policies/SecurityPolicy.yaml").problem()).isEqualTo(PolicyFileProblem.MISSING);
    }

    @Test
    void refusesToWriteOrDeleteAPolicyWhereThereIsNoFile() throws IOException {
        Files.createDirectories(root.resolve(TestRepositoryFiles.POLICY_FILE_NAME).resolve("nested"));
        FileUtils.writeStringToFile(root.resolve("notes.txt").toFile(), "notes", StandardCharsets.UTF_8);

        assertThatIllegalArgumentException().isThrownBy(() -> TestRepositoryCheckout.writePolicy(root, TestRepositoryFiles.POLICY_FILE_NAME, POLICY));
        assertThatIllegalArgumentException().isThrownBy(() -> TestRepositoryCheckout.writePolicy(root, "../SecurityPolicy.yaml", POLICY));
        assertThatIllegalArgumentException().isThrownBy(() -> TestRepositoryCheckout.writePolicy(root, "notes.txt/SecurityPolicy.yaml", POLICY));
        assertThatThrownBy(() -> TestRepositoryCheckout.deletePolicy(root)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void aGeneratedPolicyWithoutPackageAndTestClassesHasAnEmptyList() {
        String policy = TestRepositoryCheckout.generateDefaultPolicy(root, BuildTool.MAVEN, Optional.of(" "), "");

        assertThat(policy).contains("JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ\n  theFollowingClassesAreTestClasses: [ ]\n").doesNotContain("theSupervisedCodeUsesTheFollowingPackage");
    }

    @Test
    void pinsTheVersionOnlyWhereTheBuildReadsIt() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "// aresVersion = '2.3.0'\ndef aresVersion = '2.2.1'\n", StandardCharsets.UTF_8);

        assertThat(TestRepositoryCheckout.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo("// aresVersion = '2.3.0'\ndef aresVersion = '2.4.0'\n");
    }
}
