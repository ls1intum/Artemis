package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import org.apache.commons.io.FileUtils;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefLeaseSpec;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorAlertException;
import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.localvc.service.LocalVCServletService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.SolutionProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkVersionsDTO;
import de.tum.cit.aet.artemis.programming.exception.VersionControlException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;

/**
 * Tests {@link SecurityFrameworkService} against real git repositories: a bare repository stands in for the LocalVC tests repository, laid out like the
 * one of the Java Gradle template. Only the lookups of {@link GitService} and {@link BareGitRepositoryService} are replaced, so checkouts, commits and pushes
 * run for real and the assertions see exactly what an instructor would find in the repository.
 */
class SecurityFrameworkServiceTest {

    private static final long SOLUTION_PARTICIPATION_ID = 5L;

    private static final Path MAIN_RESOURCES = Path.of("src", "main", "resources");

    private static final String PACKAGE_NAME = "de.example";

    private static final String CUSTOM_POLICY = """
            thisPolicyFileCompliesToThePolicyVersion: 1
            regardingTheSupervisedCode:
              theFollowingProgrammingLanguageConfigurationIsUsed: JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ
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

    private static final String TEST_CLASS = """
            package de.example;

            import org.junit.jupiter.api.Test;

            // The fully qualified name is required, because the exercise itself contains a class named Policy
            @de.tum.cit.ase.ares.api.Policy(value = "SecurityPolicy.yaml")
            class SortingTest {

                @Test
                void sorts() {
                }
            }
            """;

    @TempDir
    private Path root;

    private Path remote;

    private final AtomicInteger directoryCounter = new AtomicInteger();

    /** Simulates someone else, e.g. the online editor, pushing to the tests repository while a change is being prepared. */
    private boolean pushConcurrentlyAfterCheckout;

    /** Runs once after the next checkout, e.g. another instructor's change that is pushed while this checkout is being prepared. */
    private Runnable afterNextCheckout;

    private GitService gitService;

    private BareGitRepositoryService bareGitRepositoryService;

    private TempFileUtilService tempFileUtilService;

    private ResourceLoaderService resourceLoaderService;

    private LocalVCServletService localVCServletService;

    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    private LocalDataProviderService distributedDataProvider;

    private SecurityFrameworkService securityFrameworkService;

    private final User user = new User();

    @BeforeEach
    void setUp() throws Exception {
        remote = root.resolve("remote.git");
        initRemote(true);

        gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepositoryOnBranch(any(LocalVCRepositoryUri.class), any(Path.class), anyString())).thenAnswer(call -> {
            Path localPath = call.getArgument(1, Path.class);
            Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(localPath.toFile()).setBranch(call.getArgument(2, String.class)).call().close();
            if (pushConcurrentlyAfterCheckout) {
                pushUnrelatedCommit();
            }
            Runnable hook = afterNextCheckout;
            afterNextCheckout = null;
            if (hook != null) {
                hook.run();
            }
            return new Repository(new FileRepositoryBuilder().setGitDir(localPath.resolve(".git").toFile()).setWorkTree(localPath.toFile()).setup(), localPath,
                    mock(LocalVCRepositoryUri.class));
        });
        doAnswer(call -> {
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                Status status = git.status().call();
                git.add().setUpdate(true).addFilepattern(".").call();
                git.add().addFilepattern(".").call();
                return !status.isClean();
            }
        }).when(gitService).stageAllChanges(any(Repository.class));
        when(gitService.getLocalHeadHash(any(Repository.class))).thenAnswer(call -> call.getArgument(0, Repository.class).resolve("HEAD").name());
        when(gitService.commitStagedChanges(any(Repository.class), anyString(), any())).thenAnswer(call -> {
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                return GitService.commit(git).setMessage(call.getArgument(1, String.class)).setAuthor("Instructor", "instructor@example.org").call().getName();
            }
        });
        doAnswer(call -> {
            String remoteRef = "refs/heads/" + call.getArgument(2, String.class);
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                for (PushResult result : git.push().setRefSpecs(new RefSpec(call.getArgument(1, String.class) + ":" + remoteRef))
                        .setRefLeaseSpecs(new RefLeaseSpec(remoteRef, call.getArgument(3, String.class))).call()) {
                    for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                        if (update.getStatus() != RemoteRefUpdate.Status.OK && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                            throw new TransportException("Push rejected: " + update.getStatus());
                        }
                    }
                }
            }
            return null;
        }).when(gitService).pushCommitWithLease(any(Repository.class), anyString(), anyString(), anyString());

        bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(LocalVCRepositoryUri.class), anyString(), anyBoolean()))
                .thenAnswer(_ -> new Repository(new FileRepositoryBuilder().setGitDir(remote.toFile()).setBare().setup(), remote, mock(LocalVCRepositoryUri.class)));

        tempFileUtilService = mock(TempFileUtilService.class);
        when(tempFileUtilService.createTempDirectory(anyString())).thenAnswer(call -> newDirectory(call.getArgument(0, String.class)));

        resourceLoaderService = mock(ResourceLoaderService.class);
        when(resourceLoaderService.getResource(any(Path.class))).thenAnswer(call -> new FileSystemResource(MAIN_RESOURCES.resolve(call.getArgument(0, Path.class))));
        localVCServletService = mock(LocalVCServletService.class);
        programmingSubmissionRepository = mock(ProgrammingSubmissionTestRepository.class);
        distributedDataProvider = new LocalDataProviderService();

        securityFrameworkService = createService("2.3.0", "2.2.1");
    }

    private SecurityFrameworkService createService(String... configuredVersions) {
        ProgrammingExerciseBuildConfigRepository buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(anyLong())).thenReturn(new ProgrammingExerciseBuildConfig());
        SolutionProgrammingExerciseParticipation solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.setId(SOLUTION_PARTICIPATION_ID);
        SolutionProgrammingExerciseParticipationRepository solutionParticipationRepository = mock(SolutionProgrammingExerciseParticipationRepository.class);
        when(solutionParticipationRepository.findByProgrammingExerciseId(anyLong())).thenReturn(Optional.of(solutionParticipation));
        return new SecurityFrameworkService(gitService, bareGitRepositoryService, tempFileUtilService, buildConfigRepository, resourceLoaderService,
                Optional.of(localVCServletService), solutionParticipationRepository, programmingSubmissionRepository, distributedDataProvider, "main", configuredVersions);
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
            FileUtils.writeStringToFile(seed.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).toFile(), CUSTOM_POLICY, StandardCharsets.UTF_8);
        }
        FileUtils.writeStringToFile(seed.resolve("test/de/example/SortingTest.java").toFile(), TEST_CLASS, StandardCharsets.UTF_8);
        try (Git git = Git.init().setDirectory(seed.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Set up the tests repository").setAuthor("Artemis", "artemis@example.org").call();
        }
        FileUtils.deleteDirectory(remote.toFile());
        Git.cloneRepository().setURI(seed.toUri().toString()).setDirectory(remote.toFile()).setBare(true).call().close();
    }

    private void pushUnrelatedCommit() throws Exception {
        Path otherCheckout = newDirectory("other-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(otherCheckout.toFile()).call()) {
            FileUtils.writeStringToFile(otherCheckout.resolve("README.md").toFile(), "Edited in the online editor", StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Concurrent change").setAuthor("Editor", "editor@example.org").call();
            git.push().call();
        }
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
     * @return the content of the file in the pushed state of the tests repository, if it exists there
     */
    private Optional<String> readPushed(String path) throws IOException {
        try (var repository = new FileRepositoryBuilder().setGitDir(remote.toFile()).setBare().build(); RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve("refs/heads/main");
            try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, revWalk.parseCommit(head).getTree())) {
                return treeWalk == null ? Optional.empty() : Optional.of(new String(repository.open(treeWalk.getObjectId(0)).getBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private int pushedCommitCount() throws Exception {
        try (Git git = Git.open(remote.toFile())) {
            int count = 0;
            for (var ignored : git.log().call()) {
                count++;
            }
            return count;
        }
    }

    @Test
    void templateLikeRepositoryIsActiveByDefault() {
        SecurityFrameworkConfigDTO config = securityFrameworkService.getConfig(exercise(ProgrammingLanguage.JAVA));

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(config.frameworkVersion()).isEqualTo("2.2.1");
    }

    @Test
    void aTestThatSwitchesItsPolicyOffMakesTheFrameworkInactive() throws Exception {
        Path checkout = newDirectory("instructor-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            Path testFile = checkout.resolve("test/de/example/SortingTest.java");
            FileUtils.writeStringToFile(testFile.toFile(),
                    Files.readString(testFile).replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION), StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Switch the policy off by hand").setAuthor("Instructor", "instructor@example.org").call();
            git.push().call();
        }

        assertThat(securityFrameworkService.getConfig(exercise(ProgrammingLanguage.JAVA)).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());

        SecurityFrameworkConfigDTO config = securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "2.2.1", user);
        assertThat(config.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(readPushed("test/de/example/SortingTest.java")).contains(TEST_CLASS);
    }

    @Test
    void updateFrameworkVersionIsRejectedWhileATestSwitchesItsPolicyOff() throws Exception {
        Path checkout = newDirectory("instructor-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            Path testFile = checkout.resolve("test/de/example/SortingTest.java");
            FileUtils.writeStringToFile(testFile.toFile(),
                    Files.readString(testFile).replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION), StandardCharsets.UTF_8);
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Switch the policy off by hand").setAuthor("Instructor", "instructor@example.org").call();
            git.push().call();
        }

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> securityFrameworkService.updateFrameworkVersion(exercise(ProgrammingLanguage.JAVA), "2.3.0", user))
                .matches(exception -> "securityFrameworkNotActive".equals(exception.getErrorKey()));
        assertThat(readPushed("build.gradle")).hasValueSatisfying(content -> assertThat(content).contains("aresVersion = '2.2.1'"));
    }

    @Test
    void anInstructorsOwnPolicyFileIsKeptAndNeverReplacedBySecurityPolicyYaml() throws Exception {
        Path checkout = newDirectory("instructor-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            Path testFile = checkout.resolve("test/de/example/SortingTest.java");
            FileUtils.writeStringToFile(testFile.toFile(), Files.readString(testFile).replace("SecurityPolicy.yaml", "custom.yaml"), StandardCharsets.UTF_8);
            FileUtils.moveFile(checkout.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME).toFile(), checkout.resolve("custom.yaml").toFile());
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Use an own policy file").setAuthor("Instructor", "instructor@example.org").call();
            git.push().call();
        }
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());

        assertThat(securityFrameworkService.deactivate(exercise, user).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        assertThat(readPushed("test/de/example/SortingTest.java")).hasValueSatisfying(content -> assertThat(content).contains("value = \"custom.yaml\", activated = false"));

        assertThat(securityFrameworkService.activate(exercise, "2.2.1", user).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(readPushed("test/de/example/SortingTest.java")).hasValueSatisfying(content -> assertThat(content).contains("value = \"custom.yaml\")"));
        assertThat(readPushed("custom.yaml")).contains(CUSTOM_POLICY);
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).isEmpty();
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

    @Test
    void aParenthesizedActivatedFlagMakesTheFrameworkInactive() throws Exception {
        String parenthesized = "@de.tum.cit.ase.ares.api.Policy(value = \"SecurityPolicy.yaml\", activated = (false))";
        pushTestClassChange(source -> source.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, parenthesized));
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());

        assertThat(securityFrameworkService.activate(exercise, "2.2.1", user).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(readPushed("test/de/example/SortingTest.java")).contains(TEST_CLASS);
    }

    @Test
    void aRelativePathToTheRootPolicySurvivesADeactivateActivateCycle() throws Exception {
        pushTestClassChange(source -> source.replace("value = \"SecurityPolicy.yaml\"", "value = \"./SecurityPolicy.yaml\""));
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());

        assertThat(securityFrameworkService.deactivate(exercise, user).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).isEmpty();

        assertThat(securityFrameworkService.activate(exercise, "2.2.1", user).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains(CUSTOM_POLICY);
        assertThat(readPushed("test/de/example/SortingTest.java"))
                .hasValueSatisfying(content -> assertThat(content).contains("@de.tum.cit.ase.ares.api.Policy(value = \"./SecurityPolicy.yaml\")"));
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
        Path checkout = newDirectory("instructor-");
        try (Git git = Git.cloneRepository().setURI(remote.toUri().toString()).setDirectory(checkout.toFile()).call()) {
            change.apply(checkout);
            git.add().setUpdate(true).addFilepattern(".").call();
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Change the repository by hand").setAuthor("Instructor", "instructor@example.org").call();
            git.push().call();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "test/de/example/OtherExerciseTest.java", SecurityPolicyRepositoryFiles.POLICY_FILE_NAME, "test/de/linked" })
    void aRepositoryWithSymbolicLinksIsNeverChanged(String link) throws Exception {
        assumeTrue(FS.DETECTED.supportsSymlinks(), "The file system does not support symbolic links");
        Path outside = newDirectory("other-exercise-");
        Path externalTest = outside.resolve("OtherExerciseTest.java");
        String externalContent = TEST_CLASS.replace("SortingTest", "OtherExerciseTest");
        FileUtils.writeStringToFile(externalTest.toFile(), externalContent, StandardCharsets.UTF_8);
        pushCheckoutChange(checkout -> {
            Path linkPath = checkout.resolve(link);
            Files.deleteIfExists(linkPath);
            Files.createDirectories(linkPath.getParent());
            Files.createSymbolicLink(linkPath, link.endsWith(".java") || link.endsWith(".yaml") ? externalTest : outside);
        });
        String headBefore = pushedHead();
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise, user))
                .matches(exception -> "securityFrameworkSymbolicLinks".equals(exception.getErrorKey()));
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.activate(exercise, "2.3.0", user))
                .matches(exception -> "securityFrameworkSymbolicLinks".equals(exception.getErrorKey()));

        assertThat(Files.readString(externalTest)).isEqualTo(externalContent);
        assertThat(outside.toFile().list()).containsExactly("OtherExerciseTest.java");
        assertThat(pushedHead()).isEqualTo(headBefore);
        verifyNoInteractions(localVCServletService);
    }

    @Test
    void committedBuildOutputDoesNotDecideTheState() throws Exception {
        // The root of the tests repository has a build.gradle, so build/ is build output there, even though it was committed by mistake.
        pushCheckoutChange(checkout -> FileUtils.writeStringToFile(checkout.resolve("build/generated/de/example/GeneratedTest.java").toFile(),
                TEST_CLASS.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION), StandardCharsets.UTF_8));
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(securityFrameworkService.updateFrameworkVersion(exercise, "2.3.0", user).status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(readPushed("build.gradle")).hasValueSatisfying(content -> assertThat(content).contains("aresVersion = '2.3.0'"));
    }

    @Test
    void aSourcePackageNamedBuildDecidesTheState() throws Exception {
        pushCheckoutChange(checkout -> FileUtils.writeStringToFile(checkout.resolve("test/de/example/build/BuildTest.java").toFile(),
                TEST_CLASS.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION).replace("package de.example;",
                        "package de.example.build;"),
                StandardCharsets.UTF_8));
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.updateFrameworkVersion(exercise, "2.3.0", user))
                .matches(exception -> "securityFrameworkNotActive".equals(exception.getErrorKey()));
    }

    @ParameterizedTest
    @ValueSource(strings = { "value = \"Security\" + \"Policy.yaml\"", "value = \"SecurityPolicy.yaml\", activated = !true" })
    void aRepositoryWithAnnotationsThatCannotBeEvaluatedIsNeitherReportedNorChanged(String arguments) throws Exception {
        pushTestClassChange(source -> source.replace(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION, "@de.tum.cit.ase.ares.api.Policy(" + arguments + ")"));
        String headBefore = pushedHead();
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);

        assertRejectedAsUnevaluable(() -> securityFrameworkService.getConfig(exercise));
        assertRejectedAsUnevaluable(() -> securityFrameworkService.activate(exercise, "2.2.1", user));
        assertRejectedAsUnevaluable(() -> securityFrameworkService.deactivate(exercise, user));
        assertRejectedAsUnevaluable(() -> securityFrameworkService.updateFrameworkVersion(exercise, "2.3.0", user));

        assertThat(pushedHead()).isEqualTo(headBefore);
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains(CUSTOM_POLICY);
        verifyNoInteractions(localVCServletService);
    }

    private static void assertRejectedAsUnevaluable(ThrowingCallable call) {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(call)
                .matches(exception -> "securityFrameworkUnevaluableAnnotation".equals(exception.getErrorKey()) && exception.getMessage().contains("SortingTest.java"));
    }

    @Test
    void readingTheStateNeverTouchesAWorkingCopy() throws Exception {
        securityFrameworkService.getConfig(exercise(ProgrammingLanguage.JAVA));

        verifyNoInteractions(gitService);
    }

    @Test
    void deactivateRemovesThePolicyAndDisablesAres() throws Exception {
        SecurityFrameworkConfigDTO config = securityFrameworkService.deactivate(exercise(ProgrammingLanguage.JAVA), user);

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).isEmpty();
        assertThat(readPushed("test/de/example/SortingTest.java")).hasValueSatisfying(
                content -> assertThat(content).contains(SecurityPolicyTestAnnotations.INACTIVE_ANNOTATION).doesNotContain(SecurityPolicyTestAnnotations.ACTIVE_ANNOTATION));
        assertThat(pushedCommitCount()).isEqualTo(2);
        assertThat(securityFrameworkService.getConfig(exercise(ProgrammingLanguage.JAVA)).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
    }

    @Test
    void reactivationRestoresThePreviousPolicy() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        securityFrameworkService.deactivate(exercise, user);

        SecurityFrameworkConfigDTO config = securityFrameworkService.activate(exercise, "2.3.0", user);

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(config.frameworkVersion()).isEqualTo("2.3.0");
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains(CUSTOM_POLICY);
        assertThat(readPushed("test/de/example/SortingTest.java")).contains(TEST_CLASS);
        assertThat(readPushed("build.gradle")).hasValueSatisfying(content -> assertThat(content).contains("aresVersion = '2.3.0'"));
        assertThat(pushedCommitCount()).isEqualTo(3);
    }

    @Test
    void activationGeneratesADefaultPolicyIfTheRepositoryNeverHadOne() throws Exception {
        initRemote(false);

        securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "2.2.1", user);

        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).hasValueSatisfying(policy -> assertThat(policy).contains("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ")
                .contains("theSupervisedCodeUsesTheFollowingPackage: \"de.example\"").contains("- \"de.example.SortingTest\"").contains("regardingFileSystemInteractions: [ ]"));
    }

    @Test
    void activatingAnActiveExerciseWithTheSameVersionCreatesNoCommit() throws Exception {
        securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "2.2.1", user);

        assertThat(pushedCommitCount()).isEqualTo(1);
        verify(gitService, never()).pushCommitWithLease(any(Repository.class), anyString(), anyString(), anyString());
    }

    @Test
    void aConcurrentPushIsNeitherOverwrittenNorReportedAsApplied() throws Exception {
        pushConcurrentlyAfterCheckout = true;

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise(ProgrammingLanguage.JAVA), user))
                .matches(exception -> "securityFrameworkPushFailed".equals(exception.getErrorKey()));
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains(CUSTOM_POLICY);
        assertThat(readPushed("README.md")).contains("Edited in the online editor");
    }

    @Test
    void temporaryCheckoutsAreDeleted() throws Exception {
        securityFrameworkService.deactivate(exercise(ProgrammingLanguage.JAVA), user);

        try (var entries = Files.list(root)) {
            assertThat(entries.map(path -> path.getFileName().toString())).noneMatch(name -> name.startsWith("security-framework-"));
        }
    }

    @Test
    void updateFrameworkVersionPinsTheVersionInTheBuildFile() throws Exception {
        SecurityFrameworkConfigDTO config = securityFrameworkService.updateFrameworkVersion(exercise(ProgrammingLanguage.JAVA), "2.3.0", user);

        assertThat(config.frameworkVersion()).isEqualTo("2.3.0");
        assertThat(readPushed("build.gradle")).hasValueSatisfying(content -> assertThat(content).contains("aresVersion = '2.3.0'"));
    }

    @Test
    void updateFrameworkVersionIsRejectedWhileInactive() {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        securityFrameworkService.deactivate(exercise, user);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.updateFrameworkVersion(exercise, "2.3.0", user))
                .matches(exception -> "securityFrameworkNotActive".equals(exception.getErrorKey()));
    }

    @Test
    void unsupportedFrameworkVersionIsRejectedBeforeTouchingTheRepository() {
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "9.9.9", user))
                .matches(exception -> "unsupportedFrameworkVersion".equals(exception.getErrorKey()));
        verifyNoInteractions(gitService, bareGitRepositoryService);
    }

    @Test
    void nonJavaExercisesAreRejectedBeforeTouchingTheRepository() {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.PYTHON);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.activate(exercise, "2.2.1", user))
                .matches(exception -> "onlyJavaSupported".equals(exception.getErrorKey()));
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise, user))
                .matches(exception -> "onlyJavaSupported".equals(exception.getErrorKey()));
        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        verifyNoInteractions(gitService, bareGitRepositoryService);
    }

    @Test
    void templateVersionsAreReadFromTheTemplatesAndAlwaysSelectable() {
        String gradleTemplateVersion = SecurityPolicyRepositoryFiles.readAresVersion(MAIN_RESOURCES.resolve("templates/java/test/gradle/projectTemplate")).orElseThrow();

        SecurityFrameworkVersionsDTO versions = securityFrameworkService.getFrameworkVersions();

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
    void aChangeRunsThePushProcessingOfTheTestsRepository() throws Exception {
        securityFrameworkService.deactivate(exercise(ProgrammingLanguage.JAVA), user);

        String pushedHead = pushedHead();
        verify(localVCServletService).processNewPush(eq(pushedHead), any(Repository.class), eq(user), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()));
    }

    @Test
    void aRetryResumesTheProcessingOfAChangeWhoseProcessingFailed() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        doThrow(new VersionControlException("Build trigger failed")).when(localVCServletService).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise, user))
                .matches(exception -> "securityFrameworkProcessingFailed".equals(exception.getErrorKey()));
        String pushedHead = pushedHead();
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).isEmpty();

        // The retry finds nothing left to change, but the pushed change has not been processed yet.
        doNothing().when(localVCServletService).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());
        SecurityFrameworkConfigDTO config = securityFrameworkService.deactivate(exercise, user);

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        assertThat(pushedHead()).isEqualTo(pushedHead);
        verify(localVCServletService, times(2)).processNewPush(eq(pushedHead), any(Repository.class), eq(user), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()));
    }

    @Test
    void aRetryResumesAProcessingThatFailedAfterSavingTheSubmission() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        // The processing saves the solution submission and then fails, e.g. while triggering the builds.
        Set<String> submittedCommits = ConcurrentHashMap.newKeySet();
        when(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(eq(SOLUTION_PARTICIPATION_ID), anyString()))
                .thenAnswer(call -> submittedCommits.contains(call.getArgument(1, String.class)));
        doAnswer(call -> {
            submittedCommits.add(call.getArgument(0, String.class));
            throw new VersionControlException("Build trigger failed");
        }).when(localVCServletService).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());

        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise, user))
                .matches(exception -> "securityFrameworkProcessingFailed".equals(exception.getErrorKey()));
        String pushedHead = pushedHead();
        assertThat(submittedCommits).containsExactly(pushedHead);

        // Although the submission exists, the processing did not complete, so the retry resumes it.
        doNothing().when(localVCServletService).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());
        securityFrameworkService.deactivate(exercise, user);
        verify(localVCServletService, times(2)).processNewPush(eq(pushedHead), any(Repository.class), eq(user), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()));

        // Once the processing has completed, a further retry does not process the change again.
        securityFrameworkService.deactivate(exercise, user);
        verify(localVCServletService, times(2)).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());
    }

    @Test
    void aRejectedCompetingChangeKeepsTheRetryOfThePushedChange() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        Set<String> submittedCommits = ConcurrentHashMap.newKeySet();
        when(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(eq(SOLUTION_PARTICIPATION_ID), anyString()))
                .thenAnswer(call -> submittedCommits.contains(call.getArgument(1, String.class)));
        AtomicBoolean failProcessing = new AtomicBoolean(true);
        doAnswer(call -> {
            submittedCommits.add(call.getArgument(0, String.class));
            if (failProcessing.get()) {
                throw new VersionControlException("Build trigger failed");
            }
            return null;
        }).when(localVCServletService).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());

        // Instructor B checks out the repository. Before B pushes, instructor A pushes a change whose processing fails after saving the submission.
        afterNextCheckout = () -> assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise, user))
                .matches(exception -> "securityFrameworkProcessingFailed".equals(exception.getErrorKey()));
        // B's push is rejected, because its checkout no longer matches the repository.
        assertThatExceptionOfType(InternalServerErrorAlertException.class).isThrownBy(() -> securityFrameworkService.updateFrameworkVersion(exercise, "2.3.0", user))
                .matches(exception -> "securityFrameworkPushFailed".equals(exception.getErrorKey()));
        String pushedHead = pushedHead();
        assertThat(readPushed(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).isEmpty();
        assertThat(submittedCommits).containsExactly(pushedHead);

        // The rejected change of B has not replaced the pending entry of A, so the retry resumes the processing of A.
        failProcessing.set(false);
        securityFrameworkService.deactivate(exercise, user);
        verify(localVCServletService, times(2)).processNewPush(eq(pushedHead), any(Repository.class), eq(user), eq(Optional.empty()), eq(Optional.empty()), eq(Optional.empty()));
        verify(localVCServletService, times(2)).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());
    }

    @Test
    void aRepeatedActionDoesNotProcessAnAlreadyProcessedChangeAgain() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        securityFrameworkService.deactivate(exercise, user);
        when(programmingSubmissionRepository.existsByParticipationIdAndCommitHash(SOLUTION_PARTICIPATION_ID, pushedHead())).thenReturn(true);

        securityFrameworkService.deactivate(exercise, user);

        verify(localVCServletService, times(1)).processNewPush(anyString(), any(Repository.class), any(), any(), any(), any());
    }

    private String pushedHead() throws IOException {
        try (Git git = Git.open(remote.toFile())) {
            return git.getRepository().resolve("refs/heads/main").name();
        }
    }

    @Test
    void anUnchangedRepositoryIsNotProcessed() {
        securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "2.2.1", user);

        verifyNoInteractions(localVCServletService);
    }
}
