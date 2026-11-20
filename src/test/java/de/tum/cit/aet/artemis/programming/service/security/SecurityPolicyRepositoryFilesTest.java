package de.tum.cit.aet.artemis.programming.service.security;

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
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    /**
     * @return a test class like {@link #TEST_CLASS} that a subclass in another package can extend
     */
    private static String publicTestClass(String packageName, String className) {
        return TEST_CLASS.formatted(packageName, className).replace("\nclass ", "\npublic class ");
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
    void anExtendWithImportedFromElsewhereRegistersNothing() throws IOException {
        writeBuildFile();
        String registration = "import example.metadata.ExtendWith;\n\n@ExtendWith(de.tum.cit.ase.ares.api.jupiter.JupiterSecurityExtension.class)\n@Public";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("@Public", registration));
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
    void aSwitchedOffSubclassThatInheritsAnActiveTestMethodIsUnsupported() throws IOException {
        writeBuildFile();
        writeTestClass("test/de/example/SortingTest.java", "de.example", "SortingTest");
        writeSource("test/de/example/MoreSortingTest.java", "package de.example;\n\nimport de.tum.cit.ase.ares.api.jupiter.Public;\n\n@Public\n"
                + SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION + "\nclass MoreSortingTest extends SortingTest {\n}\n");
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).isEmpty();

        // Ares reads the annotation of the inherited test method first, so the subclass runs it with the sandbox.
        String withMethodPolicy = TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n",
                "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n");
        writeSource("test/de/example/SortingTest.java", withMethodPolicy);

        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @CsvSource({ "de.example, void, false", "de.other, void, true", "de.other, public void, false" })
    void aSwitchedOffSubclassThatOverridesTheActiveTestMethodRunsItWithoutTheSandbox(String subclassPackage, String declaration, boolean unsupported) throws IOException {
        writeBuildFile();
        String withMethodPolicy = publicTestClass("de.example", "SortingTest").replace("    @Test\n    void runs()",
                "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    " + declaration + " runs()");
        writeSource("test/de/example/SortingTest.java", withMethodPolicy);
        String override = """
                package %s;

                import org.junit.jupiter.api.Test;

                import de.example.SortingTest;
                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    public void runs() {
                    }
                }
                """.formatted(subclassPackage, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/" + subclassPackage.replace('.', '/') + "/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // JUnit runs the override, which has no annotation of its own, unless a package-private method of another package cannot be overridden.
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason(subclassPackage + ".MoreSortingTest",
                SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS);
        if (unsupported) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @ParameterizedTest
    @CsvSource({ "java.util.Date, false", "java.sql.Date, true" })
    void anOverrideMustTakeTheSameParameterTypesAsTheInheritedTestMethod(String importedDate, boolean unsupported) throws IOException {
        writeBuildFile();
        String withMethodPolicy = publicTestClass("de.example", "SortingTest").replace("    @Test\n    void runs()",
                "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)");
        writeSource("test/de/example/SortingTest.java", withMethodPolicy);
        String override = """
                package de.other;

                import %s;

                import org.junit.jupiter.api.Test;

                import de.example.SortingTest;
                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    public void runs(Date date) {
                    }
                }
                """.formatted(importedDate, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/other/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // A method with a parameter of another type with the same simple name is an overload, so the inherited test still runs with the sandbox.
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason("de.other.MoreSortingTest",
                SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS);
        if (unsupported) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @Test
    void anOverrideWhoseParameterTypesComeFromWildcardImportsKeepsTheInheritedPolicy() throws IOException {
        writeBuildFile();
        String testImport = "import org.junit.jupiter.api.Test;";
        String withWildcardImport = TEST_CLASS.formatted("de.example", "SortingTest").replace(testImport, "import java.util.*;\n\n" + testImport);
        String methodPolicy = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n";
        String parent = withWildcardImport.replace("    @Test\n    void runs()", methodPolicy + "    @Test\n    void runs(Date date)");
        writeSource("test/de/example/SortingTest.java", parent);
        String override = """
                package de.example;

                import java.sql.*;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    void runs(Date date) {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Date may be java.util.Date in one file and java.sql.Date in the other, so the inherited test may still run with the sandbox.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @ValueSource(strings = { "<Date extends java.sql.Date>", "<X, Date extends java.sql.Date>", "<@Deprecated Date extends java.sql.Date>",
            "<@Wrap(@Flag(true)) Date extends java.sql.Date>" })
    void anOverrideWhoseParameterTypeIsATypeVariableKeepsTheInheritedPolicy(String typeParameters) throws IOException {
        writeBuildFile();
        String methodPolicy = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n";
        String annotated = methodPolicy + "    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String override = """
                package de.example;

                import java.util.Date;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    public %s void runs(Date date) {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION, typeParameters);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The type variable Date erases to java.sql.Date, so runs(Date) is an overload and the inherited test still runs with the sandbox.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @CsvSource({ "a.Outer, false", "b.Outer, true" })
    void anOverrideMustNameTheSameOuterTypeForANestedParameterType(String childImport, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(Outer.Inner value)";
        String parent = TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated);
        writeSource("test/de/example/SortingTest.java", parent.replace("import org.junit.jupiter.api.Test;", "import a.Outer;\nimport org.junit.jupiter.api.Test;"));
        String override = """
                package de.example;

                import %s;
                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    public void runs(Outer.Inner value) {
                    }
                }
                """.formatted(childImport, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Outer.Inner names a.Outer.Inner in one file and b.Outer.Inner in the other, so only the same import makes it an override.
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason("de.example.MoreSortingTest",
                SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS);
        if (unsupported) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "MoreSortingTest|    java.util.Map<String, Date> dates;|false", "MoreSortingTest<Date extends java.sql.Date>||true" })
    void onlyATypeVariableInScopeShadowsAnImportedParameterType(String declaration, String field, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String override = """
                package de.example;

                import java.util.Date;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class %s extends SortingTest {

                %s

                    @Test
                    public void runs(Date date) {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION, declaration, field == null ? "" : field);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Date in the type argument of a field declares nothing, so runs(Date) overrides the inherited test; a type variable of the class shadows the import.
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason("de.example.MoreSortingTest",
                SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS);
        if (unsupported) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "Date||java.util.Date|java.util.Date|Date", "Outer|public static class Inner { }|a.Outer.Inner|a.Outer|Outer.Inner" })
    void anOverrideWhoseParameterTypeIsAnInheritedMemberTypeKeepsTheInheritedPolicy(String member, String memberBody, String parentType, String childImport, String childType)
            throws IOException {
        writeBuildFile();
        writeSource("test/a/Outer.java", "package a;\n\npublic class Outer {\n\n    public static class Inner {\n    }\n}\n");
        String memberType = "    public static class " + member + " {\n" + (memberBody == null ? "" : "        " + memberBody + "\n") + "    }\n\n";
        String annotated = memberType + "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(" + parentType + " value)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String override = """
                package de.example;

                import %s;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    public void runs(%s value) {
                    }
                }
                """.formatted(childImport, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION, childType);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The inherited member type of SortingTest shadows the import, so the method is an overload and the inherited test still runs with the sandbox.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.MoreSortingTest");
    }

    @Test
    void anOverrideWhoseParameterTypeIsShadowedByAMemberTypeKeepsTheInheritedPolicy() throws IOException {
        writeBuildFile();
        String methodPolicy = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n";
        String parent = TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", methodPolicy + "    @Test\n    void runs(java.util.Date date)");
        writeSource("test/de/example/SortingTest.java", parent);
        String override = """
                package de.example;

                import java.util.Date;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    static class Date {
                    }

                    @Test
                    void runs(Date date) {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The member type MoreSortingTest.Date shadows the import, so runs(Date) is an overload and the inherited test still runs with the sandbox.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.MoreSortingTest");
    }

    @ParameterizedTest
    @CsvSource({ "de.example, true", "de.other, false" })
    void aPackagePrivateMemberTypeIsInheritedOnlyInItsPackage(String subclassPackage, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    static class Date {\n    }\n\n    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", publicTestClass("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String override = """
                package %s;

                import java.util.Date;

                import org.junit.jupiter.api.Test;

                import de.example.SortingTest;
                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    public void runs(Date date) {
                    }
                }
                """.formatted(subclassPackage, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/" + subclassPackage.replace('.', '/') + "/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // SortingTest.Date shadows the import only in a subclass of its own package, where runs(Date) is an overload rather than an override.
        assertKeepsAnInheritedActivePolicy(subclassPackage + ".MoreSortingTest", unsupported);
    }

    @ParameterizedTest
    @CsvSource({ "de.example, true", "de.other, false" })
    void aPackagePrivateMemberTypeIsInheritedOnlyThroughClassesOfItsPackage(String middlePackage, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    static class Date {\n    }\n\n    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", publicTestClass("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String middle = """
                package %s;

                import de.example.SortingTest;
                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                public class MiddleSortingTest extends SortingTest {
                }
                """.formatted(middlePackage, SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION);
        writeSource("test/" + middlePackage.replace('.', '/') + "/MiddleSortingTest.java", middle);
        String leaf = importingOverride("MoreSortingTest extends MiddleSortingTest", "public void runs(Date date)");
        String imports = "import java.util.Date;\n\nimport " + middlePackage + ".MiddleSortingTest;\n";
        writeSource("test/de/example/MoreSortingTest.java", leaf.replace("import java.util.Date;\n", imports));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // A class of another package does not inherit SortingTest.Date, so neither do its subclasses, even in the package of SortingTest.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", unsupported);
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void aSupertypeThatSeveralBranchesOfTheHierarchyShareIsVisitedOnce() throws IOException {
        writeBuildFile();
        // 30 layers of two interfaces that each extend both interfaces of the layer below: visiting every path would take about 2^30 steps per interface.
        for (int layer = 0; layer < 30; layer++) {
            String supertypes = layer == 0 ? "" : " extends Layer" + (layer - 1) + "A, Layer" + (layer - 1) + "B";
            for (String side : List.of("A", "B")) {
                writeSource("test/de/example/Layer" + layer + side + ".java", "package de.example;\n\ninterface Layer" + layer + side + supertypes + " {\n}\n");
            }
        }
        String implementing = TEST_CLASS.formatted("de.example", "SortingTest").replace("class SortingTest", "class SortingTest implements Layer29A, Layer29B");
        writeSource("test/de/example/SortingTest.java", implementing.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The switched-off class makes the evaluation collect the member types in scope of its hierarchy.
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({ "'', true", "'    @Test\n    void runs() {\n    }\n', false" })
    void anActiveSubclassThatInheritsATestMethodWithItsOwnPolicyIsUnsupported(String override, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    void runs()";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String subclass = TEST_CLASS.formatted("de.example", "MoreSortingTest").replace("class MoreSortingTest", "class MoreSortingTest extends SortingTest");
        writeSource("test/de/example/MoreSortingTest.java", subclass.replace("    @Test\n    void runs() {\n    }\n", override.replace("\\n", "\n")));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Ares runs the inherited runs() of MoreSortingTest with the annotation of SortingTest#runs, whatever policy MoreSortingTest is assigned to.
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason("de.example.MoreSortingTest",
                SecurityFrameworkState.UnsupportedReasonType.INHERITED_METHOD_POLICY);
        if (unsupported) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @Test
    void theSuperclassIsTheFirstExistingCandidateEvenIfItIsNoTestClass() throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs()";
        writeSource("test/de/tests/Base.java", publicTestClass("de.tests", "Base").replace("    @Test\n    void runs()", annotated));
        writeSource("test/de/helpers/Base.java", "package de.helpers;\n\npublic class Base {\n}\n");
        String child = TEST_CLASS.formatted("de.helpers", "Child").replace("class Child", "class Child extends Base").replace("void runs()", "void sorts()")
                .replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/helpers/Child.java", child.replace("import org.junit.jupiter.api.Test;", "import de.tests.*;\nimport org.junit.jupiter.api.Test;"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Child extends de.helpers.Base of its own package, not the test class de.tests.Base of the wildcard import, so it inherits no test method.
        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).noneMatch(reason -> reason.location().equals("de.helpers.Child"));
    }

    @Test
    void aTypeArgumentOfASupertypeDeclaresNoTypeVariable() throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        writeSource("test/de/example/Marker.java", "package de.example;\n\ninterface Marker<T> {\n}\n");
        writeSource("test/de/example/MoreSortingTest.java", importingOverride("MoreSortingTest extends SortingTest implements Marker<Date>", "public void runs(Date date)"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Date in Marker<Date> is the imported java.util.Date, so runs(Date) overrides the inherited test.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", false);
    }

    @Test
    void aTypeVariableOfAMemberTypeDoesNotShadowAnImportedParameterType() throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String override = """
                package de.example;

                import java.util.Date;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    static class Helper<Date> {
                    }

                    @Test
                    public void runs(Date date) {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/example/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // The type variable Date is in scope in the body of Helper only, so runs(Date) names java.util.Date and overrides the inherited test.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", false);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "void helper() { class Date { } }|false", "static class Outer { static class Date { } }|false", "private static class Date { }|false",
            "public static class Date { }|true" })
    void onlyADirectMemberTypeOfASuperclassShadowsAnImportedParameterType(String parentMember, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    " + parentMember + "\n\n    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        writeSource("test/de/example/MoreSortingTest.java", importingOverride("MoreSortingTest extends SortingTest", "public void runs(Date date)"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // A local type of a method, a type nested in a member type and a private member type are not inherited, so runs(Date) overrides the test.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", unsupported);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "class SortingTest|MoreSortingTest extends SortingTest implements Dates",
            "class SortingTest implements Dates|MoreSortingTest extends SortingTest", "class SortingTest implements MoreDates|MoreSortingTest extends SortingTest" })
    void aMemberTypeInheritedFromAnInterfaceShadowsAnImportedParameterType(String parentDeclaration, String childDeclaration) throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        String parent = TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated);
        writeSource("test/de/example/SortingTest.java", parent.replace("class SortingTest", parentDeclaration));
        writeSource("test/de/example/Dates.java", "package de.example;\n\ninterface Dates {\n\n    class Date {\n    }\n}\n");
        writeSource("test/de/example/MoreDates.java", "package de.example;\n\ninterface MoreDates extends Dates {\n}\n");
        writeSource("test/de/example/MoreSortingTest.java", importingOverride(childDeclaration, "public void runs(Date date)"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Dates.Date shadows the import in every class that implements Dates, so runs(Date) is an overload and the inherited test still runs with the sandbox.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", true);
    }

    @Test
    void aMemberTypeOfASubclassDoesNotShadowTheParameterTypeOfAnOverrideInItsSuperclass() throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String middle = importingOverride("MiddleSortingTest extends SortingTest", "public void runs(Date date)");
        writeSource("test/de/example/MiddleSortingTest.java", middle.replace(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION));
        String leaf = importingOverride("MoreSortingTest extends MiddleSortingTest", "public void check()");
        writeSource("test/de/example/MoreSortingTest.java", leaf.replace("    @Test\n", "    static class Date {\n    }\n\n    @Test\n"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // MiddleSortingTest overrides runs(java.util.Date); the member type Date of MoreSortingTest is not in scope there.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", false);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aTestInterfaceIsNotSupported(boolean implementedBySwitchedOffClass) throws IOException {
        writeBuildFile();
        // Either the interface carries the policy for its default test, or the test has an active policy of its own while the class switches the sandbox off.
        String interfacePolicy = implementedBySwitchedOffClass ? "" : SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION;
        String methodPolicy = implementedBySwitchedOffClass ? SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION : "";
        String sharedTests = """
                package de.example;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                interface SharedTests {

                    %s
                    @Test
                    default void sharedTest() {
                    }
                }
                """.formatted(interfacePolicy, methodPolicy);
        writeSource("test/de/example/SharedTests.java", sharedTests);
        String implementing = TEST_CLASS.formatted("de.example", "SortingTest").replace("class SortingTest", "class SortingTest implements SharedTests");
        if (implementedBySwitchedOffClass) {
            implementing = implementing.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        }
        writeSource("test/de/example/SortingTest.java", implementing);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // JUnit runs sharedTest() in SortingTest as well, under an annotation that neither the assignments nor the state of SortingTest show.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_INTERFACE, "test/de/example/SharedTests.java");
    }

    @ParameterizedTest
    @ValueSource(strings = { "@net.jqwik.api.Example", "@net.jqwik.api.Example\n    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION })
    void anInterfaceWithAJqwikExampleIsNotSupported(String annotations) throws IOException {
        writeBuildFile();
        writeSource("test/de/example/SharedTests.java", "package de.example;\n\ninterface SharedTests {\n\n    " + annotations + "\n    default void sharedTest() {\n    }\n}\n");
        String implementing = TEST_CLASS.formatted("de.example", "SortingTest").replace("class SortingTest", "class SortingTest implements SharedTests");
        writeSource("test/de/example/SortingTest.java", implementing);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // jqwik runs sharedTest() in SortingTest as well, also when only the method carries a @Policy annotation.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.TEST_INTERFACE, "test/de/example/SharedTests.java");
    }

    @Test
    void anInterfaceWithoutTestsOrAresAnnotationsIsSupported() throws IOException {
        writeBuildFile();
        writeSource("test/de/example/Answers.java", "package de.example;\n\ninterface Answers {\n\n    default int answer() {\n        return 42;\n    }\n}\n");
        String implementing = TEST_CLASS.formatted("de.example", "SortingTest").replace("class SortingTest", "class SortingTest implements Answers");
        writeSource("test/de/example/SortingTest.java", implementing);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        assertThat(SecurityPolicyRepositoryFiles.evaluate(root).status()).isNotEqualTo(SecurityActivationStatus.UNSUPPORTED);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "MoreSortingTest extends SortingTest implements java.io.Serializable",
            "MoreSortingTest extends SortingTest implements Comparable<MoreSortingTest>, Cloneable" })
    void aSupertypeOutsideTheRepositoryMayShadowAnImportedParameterType(String declaration) throws IOException {
        writeBuildFile();
        String annotated = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    public void runs(java.util.Date date)";
        writeSource("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        writeSource("test/de/example/MoreSortingTest.java", importingOverride(declaration, "public void runs(Date date)"));
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // A member type Date of a library interface would shadow the import, so runs(Date) may be an overload and the inherited test may still run with the sandbox.
        assertKeepsAnInheritedActivePolicy("de.example.MoreSortingTest", true);
    }

    /**
     * @return a switched-off test class that imports {@code java.util.Date} and declares one test method
     */
    private static String importingOverride(String declaration, String method) {
        return """
                package de.example;

                import java.util.Date;

                import org.junit.jupiter.api.Test;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class %s {

                    @Test
                    %s {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION, declaration, method);
    }

    private void assertKeepsAnInheritedActivePolicy(String testClass, boolean expected) {
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason(testClass,
                SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS);
        if (expected) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @ParameterizedTest
    @CsvSource({ "protected, false", "'', true" })
    void theModifiersOfAnInheritedTestMethodMayPrecedeItsAnnotations(String modifier, boolean unsupported) throws IOException {
        writeBuildFile();
        String annotated = "    " + modifier + " @SuppressWarnings({ \"unused\" })\n    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n    @Test\n    void runs()";
        writeSource("test/de/example/SortingTest.java", publicTestClass("de.example", "SortingTest").replace("    @Test\n    void runs()", annotated));
        String override = """
                package de.other;

                import org.junit.jupiter.api.Test;

                import de.example.SortingTest;
                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @Test
                    protected void runs() {
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/other/MoreSortingTest.java", override);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // A protected method is overridden in another package as well; a package-private one is not.
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason("de.other.MoreSortingTest",
                SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS);
        if (unsupported) {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(SecurityPolicyRepositoryFiles.evaluate(root).unsupportedReasons()).doesNotContain(reason);
        }
    }

    @Test
    void aTestMethodOfANestedTypeDoesNotOverrideAnInheritedTest() throws IOException {
        writeBuildFile();
        String methodPolicy = "    " + SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION + "\n";
        String parent = TEST_CLASS.formatted("de.example", "SortingTest").replace("    @Test\n    void runs()", methodPolicy + "    @Test\n    void runs()");
        writeSource("test/de/example/SortingTest.java", parent);
        String group = """
                package de.example;

                import de.tum.cit.ase.ares.api.jupiter.Public;

                @Public
                %s
                class MoreSortingTest extends SortingTest {

                    @net.jqwik.api.Group
                    class Inner {

                        @net.jqwik.api.Example
                        void runs() {
                        }
                    }
                }
                """.formatted(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION);
        writeSource("test/de/example/MoreSortingTest.java", group);
        SecurityPolicyRepositoryFiles.writePolicy(root, "SecurityPolicy.yaml", POLICY);

        // Inner.runs() is a test of the group, so MoreSortingTest still runs the inherited runs() with the sandbox.
        assertUnsupported(SecurityFrameworkState.UnsupportedReasonType.ACTIVE_POLICY_IN_SWITCHED_OFF_CLASS, "de.example.MoreSortingTest");
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
        writeSource("test/de/example/SortingTest.java", publicTestClass("de.example", "SortingTest"));
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
    @ValueSource(strings = { "../outside.yaml", "/etc/policy.yaml", ".git/config", "C:\\policy.yaml", "", "  ", "a\0b.yaml", "\\policy.yaml", ".", ".git", "..",
            "a/../../policy.yaml", "x\\..\\.git\\config", "a\\..\\..\\policy.yaml" })
    void rejectsPolicyPathsOutsideTheRepository(String path) {
        assertThat(SecurityPolicyRepositoryFiles.validatePolicyPath(path)).isEmpty();
        assertThat(SecurityPolicyRepositoryFiles.validatePolicyPath("./policies/../SecurityPolicy.yaml")).contains("SecurityPolicy.yaml");
        // A backslash separates names, so the path is normalized as the checkout resolves it.
        assertThat(SecurityPolicyRepositoryFiles.validatePolicyPath(".\\policies\\..\\SecurityPolicy.yaml")).contains("SecurityPolicy.yaml");
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

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = { "ext { aresVersion = '2.2.1' } // Replaced artemis-java-test-sandbox with Ares 2\\n||false",
            "/* artemis-java-test-sandbox */ ext { aresVersion = '2.2.1' }\\n||false",
            "ext { aresVersion = '2.2.1' }\\ndependencies { testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0' }\\n||true",
            "ext { aresVersion = '2.2.1' }\\ndependencies { testImplementation group: 'de.tum.in.ase', name: 'artemis-java-test-sandbox' }\\n||true",
            "|<!-- replaced artemis-java-test-sandbox --><properties><ares.version>2.2.1</ares.version></properties>|false",
            "|<properties><ares.version>2.2.1</ares.version></properties><dependency><artifactId> artemis-java-test-sandbox </artifactId></dependency>|true" })
    void onlyADependencyOnAres1MakesTheBuildUnsupported(String gradleBuild, String mavenPom, boolean ares1) {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest"));
        String gradle = gradleBuild == null ? null : gradleBuild.replace("\\n", "\n");
        SecurityFrameworkState.UnsupportedReason reason = new SecurityFrameworkState.UnsupportedReason(gradle != null ? "build.gradle" : "pom.xml",
                SecurityFrameworkState.UnsupportedReasonType.ARES_1);

        // A comment that mentions the library, e.g. after the migration to Ares 2, declares no dependency on it.
        if (ares1) {
            assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles(gradle, mavenPom)).unsupportedReasons()).contains(reason);
        }
        else {
            assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles(gradle, mavenPom)).unsupportedReasons()).doesNotContain(reason);
        }
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
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("<ares.version>2.2.1</ares.version>", "<ares.version>2.4.0</ares.version>"));
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, "<profiles><profile><properties><ares.version>2.3.0</ares.version></properties></profile></profiles>"))
                .isEmpty();
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
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("<ares.version>2.2.1</ares.version>", "<ares.version>2.4.0</ares.version>"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "def helper() {\n    def aresVersion = '2.3.0'\n    return aresVersion\n}\n", "tasks.register('notes') {\n    String aresVersion = \"2.3.0\"\n}\n",
            "def describe = {\n    final aresVersion = '2.3.0'\n}\n" })
    void aLocalVariableOfAGradleMethodOrClosureIsNoPin(String helper) throws IOException {
        String gradleBuild = helper + "ext { aresVersion = '2.2.1' }\n";
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(helper + "ext { aresVersion = '2.4.0' }\n");
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
    @ValueSource(strings = { "def half = total / 2\n", "def half = n++ / 2\n", "def half = n-- / 2\n", "def half = this / 2\n", "def half = (total) / 2\n",
            "def half = values[0] / 2\n", "def half = { total } / 2\n", "def half = \"total\" / 2\n" })
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

    @ParameterizedTest
    @ValueSource(strings = { "def notes = \"\"\"\naresVersion = '9.9.9'\n\"\"\"\n", "def notes = '''aresVersion = \"9.9.9\"'''\n", "def quote = 'it\\'s aresVersion = \"9.9.9\"'\n",
            "def path = $/ $$ $/ aresVersion = '9.9.9' /$\n", "def pattern = / \\/ aresVersion = '9.9.9' /\n" })
    void ignoresAresVersionsInGradleStringsWithEscapesAndTripleQuotes(String example) throws IOException {
        String gradleBuild = example + "ext { aresVersion = '2.2.1' }\n";
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild.replace("2.2.1", "2.4.0"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "ext { aresVersion = '2.2.1' }\n/* aresVersion = '9.9.9'", "ext { aresVersion = '2.2.1' }\n// aresVersion = '9.9.9'",
            "ext { aresVersion = '2.2.1' }\ndef notes = 'aresVersion = \"9.9.9\"", "def notes = 'unclosed\next { aresVersion = '2.2.1' }\n",
            "def broken = + /\next { aresVersion = '2.2.1' }\n", "ext { aresVersion = '2.2.1' }\napply" })
    void readsTheAresVersionNextToCommentsAndStringsThatAreNotClosed(String gradleBuild) throws IOException {
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(gradleBuild, null)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), gradleBuild, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo(gradleBuild.replace("2.2.1", "2.4.0"));
    }

    @Test
    void pinsTheVersionInAMavenBuildOutsideAnUnclosedComment() throws IOException {
        String pom = "<ares.version>2.2.1</ares.version>\n<!-- <ares.version>9.9.9</ares.version>";
        assertThat(SecurityPolicyRepositoryFiles.parseAresVersion(null, pom)).contains("2.2.1");
        FileUtils.writeStringToFile(root.resolve("pom.xml").toFile(), pom, StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(pom.replace("2.2.1", "2.4.0"));
    }

    @Test
    void pinsNoVersionWithoutABuildFileThatPinsOne() {
        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isFalse();
    }

    @Test
    void namesTheBuildFileThatRefersToAres1OrPinsNoVersion() {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest"));
        String ares1Pom = "<dependency><artifactId>artemis-java-test-sandbox</artifactId></dependency>";
        String ares1Gradle = "dependencies { testImplementation 'de.tum.in.ase:artemis-java-test-sandbox:1.13.0' }\n";

        assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles(null, ares1Pom)).unsupportedReasons()).containsExactly(
                new SecurityFrameworkState.UnsupportedReason("pom.xml", SecurityFrameworkState.UnsupportedReasonType.ARES_1),
                new SecurityFrameworkState.UnsupportedReason("pom.xml", SecurityFrameworkState.UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles(ares1Gradle, null)).unsupportedReasons()).containsExactly(
                new SecurityFrameworkState.UnsupportedReason("build.gradle", SecurityFrameworkState.UnsupportedReasonType.ARES_1),
                new SecurityFrameworkState.UnsupportedReason("build.gradle", SecurityFrameworkState.UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles(null, "<project/>")).unsupportedReasons())
                .containsExactly(new SecurityFrameworkState.UnsupportedReason("pom.xml", SecurityFrameworkState.UnsupportedReasonType.NO_ARES_VERSION_PINNED));
        assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles(null, null)).unsupportedReasons())
                .containsExactly(new SecurityFrameworkState.UnsupportedReason("build.gradle", SecurityFrameworkState.UnsupportedReasonType.NO_ARES_VERSION_PINNED));
    }

    @Test
    void aBlankPolicyPathNamesNoPolicyFile() {
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", TEST_CLASS.formatted("de.example", "SortingTest").replace("SecurityPolicy.yaml", " "));

        SecurityFrameworkState state = evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles("ext {\n    aresVersion = '2.2.1'\n}\n", null));

        assertThat(state.status()).isEqualTo(SecurityActivationStatus.CONFIGURED);
        assertThat(state.policyFiles()).isEmpty();
    }

    @Test
    void aTestClassWithTwoPoliciesIsUnsupportedAndHasNoAnnotationState() {
        String source = TEST_CLASS.formatted("de.example", "SortingTest").replace("class SortingTest",
                "@de.tum.cit.ase.ares.api.Policy(value = \"Other.yaml\")\nclass SortingTest");
        Map<String, String> sources = Map.of("test/de/example/SortingTest.java", source);

        assertThat(evaluate(sources, new SecurityPolicyRepositoryFiles.BuildFiles("ext {\n    aresVersion = '2.2.1'\n}\n", null)).unsupportedReasons())
                .containsExactly(new SecurityFrameworkState.UnsupportedReason("de.example.SortingTest", SecurityFrameworkState.UnsupportedReasonType.UNEVALUABLE_ARGUMENTS));
        assertThat(SecurityPolicyRepositoryFiles.annotationStates(sources)).isEmpty();
    }

    private static SecurityFrameworkState evaluate(Map<String, String> sources, SecurityPolicyRepositoryFiles.BuildFiles buildFiles) {
        return SecurityPolicyRepositoryFiles.evaluate(sources, _ -> SecurityPolicyRepositoryFiles.PolicyFileContent.of(POLICY), buildFiles);
    }

    @Test
    void readsNoPolicyOutsideTheRootAndNoPolicyThatIsNoUtf8() throws IOException {
        Path absoluteRoot = root.toAbsolutePath().normalize();
        FileUtils.writeByteArrayToFile(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).toFile(), new byte[] { (byte) 0xC3, (byte) 0x28 });

        assertThat(SecurityPolicyRepositoryFiles.readPolicyFile(absoluteRoot, "../outside.yaml").problem()).isEqualTo(SecurityFrameworkState.PolicyFileProblem.INVALID_PATH);
        assertThat(SecurityPolicyRepositoryFiles.readPolicyFile(absoluteRoot, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).problem())
                .isEqualTo(SecurityFrameworkState.PolicyFileProblem.UNPARSEABLE);
        assertThat(SecurityPolicyRepositoryFiles.addTestClassesToPolicy(root, "missing.yaml", List.of("de.example.SortingTest"))).isFalse();
    }

    @Test
    void aJavaSourceThatIsNoUtf8FailsTheEvaluation() throws IOException {
        writeBuildFile();
        FileUtils.writeByteArrayToFile(root.resolve("test/SortingTest.java").toFile(), new byte[] { (byte) 0xC3, (byte) 0x28 });

        assertThatThrownBy(() -> SecurityPolicyRepositoryFiles.evaluate(root)).isInstanceOf(UncheckedIOException.class);
    }

    @Test
    void findsNoSourcesInARepositoryThatDoesNotExist() {
        assertThat(SecurityPolicyRepositoryFiles.javaSources(root.resolve("missing"))).isEmpty();
        assertThat(SecurityPolicyRepositoryFiles.isJavaSourcePath("test//de/example/SortingTest.java", _ -> false)).isTrue();
    }

    @Test
    void refusesToWriteOrDeleteAPolicyWhereThereIsNoFile() throws IOException {
        Files.createDirectories(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).resolve("nested"));
        FileUtils.writeStringToFile(root.resolve("notes.txt").toFile(), "notes", StandardCharsets.UTF_8);

        assertThatIllegalArgumentException().isThrownBy(() -> SecurityPolicyRepositoryFiles.writePolicy(root, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, POLICY));
        assertThatIllegalArgumentException().isThrownBy(() -> SecurityPolicyRepositoryFiles.writePolicy(root, "../SecurityPolicy.yaml", POLICY));
        assertThatThrownBy(() -> SecurityPolicyRepositoryFiles.writePolicy(root, "notes.txt/SecurityPolicy.yaml", POLICY)).isInstanceOf(UncheckedIOException.class);
        // A directory at the path of a policy is no policy file, so deleting the policy leaves it alone.
        SecurityPolicyRepositoryFiles.deletePolicyFile(root, SecurityPolicyRepositoryFiles.POLICY_FILE_NAME);
        assertThat(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).resolve("nested")).isDirectory();
    }

    @Test
    void aGeneratedPolicyWithoutPackageAndTestClassesHasAnEmptyList() {
        String policy = SecurityPolicyRepositoryFiles.generateDefaultPolicy(root, SecurityPolicyRepositoryFiles.BuildTool.MAVEN, Optional.of(" "), "");

        assertThat(policy).contains("JAVA_USING_MAVEN_ARCHUNIT_AND_ASPECTJ\n  theFollowingClassesAreTestClasses: [ ]\n").doesNotContain("theSupervisedCodeUsesTheFollowingPackage");
    }

    @Test
    void pinsTheVersionOnlyWhereTheBuildReadsIt() throws IOException {
        FileUtils.writeStringToFile(root.resolve("build.gradle").toFile(), "// aresVersion = '2.3.0'\ndef aresVersion = '2.2.1'\n", StandardCharsets.UTF_8);

        assertThat(SecurityPolicyRepositoryFiles.writeAresVersion(root, "2.4.0")).isTrue();

        assertThat(Files.readString(root.resolve("build.gradle"))).isEqualTo("// aresVersion = '2.3.0'\ndef aresVersion = '2.4.0'\n");
    }
}
