package de.tum.cit.aet.artemis.sandbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;

import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.sandbox.domain.CompatibilityVerdict;
import de.tum.cit.aet.artemis.sandbox.domain.PolicyFileProblem;
import de.tum.cit.aet.artemis.sandbox.domain.SandboxStatus;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxConfigurationDTO;
import de.tum.cit.aet.artemis.sandbox.dto.SandboxFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.sandbox.util.SecurityPolicyTestAnnotations;
import de.tum.cit.aet.artemis.sandbox.util.SecurityPolicyYaml;
import de.tum.cit.aet.artemis.sandbox.util.TestRepositoryFiles;

/**
 * Tests how {@link SandboxConfigurationService} reads the sandbox configuration of an exercise from the pushed state of its tests repository, and which Ares
 * versions it offers.
 */
class SandboxConfigurationServiceTest {

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    private static final String PACKAGE_NAME = "de.example";

    private static final String CUSTOM_POLICY = """
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
                regardingThreadCreations:
                  - createTheFollowingNumberOfThreads: 1
                    ofThisClass: "java.util.concurrent.ThreadLocalRandom"
                regardingPackageImports: [ ]
                regardingTimeouts: [ ]
            """;

    /** Valid YAML that Ares 2.2.1 refuses, because the required fields are missing. */
    private static final String PARTIAL_POLICY = "regardingTheSupervisedCode:\n  theFollowingClassesAreTestClasses:\n    - \"de.example.SortingTest\"\n";

    private static final String TEST_CLASS = """
            package de.example;

            import org.junit.jupiter.api.Test;

            // The fully qualified name is required, because the exercise itself contains a class named Policy
            @de.tum.cit.ase.ares.api.jupiter.Public
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class SortingTest {

                @Test
                void sorts() {
                }
            }
            """;

    private static final String NEW_TEST_CLASS = TEST_CLASS.replace("SortingTest", "NewTest");

    @TempDir
    private Path root;

    private Path remote;

    private final AtomicInteger directoryCounter = new AtomicInteger();

    private BareGitRepositoryService bareGitRepositoryService;

    private ResourceLoaderService resourceLoaderService;

    private SandboxConfigurationService configurationService;

    @BeforeEach
    void setUp() throws Exception {
        remote = root.resolve("remote.git");
        initRemote(true);

        bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(LocalVCRepositoryUri.class), anyString(), anyBoolean()))
                .thenAnswer(_ -> new Repository(new FileRepositoryBuilder().setGitDir(remote.toFile()).setBare().setup(), remote, mock(LocalVCRepositoryUri.class)));

        resourceLoaderService = mock(ResourceLoaderService.class);
        when(resourceLoaderService.getResource(any(Path.class))).thenAnswer(call -> new FileSystemResource(MAIN_RESOURCES.resolve(call.getArgument(0, Path.class))));

        configurationService = createService("2.3.0", "2.2.1");
    }

    private SandboxConfigurationService createService(String... configuredVersions) {
        return createServiceForBranch(null, configuredVersions);
    }

    /**
     * @param branch the branch the build configuration names; null if it names none
     */
    private SandboxConfigurationService createServiceForBranch(String branch, String... configuredVersions) {
        ProgrammingExerciseBuildConfig buildConfig = new ProgrammingExerciseBuildConfig();
        buildConfig.setBranch(branch);
        ProgrammingExerciseBuildConfigRepository buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(anyLong())).thenReturn(buildConfig);
        return new SandboxConfigurationService(bareGitRepositoryService, buildConfigRepository, resourceLoaderService, "main", configuredVersions);
    }

    /**
     * Creates a new, uniquely named directory below the test's temporary folder (the architecture tests forbid {@code Files.createTempDirectory}).
     */
    private Path newDirectory(String prefix) throws IOException {
        return Files.createDirectories(root.resolve(prefix + directoryCounter.incrementAndGet()));
    }

    /**
     * Creates the bare tests repository with a single commit, laid out like the tests repository of the Java Gradle template.
     */
    private void initRemote(boolean withPolicy) throws Exception {
        Path seed = newDirectory("seed-");
        FileUtils.writeStringToFile(seed.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '2.2.1'\n}\n", StandardCharsets.UTF_8);
        if (withPolicy) {
            FileUtils.writeStringToFile(seed.resolve(TestRepositoryFiles.POLICY_FILE_NAME).toFile(), CUSTOM_POLICY, StandardCharsets.UTF_8);
        }
        FileUtils.writeStringToFile(seed.resolve("test/de/example/SortingTest.java").toFile(), TEST_CLASS, StandardCharsets.UTF_8);
        try (Git git = Git.init().setDirectory(seed.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Set up the tests repository").setAuthor("Artemis", "artemis@example.org").call();
        }
        FileUtils.deleteDirectory(remote.toFile());
        Git.cloneRepository().setURI(seed.toUri().toString()).setDirectory(remote.toFile()).setBare(true).call().close();
    }

    private static ProgrammingExercise exercise(ProgrammingLanguage language) {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getProgrammingLanguage()).thenReturn(language);
        when(exercise.getPackageName()).thenReturn(PACKAGE_NAME);
        when(exercise.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        return exercise;
    }

    /**
     * Pushes a change of the test class, as an instructor would through git.
     */
    private void pushTestClassChange(UnaryOperator<String> change) throws Exception {
        Path checkout = newDirectory("instructor-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            Path testFile = checkout.resolve("test/de/example/SortingTest.java");
            FileUtils.writeStringToFile(testFile.toFile(), change.apply(Files.readString(testFile)), StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Change the test class by hand").setAuthor("Instructor", "instructor@example.org").call();
            git.push().call();
        }
    }

    /** A change of an instructor's checkout that may throw, e.g. creating a symbolic link. */
    @FunctionalInterface
    private interface CheckoutChange {

        void apply(Path checkout) throws IOException;
    }

    /**
     * Pushes a change of the repository content, as an instructor would through git.
     */
    private void pushCheckoutChange(CheckoutChange change) throws Exception {
        pushCheckoutChange("Change the repository by hand", change);
    }

    /**
     * Pushes a change of the repository content with a commit message of its own, e.g. one that names a Security Framework action.
     */
    private void pushCheckoutChange(String message, CheckoutChange change) throws Exception {
        Path checkout = newDirectory("instructor-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            change.apply(checkout);
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage(message).setAuthor("Instructor", "instructor@example.org").call();
            git.push().call();
        }
    }

    private void pushPolicyAndVersion(String policy, String aresVersion) throws Exception {
        pushCheckoutChange(checkout -> {
            FileUtils.writeStringToFile(checkout.resolve(TestRepositoryFiles.POLICY_FILE_NAME).toFile(), policy, StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(checkout.resolve("build.gradle").toFile(), "ext {\n    aresVersion = '" + aresVersion + "'\n}\n", StandardCharsets.UTF_8);
        });
    }

    @Test
    void templateLikeRepositoryIsActiveByDefault() {
        SandboxConfigurationDTO config = configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA));

        assertThat(config.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(config.frameworkVersion()).isEqualTo("2.2.1");
    }

    @Test
    void aSourcePackageNamedBuildDecidesTheState() throws Exception {
        pushCheckoutChange(checkout -> FileUtils.writeStringToFile(checkout.resolve("test/de/example/build/BuildTest.java").toFile(),
                TEST_CLASS.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION).replace("package de.example;",
                        "package de.example.build;"),
                StandardCharsets.UTF_8));
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        // The test class in the package named build is scanned: its switched off policy is reported, while the other test class keeps the framework active.
        assertThat(configurationService.getConfiguration(exercise).disabledTestClasses()).containsExactly("de.example.build.SortingTest");
        assertThat(configurationService.getConfiguration(exercise).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void readingTheStateReadsThePushedStateOfTheBareRepository() throws Exception {
        configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA));

        verify(bareGitRepositoryService).getBareRepository(any(LocalVCRepositoryUri.class), eq("main"), eq(false));
    }

    @Test
    void aPolicyAresRefusesIsMisconfiguredOnlyForTheVerifiedVersion() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        pushPolicyAndVersion(PARTIAL_POLICY, "2.2.1");
        assertThat(configurationService.getConfiguration(exercise).status()).isEqualTo(SandboxStatus.MISCONFIGURED);

        pushPolicyAndVersion(PARTIAL_POLICY, "2.3.0");
        assertThat(configurationService.getConfiguration(exercise).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void aVersionIsNotVerifiedIfAPolicyFileCannotBeRead() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        pushPolicyAndVersion(CUSTOM_POLICY, "2.2.1");
        pushCheckoutChange(checkout -> FileUtils.writeStringToFile(checkout.resolve("test/de/example/NewTest.java").toFile(),
                NEW_TEST_CLASS.replace("\"SecurityPolicy.yaml\"", "\"MissingPolicy.yaml\""), StandardCharsets.UTF_8));

        // The readable file is accepted by 2.2.1, but the missing one cannot be checked.
        assertThat(configurationService.getConfiguration(exercise).versionCompatibilities())
                .contains(new SandboxConfigurationDTO.SandboxVersionCompatibilityDTO("2.2.1", CompatibilityVerdict.UNVERIFIED, List.of()));
    }

    @Test
    void templateVersionsAreReadFromTheTemplatesAndAlwaysSelectable() throws IOException {
        String gradleTemplateVersion = TestRepositoryFiles
                .parseAresVersion(Files.readString(MAIN_RESOURCES.resolve("templates/java/test/gradle/projectTemplate/build.gradle")), null).orElseThrow();

        SandboxFrameworkVersionsDTO versions = configurationService.getFrameworkVersions();

        assertThat(versions.templateVersions()).containsOnlyKeys(ProjectType.PLAIN_GRADLE, ProjectType.GRADLE_GRADLE, ProjectType.PLAIN_MAVEN, ProjectType.MAVEN_MAVEN)
                .containsEntry(ProjectType.PLAIN_GRADLE, gradleTemplateVersion);
        // All Java templates start with the same version, otherwise a new exercise would show a version that depends on the build tool.
        assertThat(versions.templateVersions().values()).containsOnly(gradleTemplateVersion);
        assertThat(versions.selectableVersions()).startsWith("2.3.0").contains(gradleTemplateVersion).doesNotHaveDuplicates();
        assertThat(createService().getFrameworkVersions().selectableVersions()).containsExactly(gradleTemplateVersion);
    }

    @Test
    void theTemplateTestsUseTheAnnotationThatIsToggled() throws IOException {
        assertThat(Files.readString(MAIN_RESOURCES.resolve("templates/java/test/testFiles/structural/ClassTest.java"))).contains(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION);
    }

    @Test
    void aPolicyBehindASymbolicLinkInACommitIsAnInvalidPathAndNotMissing() throws Exception {
        assumeTrue(FS.DETECTED.supportsSymlinks(), "The file system does not support symbolic links");
        pushCheckoutChange(checkout -> {
            FileUtils.writeStringToFile(checkout.resolve("test/de/example/SortingTest.java").toFile(),
                    TEST_CLASS.replace("value = \"SecurityPolicy.yaml\"", "value = \"policies/SecurityPolicy.yaml\""), StandardCharsets.UTF_8);
            Files.createSymbolicLink(checkout.resolve("policies"), checkout.resolve("test"));
        });

        SandboxConfigurationDTO config = configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA));

        assertThat(config.status()).isEqualTo(SandboxStatus.MISCONFIGURED);
        assertThat(config.policyFiles()).singleElement().satisfies(policyFile -> assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.INVALID_PATH));
    }

    @Test
    void anEmptyTestsRepositoryIsInactive() throws Exception {
        FileUtils.deleteDirectory(remote.toFile());
        Git.init().setBare(true).setDirectory(remote.toFile()).setInitialBranch("main").call().close();

        SandboxConfigurationDTO config = configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA));

        assertThat(config.status()).isEqualTo(SandboxStatus.INACTIVE);
        assertThat(config.frameworkVersion()).isNull();
    }

    @Test
    void anExerciseWithoutTestsRepositoryIsRejected() {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        when(exercise.getVcsTestRepositoryUri()).thenReturn(null);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> configurationService.getConfiguration(exercise))
                .matches(exception -> "sandboxNoTestRepository".equals(exception.getErrorKey()));
    }

    @Test
    void aPolicyWithAnotherFormatVersionNamesItAsIncompatible() throws Exception {
        pushPolicyAndVersion(CUSTOM_POLICY.replace("thisPolicyFileCompliesToThePolicyVersion: 1", "thisPolicyFileCompliesToThePolicyVersion: 2"), "2.2.1");

        assertThat(configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA)).versionCompatibilities())
                .contains(new SandboxConfigurationDTO.SandboxVersionCompatibilityDTO("2.2.1", CompatibilityVerdict.INCOMPATIBLE,
                        List.of(TestRepositoryFiles.POLICY_FILE_NAME + ": thisPolicyFileCompliesToThePolicyVersion 2")));
    }

    @ParameterizedTest
    @ValueSource(strings = { "notes.txt/SecurityPolicy.yaml", "policies" })
    void aPolicyPathThatIsNoFileInTheCommitIsAnInvalidPath(String policyPath) throws Exception {
        pushCheckoutChange(checkout -> {
            FileUtils.writeStringToFile(checkout.resolve("test/de/example/SortingTest.java").toFile(),
                    TEST_CLASS.replace("value = \"SecurityPolicy.yaml\"", "value = \"" + policyPath + "\""), StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(checkout.resolve("notes.txt").toFile(), "notes", StandardCharsets.UTF_8);
            FileUtils.writeStringToFile(checkout.resolve("policies/README.md").toFile(), "policies", StandardCharsets.UTF_8);
        });

        assertThat(configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA)).policyFiles()).singleElement()
                .satisfies(policyFile -> assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.INVALID_PATH));
    }

    @Test
    void aPolicyBelowAMissingDirectoryOrTooLargeInTheCommitIsReported() throws Exception {
        pushTestClassChange(source -> source.replace("value = \"SecurityPolicy.yaml\"", "value = \"policies/SecurityPolicy.yaml\""));
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        assertThat(configurationService.getConfiguration(exercise).policyFiles()).singleElement()
                .satisfies(policyFile -> assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.MISSING));

        pushCheckoutChange(checkout -> FileUtils.writeStringToFile(checkout.resolve("policies/SecurityPolicy.yaml").toFile(),
                CUSTOM_POLICY + "#".repeat(SecurityPolicyYaml.MAX_POLICY_SIZE), StandardCharsets.UTF_8));
        assertThat(configurationService.getConfiguration(exercise).policyFiles()).singleElement()
                .satisfies(policyFile -> assertThat(policyFile.problem()).isEqualTo(PolicyFileProblem.TOO_LARGE));
    }

    @Test
    void theBranchOfTheBuildConfigurationIsRead() throws Exception {
        // The configured branch switches the policy off, while the default branch keeps it on, so the status tells which branch was read.
        Path checkout = newDirectory("branch-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            git.checkout().setCreateBranch(true).setName("exercise-branch").call();
            FileUtils.writeStringToFile(checkout.resolve("test/de/example/SortingTest.java").toFile(),
                    TEST_CLASS.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION), StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Switch the policy off on another branch").setAuthor("Instructor", "instructor@example.org").call();
            git.push().setRefSpecs(new RefSpec("exercise-branch:exercise-branch")).call();
        }
        SandboxConfigurationService service = createServiceForBranch("exercise-branch", "2.2.1");

        assertThat(service.getConfiguration(exercise(ProgrammingLanguage.JAVA)).status()).isEqualTo(SandboxStatus.INACTIVE);
        assertThat(configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA)).status()).isEqualTo(SandboxStatus.CONFIGURED);
    }

    @Test
    void versionsOlderThanTheMinimumOrUnparseableAreNeverSelectableAndTheNewestComesFirst() {
        SandboxConfigurationService service = createService("2.1.0", " ", "2.2.10", "not-a-version", "2.3.0", "2.3.0");

        // The template version 2.2.1 is always selectable; blank and repeated entries are ignored like the versions Ares 2 does not support.
        assertThat(service.getFrameworkVersions().selectableVersions()).containsExactly("2.3.0", "2.2.10", "2.2.1");
        assertThat(SandboxConfigurationService.isAtLeastMinimumVersion("2.3.0-SNAPSHOT")).isTrue();
        assertThat(SandboxConfigurationService.isAtLeastMinimumVersion("2.2.1-SNAPSHOT")).isFalse();
        assertThat(SandboxConfigurationService.isAtLeastMinimumVersion("not-a-version")).isFalse();
    }

    @Test
    void unreadableTemplatesAndManifestLeaveOnlyTheConfiguredVersionsUnverified() {
        when(resourceLoaderService.getResource(any(Path.class))).thenReturn(new FileSystemResource(root.resolve("missing.yml")));
        SandboxConfigurationService service = createService("2.2.1");

        assertThat(service.getFrameworkVersions().templateVersions()).isEmpty();
        assertThat(service.getFrameworkVersions().selectableVersions()).containsExactly("2.2.1");
        // Without the manifest no release can be checked, so the version is reported as not verified instead of blocking it.
        assertThat(service.getConfiguration(exercise(ProgrammingLanguage.JAVA)).versionCompatibilities())
                .containsExactly(new SandboxConfigurationDTO.SandboxVersionCompatibilityDTO("2.2.1", CompatibilityVerdict.UNVERIFIED, List.of()));
    }

    @Test
    void aTestsRepositoryThatCannotBeReadFailsWithAnError() throws Exception {
        // The branch points to a commit the bare repository does not contain, e.g. after a broken copy of the repository.
        FileUtils.writeStringToFile(remote.resolve("refs/heads/main").toFile(), "0123456789abcdef0123456789abcdef01234567\n", StandardCharsets.UTF_8);

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA)))
                .matches(exception -> "sandboxReadFailed".equals(exception.getErrorKey()));
    }

    @Test
    void reportsThePartsOfAresTheTestsUseBesidesTheSandbox() throws Exception {
        String testImport = "import org.junit.jupiter.api.Test;\n";
        String strictTimeoutImport = testImport + "\nimport de.tum.cit.ase.ares.api.StrictTimeout;\n";
        pushTestClassChange(source -> source.replace(testImport, strictTimeoutImport).replace("class SortingTest {", "@StrictTimeout(5)\nclass SortingTest {"));

        SandboxConfigurationDTO config = configurationService.getConfiguration(exercise(ProgrammingLanguage.JAVA));

        assertThat(config.status()).isEqualTo(SandboxStatus.CONFIGURED);
        assertThat(config.aresApiUses()).containsExactly(new SandboxConfigurationDTO.SandboxAresApiUseDTO("test/de/example/SortingTest.java", "StrictTimeout"));
    }
}
