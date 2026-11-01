package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityFrameworkState;

/**
 * Tests which Java files {@link SecurityPolicyRepositoryFiles} treats as test sources of an exercise-tests repository.
 */
class SecurityPolicyRepositoryFilesTest {

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
              theFollowingClassesAreTestClasses:
                - "de.example.SortingTest"
              theFollowingResourceAccessesArePermitted:
                regardingFileSystemInteractions: [ ]
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

    @Test
    void scansSourcePackagesNamedLikeBuildOutput() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
        writeTestClass("test/de/example/build/BuildTest.java", "de.example.build", "BuildTest");
        writeTestClass("test/de/example/target/TargetTest.java", "de.example.target", "TargetTest");

        assertThat(SecurityPolicyRepositoryFiles.findTestClasses(root)).containsExactly("de.example.build.BuildTest", "de.example.target.TargetTest");
        assertThat(SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false)).isEqualTo(2);
        assertThat(Files.readString(root.resolve("test/de/example/build/BuildTest.java"))).contains(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
    }

    @Test
    void skipsBuildOutputOfTheProjectAndItsModules() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(root.resolve("module/pom.xml").toFile(), "<project/>", StandardCharsets.UTF_8);
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeTestClass("build/generated/de/example/GeneratedTest.java", "de.example", "GeneratedTest");
        writeTestClass("module/target/generated/de/example/ModuleGeneratedTest.java", "de.example", "ModuleGeneratedTest");

        assertThat(SecurityPolicyRepositoryFiles.findTestClasses(root)).containsExactly("de.example.SortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")",
            "@de.tum.cit.ase.ares.api.Policy(value = \"custom.yaml\", withinPath = \"src, test\")", "@de.tum.cit.ase.ares.api.Policy" })
    void aDeactivateActivateCycleRestoresTheAnnotationExactly(String annotation) throws IOException {
        Path testFile = root.resolve("test/de/example/SortingTest.java");
        String original = "package de.example;\n\n" + annotation + "\nclass SortingTest {\n}\n";
        FileUtils.writeStringToFile(testFile.toFile(), original, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false)).isEqualTo(1);
        assertThat(Files.readString(testFile)).contains("activated = false").contains(annotation.contains("custom.yaml") ? "value = \"custom.yaml\"" : "class SortingTest");
        assertThat(SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false)).isZero();

        assertThat(SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, true)).isEqualTo(1);
        assertThat(Files.readString(testFile)).isEqualTo(original);
        assertThat(SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, true)).isZero();
    }

    @Test
    void keepsTheCustomPolicyOfAnImportedAnnotation() throws IOException {
        Path testFile = root.resolve("test/de/example/SortingTest.java");
        String original = "package de.example;\n\nimport de.tum.cit.ase.ares.api.Policy;\n\n@Policy(\"custom.yaml\")\nclass SortingTest {\n}\n";
        FileUtils.writeStringToFile(testFile.toFile(), original, StandardCharsets.UTF_8);

        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false);
        assertThat(Files.readString(testFile)).contains("@Policy(value = \"custom.yaml\", activated = false)");

        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, true);
        assertThat(Files.readString(testFile)).contains("@Policy(value = \"custom.yaml\")");
    }

    @Test
    void aRelativePathToTheRootPolicyUsesThePolicyFile() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("value = \"SecurityPolicy.yaml\"", "\"./SecurityPolicy.yaml\""));

        SecurityFrameworkState missing = SecurityPolicyRepositoryFiles.evaluate(root);
        assertThat(missing.status()).isEqualTo(SecurityActivationStatus.MISCONFIGURED);
        assertThat(missing.policyFiles()).singleElement().satisfies(policyFile -> {
            assertThat(policyFile.policyPath()).isEqualTo("SecurityPolicy.yaml");
            assertThat(policyFile.problem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.MISSING);
            assertThat(policyFile.referencedBy()).containsExactly("de.example.SortingTest");
        });

        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.ACTIVE);
    }

    @Test
    void findsThePolicyBelowARootThatIsRelativeOrNotNormalized() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        Path notNormalized = Files.createDirectories(root.resolve("module")).resolve("..").resolve(".");
        Path relative = Path.of("").toAbsolutePath().relativize(root).resolve("module").resolve("..");

        assertThat(SecurityPolicyRepositoryFiles.evaluate(notNormalized).status()).isEqualTo(SecurityActivationStatus.ACTIVE);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(relative).status()).isEqualTo(SecurityActivationStatus.ACTIVE);

        SecurityPolicyRepositoryFiles.deletePolicy(root);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(notNormalized).status()).isEqualTo(SecurityActivationStatus.MISCONFIGURED);
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

        assertThat(SecurityPolicyRepositoryFiles.findSymbolicLinks(repository)).containsExactly("linked", "test/de/example/Other.java");
        assertThat(SecurityPolicyRepositoryFiles.findTestClasses(repository)).containsExactly("de.example.SortingTest");
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

        assertThat(SecurityPolicyRepositoryFiles.findTestClasses(root)).containsExactly("de.example.SortingTest");
    }

    @Test
    void skipsBuildOutputInTheSameWayForCheckoutsAndCommittedTrees() {
        Set<String> files = Set.of("build.gradle", "module/pom.xml", "build/generated/GeneratedTest.java", "module/target/generated/ModuleTest.java",
                "test/de/example/build/BuildTest.java", "test/de/example/target/TargetTest.java", "other/build/OtherTest.java", ".gradle/cache/CachedTest.java",
                "test/de/example/SortingTest.java");

        assertThat(files.stream().filter(path -> SecurityPolicyRepositoryFiles.isJavaSourcePath(path, files::contains)).sorted()).containsExactly("other/build/OtherTest.java",
                "test/de/example/SortingTest.java", "test/de/example/build/BuildTest.java", "test/de/example/target/TargetTest.java");
    }

    @Test
    void reportsTheSourcesWithAnnotationsThatCannotBeEvaluatedAsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/ConcatenatedTest.java",
                TEST_CLASS.formatted("de.example", "ConcatenatedTest").replace("value = \"SecurityPolicy.yaml\"", "value = \"Security\" + \"Policy.yaml\""));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The state of such a repository is unknown, so it is never reported as active or inactive.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.UNEVALUABLE_ARGUMENTS, "test/de/example/ConcatenatedTest.java");
    }

    private void assertUnsupported(SecurityFrameworkState.UnsupportedReasonType type, String location) {
        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
        assertThat(state.status()).isEqualTo(SecurityActivationStatus.UNSUPPORTED);
        assertThat(state.unsupportedReasons()).contains(new SecurityFrameworkState.UnsupportedReason(location, type));
    }

    @Test
    void aTestClassThatSwitchesItsPolicyOffDoesNotMakeTheFrameworkInactive() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/ClassTest.java",
                TEST_CLASS.formatted("de.example", "ClassTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
        assertThat(state.status()).isEqualTo(SecurityActivationStatus.ACTIVE);
        assertThat(state.disabledTestClasses()).containsExactly("de.example.ClassTest");

        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.INACTIVE);
    }

    @Test
    void reportsTestClassesThatThePolicyDoesNotList() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeTestClass("test/de/example/NewTest.java", "de.example", "NewTest");
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
        assertThat(state.status()).isEqualTo(SecurityActivationStatus.ACTIVE);
        assertThat(state.policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.missingTestClasses()).containsExactly("de.example.NewTest"));

        assertThat(SecurityPolicyRepositoryFiles.addTestClassesToPolicy(root, "SecurityPolicy.yaml", List.of("de.example.NewTest"))).isTrue();
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.missingTestClasses()).isEmpty());
        assertThat(Files.readString(root.resolve("SecurityPolicy.yaml")))
                .isEqualTo(POLICY.replace("    - \"de.example.SortingTest\"\n", "    - \"de.example.SortingTest\"\n    - \"de.example.NewTest\"\n"));
    }

    @Test
    void namesTestClassesAfterTheirDeclarationAndNotTheirFile() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/Helpers.java", TEST_CLASS.formatted("de.example", "ActualTest"));

        assertThat(SecurityPolicyRepositoryFiles.findTestClasses(root)).containsExactly("de.example.ActualTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "class SortingTest {|    @de.tum.cit.ase.ares.api.Policy\n    @Test\n    void sorts() {\n    }\n}",
            "class SortingTest {|    @Test\n    void runs() {\n    }\n\n" + "    @de.tum.cit.ase.ares.api.Policy\n    static class Nested {\n    }\n}" })
    void policiesOnMembersAreUnsupported(String classAndBody) throws IOException {
        writeBuildFile();
        String[] parts = classAndBody.split("\\|");
        writeSource("test/de/example/SortingTest.java", "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\n@de.tum.cit.ase.ares.api.jupiter.Public\n"
                + "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\n" + parts[0] + "\n" + parts[1] + "\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE, "test/de/example/SortingTest.java");
    }

    @Test
    void severalTestTypesInOneFileAreUnsupported() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest") + TEST_CLASS.formatted("de.example", "OtherTest")
                .replace("package de.example;\n", "").replace("import org.junit.jupiter.api.Test;\n", "").replace("import de.tum.cit.ase.ares.api.jupiter.Public;\n", ""));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.MULTIPLE_TOP_LEVEL_TYPES, "test/de/example/SortingTest.java");
    }

    @Test
    void aTestClassWithoutItsOwnPolicyIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/HiddenTest.java", TEST_CLASS.formatted("de.example", "HiddenTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n", "")
                .replace("jupiter.Public;", "jupiter.Hidden;").replace("@Public", "@Hidden"));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.HiddenTest");
    }

    @Test
    void aSubclassThatOnlyInheritsThePolicyIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/MoreSortingTest.java", "package de.example;\n\nclass MoreSortingTest extends SortingTest {\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "@org.junit.jupiter.api.extension.ExtendWith(de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class)",
            "@de.tum.cit.ase.ares.api.jupiter.JupiterAresTest" })
    void aTestClassThatDoesNotRegisterAresThroughItsMarkersIsUnsupported(String registration) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("@Public", registration));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @Test
    void aTestClassThatOnlyUsesJupiterAresTestIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", "package de.example;\n\n@de.tum.cit.ase.ares.api.jupiter.JupiterAresTest\nclass OtherTest {\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "test/de/example/OtherTest.java");
    }

    @Test
    void anUnrelatedClassWithTheNameOfATestClassInAnotherPackageIsNoTestClass() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/helpers/SortingTest.java", "package de.helpers;\n\nclass SortingTest {\n}\n");
        // Java resolves the superclass in the own package first, even though the wildcard import offers the test class of the same name.
        writeSource("test/de/helpers/SortingTestHelper.java", "package de.helpers;\n\nimport de.example.*;\n\nclass SortingTestHelper extends SortingTest {\n}\n");
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.ACTIVE);
    }

    @Test
    void aSubclassInAnotherPackageFoundThroughAWildcardImportIsATestClass() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/other/MoreSortingTest.java", "package de.other;\n\nimport de.example.*;\n\nclass MoreSortingTest extends SortingTest {\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.other.MoreSortingTest");
    }

    @Test
    void namesThatOnlyResembleAnUnsupportedRegistrationDoNotCount() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java",
                TEST_CLASS.formatted("de.example", "SortingTest")
                        .replace("import org.junit.jupiter.api.Test;\n", "import org.junit.jupiter.api.Test;\nimport de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension;\n")
                        .replace("@Public\n", "@Public\n@de.other.JupiterAresTest\n"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.ACTIVE);
    }

    @Test
    void plainJUnitTestsAreUnsupported() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java",
                "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\nclass SortingTest {\n\n    @Test\n    void sorts() {\n    }\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.NO_ARES_2, "de.example.SortingTest");
    }

    @Test
    void ares1TestsAreUnsupported() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "dependencies {\n    testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.15.0'\n}\n",
                StandardCharsets.UTF_8);
        writeSource("test/de/example/SortingTest.java", "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\nimport de.tum.in.test.api.jupiter.Public;\n\n"
                + "@Public\nclass SortingTest {\n\n    @Test\n    void sorts() {\n    }\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ARES_1, "build.gradle");
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.NO_ARES_VERSION_PINNED, "build.gradle");
    }

    @ParameterizedTest
    @ValueSource(strings = { "../outside.yaml", "/etc/policy.yaml", ".git/config", "C:\\policy.yaml", "", "  " })
    void rejectsPolicyPathsOutsideTheRepository(String path) {
        assertThat(SecurityPolicyRepositoryFiles.validatePolicyPath(path)).isEmpty();
        assertThat(SecurityPolicyRepositoryFiles.validatePolicyPath("./policies/../SecurityPolicy.yaml")).contains("SecurityPolicy.yaml");
    }

    @Test
    void anInvalidPathOfAnActivePolicyIsMisconfiguredButNeverRead() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("SecurityPolicy.yaml", "../outside.yaml"));

        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);
        assertThat(state.status()).isEqualTo(SecurityActivationStatus.MISCONFIGURED);
        assertThat(state.policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.problem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH));
        assertThatIllegalArgumentException().isThrownBy(() -> SecurityPolicyRepositoryFiles.writePolicy(root, "../outside.yaml", POLICY));

        // Ares does not read the policy of a switched off annotation, so its path does not matter.
        SecurityPolicyRepositoryFiles.setPolicyAnnotationsActive(root, false);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.INACTIVE);
    }

    @Test
    void reportsPolicyFilesThatAreDirectoriesTooLargeOrNoPolicy() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");

        Files.createDirectories(root.resolve("SecurityPolicy.yaml"));
        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
        Files.delete(root.resolve("SecurityPolicy.yaml"));

        FileUtils.writeStringToFile(root.resolve("SecurityPolicy.yaml").toFile(), "a".repeat(SecurityPolicyYaml.MAX_POLICY_SIZE + 1), StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.TOO_LARGE);

        FileUtils.writeStringToFile(root.resolve("SecurityPolicy.yaml").toFile(), "key: [unclosed", StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE);

        FileUtils.writeStringToFile(root.resolve("SecurityPolicy.yaml").toFile(), "thisPolicyFileCompliesToThePolicyVersion: 1\n", StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.ACTIVE);
    }

    private SecurityFrameworkState.PolicyFileProblem onlyPolicyFileProblem() {
        return SecurityPolicyRepositoryFiles.evaluate(root).policyFiles().getFirst().problem();
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

        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
        assertThatIllegalArgumentException().isThrownBy(() -> SecurityPolicyRepositoryFiles.writePolicy(root, "policies/SecurityPolicy.yaml", POLICY));
    }

    @Test
    void readsTheAresVersionFromBuildFileContents() {
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion("ext {\n    aresVersion = '2.3.0'\n}\n", null)).contains("2.3.0");
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, "<properties><ares.version>2.2.1</ares.version></properties>")).contains("2.2.1");
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, "<project/>")).isEmpty();
    }
}
