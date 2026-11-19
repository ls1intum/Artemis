package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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

    @Test
    void reportsThePartsOfAresTheTestsUseBesidesTheSandbox() throws IOException {
        writeBuildFile();
        writeSource(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, POLICY);
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

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).aresApiUses()).containsExactly(
                new SecurityFrameworkState.AresApiUse("test/de/example/SortingTest.java", "StrictTimeout"),
                new SecurityFrameworkState.AresApiUse("test/de/example/SortingTest.java", "structural.*"),
                new SecurityFrameworkState.AresApiUse("test/de/example/SortingTest.java", "structural.ClassTestProvider"),
                new SecurityFrameworkState.AresApiUse("test/de/example/SortingTest.java", "util.ReflectionTestUtils"));
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
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
    }

    @Test
    void findsThePolicyBelowARootThatIsRelativeOrNotNormalized() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        Path notNormalized = Files.createDirectories(root.resolve("module")).resolve("..").resolve(".");
        Path relative = Path.of("").toAbsolutePath().relativize(root).resolve("module").resolve("..");

        assertThat(SecurityPolicyRepositoryFiles.evaluate(notNormalized).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(relative).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);

        SecurityPolicyRepositoryFiles.deletePolicyFileIfPolicy(root, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(notNormalized).status()).isEqualTo(SecurityActivationStatus.MISCONFIGURED);
    }

    @Test
    void deletesOnlyFilesThatHaveTheShapeOfAPolicy() throws IOException {
        writeBuildFile();
        FileUtils.writeStringToFile(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).toFile(), "database: production\n", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(root.resolve("config/broken.yaml").toFile(), "regardingTheSupervisedCode: [\n", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(root.resolve("config/other.yaml").toFile(), "regardingTheSupervisedCode: everything\n", StandardCharsets.UTF_8);
        SecurityPolicyRepositoryFiles.writePolicy(root, "config/Refused.yaml", "regardingTheSupervisedCode:\n  unknownKey: 1\n");

        for (String path : List.of(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, "config/broken.yaml", "config/other.yaml", "config/Refused.yaml", "config/missing.yaml")) {
            SecurityPolicyRepositoryFiles.deletePolicyFileIfPolicy(root, path);
        }

        assertThat(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).exists();
        assertThat(root.resolve("config/broken.yaml")).exists();
        assertThat(root.resolve("config/other.yaml")).exists();
        assertThat(root.resolve("config/Refused.yaml")).doesNotExist();
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
        assertThat(state.status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
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
        assertThat(state.status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
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
    @ValueSource(strings = { "class SortingTest {|    @de.tum.cit.ase.ares.api.Policy\n    private int size;\n}",
            "class SortingTest {|    @Test\n    void runs() {\n    }\n\n" + "    @de.tum.cit.ase.ares.api.Policy\n    static class Nested {\n    }\n}",
            // A record names its components in parentheses after its name, like the parameters of a method.
            "class SortingTest {|    @Test\n    void runs() {\n    }\n\n" + "    @de.tum.cit.ase.ares.api.Policy(\"Stress.yaml\")\n    record Data(int size) {\n    }\n}" })
    void policiesOnFieldsAndNestedTypesAreUnsupported(String classAndBody) throws IOException {
        writeBuildFile();
        String[] parts = classAndBody.split("\\|");
        writeSource("test/de/example/SortingTest.java", "package de.example;\n\nimport org.junit.jupiter.api.Test;\n\n@de.tum.cit.ase.ares.api.jupiter.Public\n"
                + "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\")\n" + parts[0] + "\n" + parts[1] + "\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.POLICY_NOT_ON_TOP_LEVEL_TYPE, "test/de/example/SortingTest.java");
    }

    @Test
    void aPolicyOnATestMethodIsReadForThatTestCase() throws IOException {
        writeBuildFile();
        writeSource(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, POLICY);
        writeSource("Stress.yaml", POLICY);
        writeSource("test/de/example/SortingTest.java", """
                package de.example;

                import org.junit.jupiter.api.Test;

                @de.tum.cit.ase.ares.api.jupiter.Public
                @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
                class SortingTest {

                    @de.tum.cit.ase.ares.api.Policy(value = "Stress.yaml")
                    @Test
                    void sortsManyElements() {
                    }
                }
                """);

        SecurityFrameworkState state = SecurityPolicyRepositoryFiles.evaluate(root);

        assertThat(state.status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
        assertThat(state.policyFiles()).extracting(SecurityFrameworkState.PolicyFile::referencedBy).containsExactly(List.of("de.example.SortingTest"),
                List.of("de.example.SortingTest#sortsManyElements"));
        assertThat(state.policyFiles()).allSatisfy(policyFile -> assertThat(policyFile.missingTestClasses()).isEmpty());
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

    @Test
    void aSubclassOfATestClassWithAUnicodeNameIsATestClassToo() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingÜTest");
        writeSource("test/de/example/MoreSortingTest.java", "package de.example;\n\nclass MoreSortingTest extends SortingÜTest {\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "@org.junit.jupiter.api.extension.ExtendWith(de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class)",
            "@de.tum.cit.ase.ares.api.jupiter.JupiterAresTest", "@Public$Tag", "@PublicÜ", "@de.tum.cit.ase.ares.api.jupiter.Public$Tag",
            "@ ExtendWith({ Other.class, de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class })" })
    void aTestClassThatDoesNotRegisterAresThroughItsMarkersIsUnsupported(String registration) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("@Public", registration));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "@PublicTest$Tag", "@PublicTestÜ", "@de.tum.cit.ase.ares.api.jupiter.PublicTest$Tag" })
    void aMethodAnnotationWhoseNameOnlyStartsWithAnAresMarkerDoesNotRegisterAres(String annotation) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("jupiter.Public;", "jupiter.*;").replace("@Public\n", "")
                .replace("@Test\n", "@Test\n    " + annotation + "\n"));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @Test
    void aClassLiteralOfTheSecurityExtensionOutsideExtendWithRegistersNothing() throws IOException {
        writeBuildFile();
        String extensionField = "    private static final Class<?> EXTENSION = de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class;\n\n";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n", extensionField + "    @Test\n"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
    }

    @Test
    void aHelperWhoseTestAnnotationIsImportedFromElsewhereIsNoTestClass() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        // @Test is example.metadata.Test here, so the helper has no JUnit test and needs neither Ares nor a policy.
        writeSource("test/de/example/Helper.java", "package de.example;\n\nimport example.metadata.Test;\n\nclass Helper {\n\n    @Test\n    void describes() {\n    }\n}\n");
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
    }

    @Test
    void annotationsWithWhitespaceAfterTheAtSignCount() throws IOException {
        writeBuildFile();
        String spaced = TEST_CLASS.formatted("de.example", "SortingTest").replace("@Public", "@ /* Ares */ Public").replace("    @Test", "    @ Test");
        writeSource("test/de/example/SortingTest.java", spaced.replace("@de.tum.cit.ase.ares.api.Policy", "@ de.tum.cit.ase.ares.api.Policy"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);

        // A qualified test annotation written with whitespace makes a test class too, which then needs a policy.
        String withoutPolicy = TEST_CLASS.formatted("de.example", "OtherTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n", "");
        writeSource("test/de/example/OtherTest.java", withoutPolicy.replace("    @Test", "    @ org . junit.jupiter.api.Test"));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.example.OtherTest");
    }

    @Test
    void anActiveTestMethodInASwitchedOffTestClassIsUnsupported() throws IOException {
        writeBuildFile();
        String switchedOff = TEST_CLASS.formatted("de.example", "SortingTest").replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION,
                SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/example/SortingTest.java", switchedOff.replace("    @Test\n", "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Ares runs the test method with the sandbox, so reporting the class as switched off would hide it.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.SortingTest");
    }

    @Test
    void aMarkerTheFileDeclaresItselfDoesNotRegisterAres() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        // The declared annotation shadows the Ares one that the wildcard import provides.
        writeSource("test/de/example/OtherTest.java", TEST_CLASS.formatted("de.example", "OtherTest").replace("import de.tum.cit.ase.ares.api.jupiter.Public;",
                "import de.tum.cit.ase.ares.api.jupiter.*;\n\n@interface Public {\n}"));

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_ARES, "de.example.OtherTest");
    }

    @Test
    void aNestedTypeShadowsAMarkerOnlyWithinItsBody() throws IOException {
        writeBuildFile();
        // Helper.Public is in scope only inside SortingTest's body, so the @Public on SortingTest itself is still the Ares marker.
        writeSource("test/de/example/SortingTest.java",
                TEST_CLASS.formatted("de.example", "SortingTest").replace("import de.tum.cit.ase.ares.api.jupiter.Public;", "import de.tum.cit.ase.ares.api.jupiter.*;")
                        .replace("    @Test\n", "    static class Helper {\n\n        @interface Public {\n        }\n    }\n\n    @Test\n"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
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

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
    }

    @Test
    void aSubclassInAnotherPackageFoundThroughAWildcardImportIsATestClass() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/other/MoreSortingTest.java", "package de.other;\n\nimport de.example.*;\n\nclass MoreSortingTest extends SortingTest {\n}\n");

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_CLASS_WITHOUT_POLICY, "de.other.MoreSortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "import de.bücher.SortingÜTest;", "import de.bücher.*;" })
    void aSubclassInAnotherPackageOfAnImportedTestClassWithAUnicodeNameIsATestClass(String importDeclaration) throws IOException {
        writeBuildFile();
        writeTestClass("test/de/buecher/SortingTest.java", "de.bücher", "SortingÜTest");
        writeSource("test/de/other/MoreSortingTest.java", "package de.other;\n\n" + importDeclaration + "\n\nclass MoreSortingTest extends SortingÜTest {\n}\n");

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

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
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
        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE);

        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.3.0'\n}\n", StandardCharsets.UTF_8);
        assertThat(onlyPolicyFileProblem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.TEST_CLASS_LIST_NOT_FOUND);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
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

        assertThat(SecurityPolicyRepositoryFiles.extractResourcePermissions(template))
                .contains("  theFollowingResourceAccessesArePermitted:\n    # a comment stays\n    regardingTimeouts:\n\n      - timeout: 1\n");
    }

    @Test
    void extractsThePermissionsUpToTheEndOfTheFile() {
        assertThat(SecurityPolicyRepositoryFiles.extractResourcePermissions("a:\n  theFollowingResourceAccessesArePermitted:\r\n    regardingTimeouts: [ ]"))
                .contains("  theFollowingResourceAccessesArePermitted:\n    regardingTimeouts: [ ]\n");
    }

    @Test
    void findsNoPermissionsInAPolicyWithoutThem() {
        assertThat(SecurityPolicyRepositoryFiles.extractResourcePermissions("thisPolicyFileCompliesToThePolicyVersion: 1\n")).isEmpty();
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

    @Test
    void ignoresAresVersionsInComments() {
        String gradleBuild = "// aresVersion = '2.3.0'\n/* aresVersion = \"9.9.9\" */\nrepositories { maven { url 'https://example.org/maven' } }\ndef aresVersion = '2.2.1'\n";
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion("// aresVersion = '2.3.0'\n", null)).isEmpty();
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, "<!-- <ares.version>2.3.0</ares.version> -->\n<ares.version>2.2.1</ares.version>")).contains("2.2.1");
    }

    @Test
    void ignoresAresVersionsInGradleStrings() {
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion("def example = \"aresVersion = '2.3.0'\"\next { aresVersion = '2.2.1' }\n", null)).contains("2.2.1");
    }

    @Test
    void readsAndWritesOnlyTheAresVersionItselfAndNotALongerName() throws IOException {
        String gradleBuild = "def previous_aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.2.1'\n";
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo("def previous_aresVersion = '2.3.0'\nproject.ext.aresVersion = '2.4.0'\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "def example = /aresVersion = '2.3.0'/\n", "def example = $/aresVersion = '2.3.0'/$\n", "def example() {\n    return /aresVersion = '2.3.0'/\n}\n" })
    void ignoresAresVersionsInGradleSlashyStrings(String example) throws IOException {
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(example + "ext { aresVersion = '2.2.1' }\n", null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), example + "ext { aresVersion = '2.2.1' }\n", StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(example + "ext { aresVersion = '2.4.0' }\n");
    }

    @ParameterizedTest
    @ValueSource(strings = { "def half = total / 2\n", "def half = n++ / 2\n", "def half = n-- / 2\n", "def half = this / 2\n" })
    void readsAndWritesAnAresVersionAfterADivision(String division) throws IOException {
        String gradleBuild = division + "ext { aresVersion = '2.2.1' }\ndef third = total / 3\n";
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild.replace("2.2.1", "2.4.0"));
    }

    @Test
    void aGeneratedPolicyListsTestClassesWithUnicodeNamesCompletely() throws IOException {
        writeBuildFile();
        // The file name stays ASCII, so the test does not depend on how the file system encodes names; Java only requires it for public classes.
        writeTestClass("test/de/buecher/SortingTest.java", "de.bücher", "SortingÜTest");

        String policy = SecurityPolicyRepositoryFiles.generateDefaultPolicy(root, SecurityPolicyRepositoryFiles.BuildTool.GRADLE, Optional.of("de.bücher"), "");

        assertThat(policy).contains("    - \"de.bücher.SortingÜTest\"\n");
    }

    @Test
    void pinsTheVersionOnlyWhereTheBuildReadsIt() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "// aresVersion = '2.3.0'\ndef aresVersion = '2.2.1'\n", StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo("// aresVersion = '2.3.0'\ndef aresVersion = '2.4.0'\n");
    }
}
