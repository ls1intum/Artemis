package de.tum.cit.aet.artemis.programming.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.localvc.service.GitService;
import de.tum.cit.aet.artemis.localvc.service.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.security.SecurityActivationStatus;
import de.tum.cit.aet.artemis.programming.dto.SecurityFrameworkConfigDTO;

/**
 * Tests {@link SecurityFrameworkService} against a real git repository laid out like the tests repository of the Java Gradle template. Only the
 * checkout and the push of {@link GitService} are replaced: staging and committing run against the repository, so the assertions see exactly the
 * files and commits an instructor would see.
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

    private GitService gitService;

    private Repository repository;

    private SecurityFrameworkService securityFrameworkService;

    private final User user = new User();

    @BeforeEach
    void setUp() throws Exception {
        initRepository(true);

        gitService = mock(GitService.class);
        when(gitService.getOrCheckoutRepository(any(LocalVCRepositoryUri.class), anyBoolean(), anyBoolean())).thenAnswer(_ -> repository);
        doAnswer(_ -> {
            try (Git git = new Git(repository)) {
                Status status = git.status().call();
                git.add().setUpdate(true).addFilepattern(".").call();
                git.add().addFilepattern(".").call();
                return !status.isClean();
            }
        }).when(gitService).stageAllChanges(any(Repository.class));
        doAnswer(call -> {
            try (Git git = new Git(repository)) {
                return GitService.commit(git).setMessage(call.getArgument(1, String.class)).setAuthor("Instructor", "instructor@example.org").call().getName();
            }
        }).when(gitService).commitAndPush(any(Repository.class), anyString(), anyBoolean(), any());

        securityFrameworkService = new SecurityFrameworkService(gitService, new String[] { "2.3.0", "2.2.1" });
    }

    /**
     * Creates the repository with a single commit, laid out like the tests repository of the Java Gradle template.
     */
    private void initRepository(boolean withPolicy) throws Exception {
        Files.writeString(root.resolve("build.gradle"), "ext {\n    aresVersion = '2.2.1'\n}\n");
        if (withPolicy) {
            Files.writeString(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME), CUSTOM_POLICY);
        }
        Path testFile = root.resolve("test/de/example/SortingTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, TEST_CLASS);
        try (Git git = Git.init().setDirectory(root.toFile()).setInitialBranch("main").call()) {
            git.add().addFilepattern(".").call();
            GitService.commit(git).setMessage("Set up the tests repository").setAuthor("Artemis", "artemis@example.org").call();
        }
        repository = new Repository(new FileRepositoryBuilder().setGitDir(root.resolve(".git").toFile()).setWorkTree(root.toFile()).setup(), root,
                mock(LocalVCRepositoryUri.class));
    }

    private static ProgrammingExercise exercise(ProgrammingLanguage language) {
        ProgrammingExercise exercise = mock(ProgrammingExercise.class);
        when(exercise.getId()).thenReturn(1L);
        when(exercise.getProgrammingLanguage()).thenReturn(language);
        when(exercise.getPackageName()).thenReturn(PACKAGE_NAME);
        when(exercise.getVcsTestRepositoryUri()).thenReturn(mock(LocalVCRepositoryUri.class));
        return exercise;
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(root.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private int commitCount() throws Exception {
        try (Git git = new Git(repository)) {
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
    void deactivateRemovesThePolicyAndDisablesAres() throws Exception {
        SecurityFrameworkConfigDTO config = securityFrameworkService.deactivate(exercise(ProgrammingLanguage.JAVA), user);

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        assertThat(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).doesNotExist();
        assertThat(read("test/de/example/SortingTest.java")).contains(SecurityPolicyRepositoryFiles.INACTIVE_ANNOTATION)
                .doesNotContain(SecurityPolicyRepositoryFiles.ACTIVE_ANNOTATION);
        assertThat(commitCount()).isEqualTo(2);
        try (Git git = new Git(repository)) {
            assertThat(git.status().call().isClean()).isTrue();
        }
    }

    @Test
    void reactivationRestoresThePreviousPolicy() throws Exception {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.JAVA);
        securityFrameworkService.deactivate(exercise, user);

        SecurityFrameworkConfigDTO config = securityFrameworkService.activate(exercise, "2.3.0", user);

        assertThat(config.status()).isEqualTo(SecurityActivationStatus.ACTIVE.name());
        assertThat(config.frameworkVersion()).isEqualTo("2.3.0");
        assertThat(read(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).isEqualTo(CUSTOM_POLICY);
        assertThat(read("test/de/example/SortingTest.java")).isEqualTo(TEST_CLASS);
        assertThat(read("build.gradle")).contains("aresVersion = '2.3.0'");
        assertThat(commitCount()).isEqualTo(3);
    }

    @Test
    void activationGeneratesADefaultPolicyIfTheRepositoryNeverHadOne() throws Exception {
        repository.close();
        FileUtils.deleteDirectory(root.resolve(".git").toFile());
        Files.delete(root.resolve(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME));
        initRepository(false);

        securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "2.2.1", user);

        assertThat(read(SecurityPolicyRepositoryFiles.POLICY_FILE_NAME)).contains("JAVA_USING_GRADLE_ARCHUNIT_AND_ASPECTJ")
                .contains("theSupervisedCodeUsesTheFollowingPackage: \"de.example\"").contains("- \"de.example.SortingTest\"").contains("regardingFileSystemInteractions: [ ]");
    }

    @Test
    void activatingAnActiveExerciseWithTheSameVersionCreatesNoCommit() throws Exception {
        securityFrameworkService.activate(exercise(ProgrammingLanguage.JAVA), "2.2.1", user);

        assertThat(commitCount()).isEqualTo(1);
        verify(gitService, never()).commitAndPush(any(Repository.class), anyString(), anyBoolean(), any());
    }

    @Test
    void updateFrameworkVersionPinsTheVersionInTheBuildFile() throws Exception {
        SecurityFrameworkConfigDTO config = securityFrameworkService.updateFrameworkVersion(exercise(ProgrammingLanguage.JAVA), "2.3.0", user);

        assertThat(config.frameworkVersion()).isEqualTo("2.3.0");
        assertThat(read("build.gradle")).contains("aresVersion = '2.3.0'");
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
        verifyNoInteractions(gitService);
    }

    @Test
    void nonJavaExercisesAreRejectedBeforeTouchingTheRepository() {
        ProgrammingExercise exercise = exercise(ProgrammingLanguage.PYTHON);

        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.activate(exercise, "2.2.1", user))
                .matches(exception -> "onlyJavaSupported".equals(exception.getErrorKey()));
        assertThatExceptionOfType(BadRequestAlertException.class).isThrownBy(() -> securityFrameworkService.deactivate(exercise, user))
                .matches(exception -> "onlyJavaSupported".equals(exception.getErrorKey()));
        assertThat(securityFrameworkService.getConfig(exercise).status()).isEqualTo(SecurityActivationStatus.INACTIVE.name());
        verifyNoInteractions(gitService);
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
