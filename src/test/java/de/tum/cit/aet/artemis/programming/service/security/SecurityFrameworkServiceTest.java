package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localvc.service.BareGitRepositoryService;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseBuildConfig;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;

/**
 * Tests {@link SecurityFrameworkService} against real git repositories: a bare repository stands in for the LocalVC tests repository, laid out like the
 * one of the Java Gradle template. Only the lookups of {@link GitService} and {@link BareGitRepositoryService} are replaced, so checkouts, commits and pushes
 * run for real and the assertions see exactly what an instructor would find in the repository.
 */
class SecurityFrameworkServiceTest {

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

    private GitService gitService;

    private BareGitRepositoryService bareGitRepositoryService;

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
        doAnswer(call -> {
            try (Git git = new Git(call.getArgument(0, Repository.class))) {
                String commit = GitService.commit(git).setMessage(call.getArgument(1, String.class)).setAuthor("Instructor", "instructor@example.org").call().getName();
                git.push().call();
                return commit;
            }
        }).when(gitService).commitAndPush(any(Repository.class), anyString(), anyBoolean(), any());

        bareGitRepositoryService = mock(BareGitRepositoryService.class);
        when(bareGitRepositoryService.getBareRepository(any(LocalVCRepositoryUri.class), anyString(), anyBoolean()))
                .thenAnswer(_ -> new Repository(new FileRepositoryBuilder().setGitDir(remote.toFile()).setBare().setup(), remote, mock(LocalVCRepositoryUri.class)));

        TempFileUtilService tempFileUtilService = mock(TempFileUtilService.class);
        when(tempFileUtilService.createTempDirectory(anyString())).thenAnswer(call -> Files.createTempDirectory(root, call.getArgument(0, String.class)));

        ProgrammingExerciseBuildConfigRepository buildConfigRepository = mock(ProgrammingExerciseBuildConfigRepository.class);
        when(buildConfigRepository.getProgrammingExerciseBuildConfigElseThrow(anyLong())).thenReturn(new ProgrammingExerciseBuildConfig());

        securityFrameworkService = new SecurityFrameworkService(gitService, bareGitRepositoryService, tempFileUtilService, buildConfigRepository, "main",
                new String[] { "2.3.0", "2.2.1" });
    }

    /**
     * Creates the bare tests repository with a single commit, laid out like the tests repository of the Java Gradle template.
     */
    private void initRemote(boolean withPolicy) throws Exception {
        Path seed = Files.createTempDirectory(root, "seed");
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
        assertThat(config.lastCommitHash()).hasSize(7);
        assertThat(config.lastSyncedAt()).isNotBlank();
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
                content -> assertThat(content).contains(SecurityPolicyRepositoryFiles.INACTIVE_ANNOTATION).doesNotContain(SecurityPolicyRepositoryFiles.ACTIVE_ANNOTATION));
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
        verify(gitService, never()).commitAndPush(any(Repository.class), anyString(), anyBoolean(), any());
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
    void defaultFrameworkVersionMatchesTheJavaTemplates() throws IOException {
        Path templates = Path.of("src", "main", "resources", "templates", "java");
        String expected = SecurityFrameworkService.DEFAULT_FRAMEWORK_VERSION;
        assertThat(SecurityPolicyRepositoryFiles.readAresVersion(templates.resolve("test/gradle/projectTemplate"))).contains(expected);
        assertThat(SecurityPolicyRepositoryFiles.readAresVersion(templates.resolve("test/maven/projectTemplate"))).contains(expected);
        assertThat(SecurityPolicyRepositoryFiles.readAresVersion(templates.resolve("maven_maven/test/projectTemplate"))).contains(expected);
        // Toggling relies on the annotation the template tests use.
        assertThat(Files.readString(templates.resolve("test/testFiles/structural/ClassTest.java"))).contains(SecurityPolicyRepositoryFiles.ACTIVE_ANNOTATION);
    }
}
