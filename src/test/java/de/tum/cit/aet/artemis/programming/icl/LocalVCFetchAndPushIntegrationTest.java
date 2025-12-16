package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/**
 * Integration tests for LocalVC fetch and push operations.
 * Tests verify that fetching and pushing changes is possible for authorized users in:
 * - Template, tests, solution, aux repos (only editors and instructors)
 * - Student repos (only students with access to the repo + TAs and above)
 * <p>
 * This test class does NOT mock git functionality and does NOT use LocalRepository.
 * Programming exercises are created via REST API which automatically creates repositories.
 * Docker/LocalCI execution is mocked to avoid lengthy tests.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class LocalVCFetchAndPushIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localvcfetchpush";

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    private Course course;

    private User student1;

    private User student2;

    private User tutor1;

    private User editor1;

    private User instructor1;

    // Store cloned repository paths for cleanup
    private final java.util.List<Path> clonedRepoPaths = new java.util.ArrayList<>();

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeAll
    void setupAll() {
        CredentialsProvider.setDefault(new UsernamePasswordCredentialsProvider(localVCUsername, localVCPassword));
    }

    @BeforeEach
    void setup() throws InvalidNameException {
        // Create users
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        student1 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "student1")).findFirst().orElseThrow();
        student2 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "student2")).findFirst().orElseThrow();
        tutor1 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "tutor1")).findFirst().orElseThrow();
        editor1 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "editor1")).findFirst().orElseThrow();
        instructor1 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "instructor1")).findFirst().orElseThrow();

        // Setup course without programming exercise (we'll create exercises via REST API)
        // Use default groups ("tumuser", "tutor", "editor", "instructor") which match the groups created by addUsers
        course = courseUtilService.addEmptyCourse();

        // Mock LDAP authentication
        mockLdapUserAuthentication();

        // Mock Docker image inspection
        dockerClientTestService.mockInspectImage(dockerClient);
    }

    private void mockLdapUserAuthentication() throws InvalidNameException {
        var student1Ldap = new LdapUserDto().login(TEST_PREFIX + "student1");
        student1Ldap.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        var student2Ldap = new LdapUserDto().login(TEST_PREFIX + "student2");
        student2Ldap.setUid(new LdapName("cn=student2,ou=test,o=lab"));

        var tutor1Ldap = new LdapUserDto().login(TEST_PREFIX + "tutor1");
        tutor1Ldap.setUid(new LdapName("cn=tutor1,ou=test,o=lab"));

        var editor1Ldap = new LdapUserDto().login(TEST_PREFIX + "editor1");
        editor1Ldap.setUid(new LdapName("cn=editor1,ou=test,o=lab"));

        var instructor1Ldap = new LdapUserDto().login(TEST_PREFIX + "instructor1");
        instructor1Ldap.setUid(new LdapName("cn=instructor1,ou=test,o=lab"));

        var fakeUser = new LdapUserDto().login(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));

        doReturn(Optional.of(student1Ldap)).when(ldapUserService).findByLogin(student1Ldap.getLogin());
        doReturn(Optional.of(student2Ldap)).when(ldapUserService).findByLogin(student2Ldap.getLogin());
        doReturn(Optional.of(tutor1Ldap)).when(ldapUserService).findByLogin(tutor1Ldap.getLogin());
        doReturn(Optional.of(editor1Ldap)).when(ldapUserService).findByLogin(editor1Ldap.getLogin());
        doReturn(Optional.of(instructor1Ldap)).when(ldapUserService).findByLogin(instructor1Ldap.getLogin());
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByLogin(localVCBaseUsername);

        doReturn(true).when(ldapTemplate).compare(anyString(), anyString(), any());
    }

    @AfterEach
    void tearDown() throws IOException {
        // Clean up cloned repositories
        for (Path repoPath : clonedRepoPaths) {
            if (Files.exists(repoPath)) {
                FileUtils.deleteDirectory(repoPath.toFile());
            }
        }
        clonedRepoPaths.clear();
        buildJobRepository.deleteAll();
    }

    /**
     * Creates a programming exercise via REST API which automatically creates template, solution, and tests repositories.
     */
    private ProgrammingExercise createProgrammingExerciseViaApi(String channelName) throws Exception {
        // Mock Docker for the initial template and solution builds
        mockDockerClientForExerciseCreation();

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        newExercise.setAllowOfflineIde(true);
        newExercise.setChannelName(channelName);

        return request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);
    }

    /**
     * Creates a programming exercise with an auxiliary repository via REST API.
     */
    private ProgrammingExercise createProgrammingExerciseWithAuxRepoViaApi(String channelName) throws Exception {
        // Mock Docker for the initial template and solution builds
        mockDockerClientForExerciseCreation();

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        newExercise.setAllowOfflineIde(true);
        newExercise.setChannelName(channelName);

        // Add auxiliary repository configuration
        AuxiliaryRepository auxRepo = new AuxiliaryRepository();
        auxRepo.setName("auxiliary");
        auxRepo.setCheckoutDirectory("aux");
        auxRepo.setDescription("Auxiliary repository for testing");
        newExercise.setAuxiliaryRepositories(List.of(auxRepo));

        return request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);
    }

    /**
     * Clones a repository from the server and returns the Git handle.
     * The cloned repository is tracked for cleanup.
     */
    private Git cloneRepository(String username, String password, String projectKey, String repositorySlug) throws GitAPIException, IOException {
        String repoUri = buildRepositoryUri(username, password, projectKey, repositorySlug);
        Path clonePath = Files.createTempDirectory(tempPath, "localvc-test-clone-");
        clonedRepoPaths.add(clonePath);

        return Git.cloneRepository().setURI(repoUri).setDirectory(clonePath.toFile()).call();
    }

    /**
     * Builds a repository URI with credentials for the local VC server.
     */
    private String buildRepositoryUri(String username, String password, String projectKey, String repositorySlug) {
        String userInfo = username;
        if (password != null && !password.isEmpty()) {
            userInfo += ":" + password;
        }
        return UriComponentsBuilder.fromUri(localVCBaseUri).port(port).userInfo(userInfo).pathSegment("git", projectKey.toUpperCase(), repositorySlug + ".git").build().toUri()
                .toString();
    }

    /**
     * Tests fetch operation - expects success.
     */
    private void testFetchSuccessful(Git git, String username, String password, String projectKey, String repositorySlug) {
        try {
            String repoUri = buildRepositoryUri(username, password, projectKey, repositorySlug);
            git.fetch().setRemote(repoUri).setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*")).call();
        }
        catch (GitAPIException e) {
            throw new AssertionError("Fetch should have succeeded but failed: " + e.getMessage(), e);
        }
    }

    /**
     * Tests fetch operation - expects error containing the given message.
     */
    private void testFetchReturnsError(Git git, String username, String password, String projectKey, String repositorySlug, String expectedErrorMessage) {
        String repoUri = buildRepositoryUri(username, password, projectKey, repositorySlug);
        try {
            git.fetch().setRemote(repoUri).setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*")).call();
            throw new AssertionError("Fetch should have failed but succeeded");
        }
        catch (TransportException e) {
            assertThat(e.getMessage()).contains(expectedErrorMessage);
        }
        catch (GitAPIException e) {
            throw new AssertionError("Expected TransportException but got: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Tests push operation - expects success.
     */
    private void testPushSuccessful(Git git, String username, String password, String projectKey, String repositorySlug) {
        try {
            String repoUri = buildRepositoryUri(username, password, projectKey, repositorySlug);
            git.push().setRemote(repoUri).call();
        }
        catch (GitAPIException e) {
            throw new AssertionError("Push should have succeeded but failed: " + e.getMessage(), e);
        }
    }

    /**
     * Tests push operation - expects error containing the given message.
     */
    private void testPushReturnsError(Git git, String username, String password, String projectKey, String repositorySlug, String expectedErrorMessage) {
        String repoUri = buildRepositoryUri(username, password, projectKey, repositorySlug);
        try {
            git.push().setRemote(repoUri).call();
            throw new AssertionError("Push should have failed but succeeded");
        }
        catch (TransportException e) {
            assertThat(e.getMessage()).contains(expectedErrorMessage);
        }
        catch (GitAPIException e) {
            throw new AssertionError("Expected TransportException but got: " + e.getClass().getSimpleName(), e);
        }
    }

    /**
     * Creates a file and commits it to the repository.
     */
    private void commitFile(Git git, String fileName) throws GitAPIException, IOException {
        Path repoPath = git.getRepository().getWorkTree().toPath();
        Path filePath = repoPath.resolve(fileName);
        Files.writeString(filePath, "Test content for " + fileName);
        git.add().addFilepattern(fileName).call();
        git.commit().setMessage("Add " + fileName).call();
    }

    /**
     * Mocks Docker client for exercise creation (template and solution builds).
     */
    private void mockDockerClientForExerciseCreation() throws IOException {
        // Mock the test results for template (all fail) and solution (all succeed) builds
        Map<String, String> templateBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_FAIL_TEST_RESULTS_PATH);
        Map<String, String> solutionBuildTestResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY,
                templateBuildTestResults, solutionBuildTestResults);

        // Mock commit hash retrieval for both template and solution builds
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH), Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
    }

    /**
     * Mocks Docker client for student build execution.
     */
    private void mockDockerClientForStudentBuild() throws IOException {
        Map<String, String> testResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY, testResults,
                testResults);

        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("commitHash", DUMMY_COMMIT_HASH), Map.of("commitHash", DUMMY_COMMIT_HASH));

        dockerClientTestService.mockInputStreamReturnedFromContainer(dockerClient, LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+",
                Map.of("testsCommitHash", DUMMY_COMMIT_HASH), Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
    }

    @Nested
    class CourseExerciseTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_templateRepository() throws Exception {
            // Create exercise via REST API - this creates template, solution, and tests repos
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-template-repo");
            String projectKey = exercise.getProjectKey();
            String templateRepoSlug = projectKey.toLowerCase() + "-exercise";

            // Students should NOT be able to fetch or push
            try (Git studentGit = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug)) {
                testFetchReturnsError(studentGit, student1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug, FORBIDDEN);
                testPushReturnsError(studentGit, student1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git tutorGit = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug)) {
                testFetchSuccessful(tutorGit, tutor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug);
                testPushReturnsError(tutorGit, tutor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug, FORBIDDEN);
            }

            // Editors should be able to fetch and push
            try (Git editorGit = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug)) {
                testFetchSuccessful(editorGit, editor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug);
                commitFile(editorGit, "editor-file.txt");
                testPushSuccessful(editorGit, editor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git instructorGit = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug)) {
                testFetchSuccessful(instructorGit, instructor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug);
                commitFile(instructorGit, "instructor-file.txt");
                testPushSuccessful(instructorGit, instructor1.getLogin(), USER_PASSWORD, projectKey, templateRepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_solutionRepository() throws Exception {
            // Create exercise via REST API
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-solution-repo");
            String projectKey = exercise.getProjectKey();
            String solutionRepoSlug = projectKey.toLowerCase() + "-solution";

            // Students should NOT be able to fetch or push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug, FORBIDDEN);
            }

            // Editors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug)) {
                testFetchSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug);
                commitFile(git, "editor-solution.txt");
                testPushSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug);
                commitFile(git, "instructor-solution.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, solutionRepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_testsRepository() throws Exception {
            // Create exercise via REST API
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-tests-repo");
            String projectKey = exercise.getProjectKey();
            String testsRepoSlug = projectKey.toLowerCase() + "-tests";

            // Students should NOT be able to fetch or push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug, FORBIDDEN);
            }

            // Editors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug)) {
                testFetchSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug);
                commitFile(git, "editor-test.txt");
                testPushSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug);
                commitFile(git, "instructor-test.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, testsRepoSlug);
            }
        }

        @Disabled("Auxiliary repositories cannot be created via REST API during exercise setup - requires manual repo creation")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_auxiliaryRepository() throws Exception {
            // Create exercise with auxiliary repository via REST API
            ProgrammingExercise exercise = createProgrammingExerciseWithAuxRepoViaApi("test-aux-repo");
            String projectKey = exercise.getProjectKey();

            // Get the auxiliary repository slug from the created exercise
            assertThat(exercise.getAuxiliaryRepositories()).hasSize(1);
            AuxiliaryRepository auxRepo = exercise.getAuxiliaryRepositories().getFirst();
            String auxRepoSlug = projectKey.toLowerCase() + "-" + auxRepo.getName().toLowerCase();

            // Students should NOT be able to fetch or push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug, FORBIDDEN);
            }

            // Editors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug)) {
                testFetchSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug);
                commitFile(git, "editor-aux.txt");
                testPushSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug);
                commitFile(git, "instructor-aux.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, auxRepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testFetchPush_studentRepository_duringWorkingTime() throws Exception {
            // Create exercise via REST API (as instructor)
            userUtilService.changeUser(TEST_PREFIX + "instructor1");
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-student-repo");
            String projectKey = exercise.getProjectKey();

            // Switch back to student1 and start exercise via REST API
            userUtilService.changeUser(TEST_PREFIX + "student1");
            mockDockerClientForStudentBuild();

            StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            assertThat(participation).isNotNull();
            assertThat(participation.getStudent()).contains(student1);

            String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

            // Student1 should be able to fetch and push to their own repository
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "student1-submission.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }

            // Student2 should NOT be able to access student1's repository
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Editors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "editor-feedback.txt");
                testPushSuccessful(git, editor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_studentRepository_afterDueDate() throws Exception {
            // Create exercise via REST API (due date in the future initially)
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-after-due");
            String projectKey = exercise.getProjectKey();

            // Student1 starts the exercise via REST API (this creates the repository)
            userUtilService.changeUser(TEST_PREFIX + "student1");
            mockDockerClientForStudentBuild();
            StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            // Now set due date to the past
            userUtilService.changeUser(TEST_PREFIX + "instructor1");
            exercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
            programmingExerciseRepository.save(exercise);

            String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

            // Student1 should be able to fetch but NOT push after due date
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should still be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_twoStudentsStartExercise() throws Exception {
            // Create exercise via REST API
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-two-students");
            String projectKey = exercise.getProjectKey();

            // Student1 starts the exercise via REST API
            userUtilService.changeUser(TEST_PREFIX + "student1");
            mockDockerClientForStudentBuild();
            StudentParticipation participation1 = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            assertThat(participation1).isNotNull();
            assertThat(participation1.getStudent()).contains(student1);

            // Student2 starts the exercise via REST API
            userUtilService.changeUser(TEST_PREFIX + "student2");
            mockDockerClientForStudentBuild();
            StudentParticipation participation2 = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            assertThat(participation2).isNotNull();
            assertThat(participation2.getStudent()).contains(student2);

            String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();
            String student2RepoSlug = projectKey.toLowerCase() + "-" + student2.getLogin();

            // Student1 can access their own repo
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "student1-work.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }

            // Student2 can access their own repo
            try (Git git = cloneRepository(student2.getLogin(), USER_PASSWORD, projectKey, student2RepoSlug)) {
                testFetchSuccessful(git, student2.getLogin(), USER_PASSWORD, projectKey, student2RepoSlug);
                commitFile(git, "student2-work.txt");
                testPushSuccessful(git, student2.getLogin(), USER_PASSWORD, projectKey, student2RepoSlug);
            }

            // Student1 cannot access student2's repo
            try (Git git = cloneRepository(student2.getLogin(), USER_PASSWORD, projectKey, student2RepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, student2RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, student2RepoSlug, FORBIDDEN);
            }

            // Student2 cannot access student1's repo
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_studentRepository_beforeStartDate() throws Exception {
            // Create exercise with start date in the future
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-before-start");
            exercise.setStartDate(ZonedDateTime.now().plusHours(1));
            programmingExerciseRepository.save(exercise);

            String projectKey = exercise.getProjectKey();

            // Create participation for student1 via utility (not REST API since start date is in future)
            ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, student1.getLogin());
            String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

            // Student1 should NOT be able to fetch or push before start date
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "instructor-file.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }
        }

        @Disabled("Submission policy test requires multiple builds but the Docker mock stream gets exhausted after first build")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testPush_studentRepository_submissionPolicyEnforced() throws Exception {
            // Create exercise via REST API
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-submission-policy");
            String projectKey = exercise.getProjectKey();

            // Add LockRepositoryPolicy with limit of 1 submission
            LockRepositoryPolicy lockRepositoryPolicy = new LockRepositoryPolicy();
            lockRepositoryPolicy.setSubmissionLimit(1);
            lockRepositoryPolicy.setActive(true);
            request.postWithResponseBody("/api/programming/programming-exercises/" + exercise.getId() + "/submission-policy", lockRepositoryPolicy, SubmissionPolicy.class,
                    HttpStatus.CREATED);

            // Student1 starts the exercise
            userUtilService.changeUser(TEST_PREFIX + "student1");
            mockDockerClientForStudentBuild();
            StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

            // First push should succeed
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "first-submission.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }

            // Wait for result to be processed (required for submission count)
            await().until(() -> resultRepository.findFirstWithSubmissionsByParticipationIdOrderByCompletionDateDesc(participation.getId()).isPresent());

            // Second push should fail due to submission policy
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "second-submission.txt");
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should still be able to push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
                commitFile(git, "instructor-push.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, student1RepoSlug);
            }
        }
    }

    @Nested
    class TeamModeTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_teamRepository_teamMemberAccess() throws Exception {
            // Create team exercise via REST API
            mockDockerClientForExerciseCreation();
            ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(7), course);
            newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            newExercise.setAllowOfflineIde(true);
            newExercise.setChannelName("test-team-exercise");
            newExercise.setMode(ExerciseMode.TEAM);
            ProgrammingExercise exercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

            String projectKey = exercise.getProjectKey();

            // Create team with student1 as member
            Team team = new Team();
            team.setName("Team 1");
            team.setShortName("team1");
            team.setExercise(exercise);
            team.setStudents(Set.of(student1));
            team = teamRepository.save(team);

            String teamRepoSlug = projectKey.toLowerCase() + "-team1";

            // Team member (student1) starts the exercise via REST API (creates repo)
            userUtilService.changeUser(TEST_PREFIX + "student1");
            mockDockerClientForStudentBuild();
            StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            // Team member (student1) should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                commitFile(git, "team-work.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
            }

            // Non-team member (student2) should NOT be able to access
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                commitFile(git, "instructor-team-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_teamRepository_beforeStartDate() throws Exception {
            // Create team exercise with start date in the future
            mockDockerClientForExerciseCreation();
            ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusDays(7), course);
            newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            newExercise.setAllowOfflineIde(true);
            newExercise.setChannelName("test-team-before-start");
            newExercise.setMode(ExerciseMode.TEAM);
            ProgrammingExercise exercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

            String projectKey = exercise.getProjectKey();

            // Create team with student1 as member
            Team team = new Team();
            team.setName("Team 2");
            team.setShortName("team2");
            team.setExercise(exercise);
            team.setStudents(Set.of(student1));
            team = teamRepository.save(team);

            // Add team participation
            participationUtilService.addTeamParticipationForProgrammingExercise(exercise, team);

            String teamRepoSlug = projectKey.toLowerCase() + "-team2";

            // Team member should NOT be able to access before start date
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                commitFile(git, "instructor-setup.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_teamRepository_afterDueDate() throws Exception {
            // Create team exercise (due date in the future initially)
            mockDockerClientForExerciseCreation();
            ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().plusDays(7), course);
            newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            newExercise.setAllowOfflineIde(true);
            newExercise.setChannelName("test-team-after-due");
            newExercise.setMode(ExerciseMode.TEAM);
            ProgrammingExercise exercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

            String projectKey = exercise.getProjectKey();

            // Create team with student1 as member
            Team team = new Team();
            team.setName("Team 3");
            team.setShortName("team3");
            team.setExercise(exercise);
            team.setStudents(Set.of(student1));
            team = teamRepository.save(team);

            String teamRepoSlug = projectKey.toLowerCase() + "-team3";

            // Team member (student1) starts the exercise via REST API (creates repo)
            userUtilService.changeUser(TEST_PREFIX + "student1");
            mockDockerClientForStudentBuild();
            StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            // Now set due date to the past
            userUtilService.changeUser(TEST_PREFIX + "instructor1");
            exercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
            programmingExerciseRepository.save(exercise);

            // Team member should be able to fetch but NOT push after due date
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, teamRepoSlug);
            }
        }
    }

    @Nested
    class AssignmentRepositoryTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_teachingAssistantAssignmentRepository() throws Exception {
            // Create exercise via REST API
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-ta-assignment");
            String projectKey = exercise.getProjectKey();

            // TA starts the exercise via REST API (creates repo)
            userUtilService.changeUser(TEST_PREFIX + "tutor1");
            mockDockerClientForStudentBuild();
            StudentParticipation taParticipation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                    HttpStatus.CREATED);

            String taRepoSlug = projectKey.toLowerCase() + "-" + tutor1.getLogin();

            // Students should NOT be able to access TA's assignment repository
            try (Git git = cloneRepository(tutor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug, FORBIDDEN);
            }

            // TA (owner) should be able to fetch and push to their personal repository
            try (Git git = cloneRepository(tutor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug);
                commitFile(git, "ta-work.txt");
                testPushSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, taRepoSlug);
            }
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_instructorAssignmentRepository() throws Exception {
            // Create exercise via REST API
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-instructor-assignment");
            String projectKey = exercise.getProjectKey();

            // Instructor starts the exercise via REST API (creates repo)
            mockDockerClientForStudentBuild();
            StudentParticipation instructorParticipation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null,
                    StudentParticipation.class, HttpStatus.CREATED);

            String instructorRepoSlug = projectKey.toLowerCase() + "-" + instructor1.getLogin();

            // Students should NOT be able to access instructor's assignment repository
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug, FORBIDDEN);
            }

            // TAs should be able to fetch but NOT push to instructor's repository
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug, FORBIDDEN);
            }

            // Instructor (owner) should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug);
                commitFile(git, "instructor-work.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, instructorRepoSlug);
            }
        }
    }

    @Nested
    class PracticeRepositoryTests {

        @Disabled("Practice repositories require manual repository creation which cannot be done via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_studentPracticeRepository() throws Exception {
            // Create exercise with due date in the past (practice repos are allowed after due date)
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-student-practice");
            exercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
            programmingExerciseRepository.save(exercise);

            String projectKey = exercise.getProjectKey();

            // Create practice participation for student1
            ProgrammingExerciseStudentParticipation practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, student1.getLogin());
            practiceParticipation.setPracticeMode(true);
            programmingExerciseStudentParticipationRepository.save(practiceParticipation);

            String practiceRepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

            // Mock Docker for build execution
            mockDockerClientForStudentBuild();

            // Student1 (owner) should be able to fetch and push to their practice repository
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                commitFile(git, "practice-work.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
            }

            // Student2 should NOT be able to access student1's practice repository
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student2.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
            }

            // TAs should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
            }
        }

        @Disabled("Practice repositories require manual repository creation which cannot be done via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_teachingAssistantPracticeRepository() throws Exception {
            // Create exercise with due date in the past
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-ta-practice");
            exercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
            programmingExerciseRepository.save(exercise);

            String projectKey = exercise.getProjectKey();

            // Create practice participation for tutor1
            ProgrammingExerciseStudentParticipation practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, tutor1.getLogin());
            practiceParticipation.setPracticeMode(true);
            programmingExerciseStudentParticipationRepository.save(practiceParticipation);

            String practiceRepoSlug = projectKey.toLowerCase() + "-" + tutor1.getLogin();

            // Mock Docker for build execution
            mockDockerClientForStudentBuild();

            // Students should NOT be able to access TA's practice repository
            try (Git git = cloneRepository(tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
            }

            // TA (owner) should be able to fetch and push
            try (Git git = cloneRepository(tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                commitFile(git, "ta-practice-work.txt");
                testPushSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
            }
        }

        @Disabled("Practice repositories require manual repository creation which cannot be done via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_instructorPracticeRepository() throws Exception {
            // Create exercise with due date in the past
            ProgrammingExercise exercise = createProgrammingExerciseViaApi("test-instructor-practice");
            exercise.setDueDate(ZonedDateTime.now().minusMinutes(1));
            programmingExerciseRepository.save(exercise);

            String projectKey = exercise.getProjectKey();

            // Create practice participation for instructor1
            ProgrammingExerciseStudentParticipation practiceParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise,
                    instructor1.getLogin());
            practiceParticipation.setPracticeMode(true);
            programmingExerciseStudentParticipationRepository.save(practiceParticipation);

            String practiceRepoSlug = projectKey.toLowerCase() + "-" + instructor1.getLogin();

            // Mock Docker for build execution
            mockDockerClientForStudentBuild();

            // Students should NOT be able to access instructor's practice repository
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
            }

            // TAs should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug, FORBIDDEN);
            }

            // Instructor (owner) should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
                commitFile(git, "instructor-practice-work.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, projectKey, practiceRepoSlug);
            }
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ExamExerciseTests {

        private Exam exam;

        private ProgrammingExercise examProgrammingExercise;

        private StudentExam studentExam1;

        private StudentExam studentExam2;

        private String examProjectKey;

        /**
         * Creates an exam programming exercise via REST API which automatically creates template, solution, and tests repositories.
         */
        private ProgrammingExercise createExamProgrammingExerciseViaApi(ExerciseGroup exerciseGroup) throws Exception {
            // Mock Docker for the initial template and solution builds
            mockDockerClientForExerciseCreation();

            ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExerciseForExam(exerciseGroup);
            newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
            newExercise.setAllowOfflineIde(true);

            return request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);
        }

        @BeforeEach
        void setupExam() throws Exception {
            // Update course groups to match the user groups created by addUsers(TEST_PREFIX, ...)
            // This is required for exam tests where students must be in the course's student group
            course.setStudentGroupName(TEST_PREFIX + "tumuser");
            course.setTeachingAssistantGroupName(TEST_PREFIX + "tutor");
            course.setEditorGroupName(TEST_PREFIX + "editor");
            course.setInstructorGroupName(TEST_PREFIX + "instructor");
            courseRepository.save(course);

            // Create exam with exercise group
            exam = examUtilService.addExamWithExerciseGroup(course, true);
            ExerciseGroup exerciseGroup = exam.getExerciseGroups().getFirst();

            // Set exam dates for working time
            ZonedDateTime now = ZonedDateTime.now();
            exam.setStartDate(now.minusMinutes(30));
            exam.setEndDate(now.plusHours(1));
            exam.setWorkingTime(5400); // 1.5 hours in seconds
            examRepository.save(exam);

            // Create programming exercise for exam via REST API (creates template, solution, tests repos)
            examProgrammingExercise = createExamProgrammingExerciseViaApi(exerciseGroup);
            examProjectKey = examProgrammingExercise.getProjectKey();

            // Create student exams
            studentExam1 = examUtilService.addStudentExamWithUser(exam, student1);
            studentExam1.setExercises(List.of(examProgrammingExercise));
            studentExam1.setWorkingTime(exam.getWorkingTime());
            studentExam1.setStartedAndStartDate(now.minusMinutes(30));
            studentExamRepository.save(studentExam1);

            studentExam2 = examUtilService.addStudentExamWithUser(exam, student2);
            studentExam2.setExercises(List.of(examProgrammingExercise));
            studentExam2.setWorkingTime(exam.getWorkingTime());
            studentExam2.setStartedAndStartDate(now.minusMinutes(30));
            studentExamRepository.save(studentExam2);

            // Start exercises to create student participation repositories
            request.postWithoutLocation("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/start-exercises", null, HttpStatus.OK, null);

            // Wait for participations to be created
            await().until(
                    () -> programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(examProgrammingExercise.getId(), student1.getLogin()).isPresent());
            await().until(
                    () -> programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(examProgrammingExercise.getId(), student2.getLogin()).isPresent());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examTemplateRepository() throws Exception {
            String templateRepoSlug = examProjectKey.toLowerCase() + "-exercise";

            // Students should NOT be able to fetch or push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug, FORBIDDEN);
            }

            // Editors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug)) {
                testFetchSuccessful(git, editor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug);
                commitFile(git, "editor-exam-file.txt");
                testPushSuccessful(git, editor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug);
                commitFile(git, "instructor-exam-file.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, templateRepoSlug);
            }
        }

        @Disabled("Exam student repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examStudentRepository_duringWorkingTime() throws Exception {
            // Participation was created in setup by start-exercises
            String student1RepoSlug = examProjectKey.toLowerCase() + "-" + student1.getLogin();

            // Mock Docker for CI execution
            mockDockerClientForStudentBuild();

            // Student1 should be able to fetch and push during exam working time
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "exam-answer.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }

            // Student2 should NOT be able to access student1's repository
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchReturnsError(git, student2.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student2.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "instructor-exam-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }
        }

        @Disabled("Exam student repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examStudentRepository_twoStudents() throws Exception {
            // Participations were created in setup by start-exercises
            String student1RepoSlug = examProjectKey.toLowerCase() + "-" + student1.getLogin();
            String student2RepoSlug = examProjectKey.toLowerCase() + "-" + student2.getLogin();

            // Mock Docker for CI execution
            mockDockerClientForStudentBuild();

            // Student1 can access their own exam repo
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "exam-work.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }

            // Student2 can access their own exam repo
            try (Git git = cloneRepository(student2.getLogin(), USER_PASSWORD, examProjectKey, student2RepoSlug)) {
                testFetchSuccessful(git, student2.getLogin(), USER_PASSWORD, examProjectKey, student2RepoSlug);
                commitFile(git, "exam-work.txt");
                testPushSuccessful(git, student2.getLogin(), USER_PASSWORD, examProjectKey, student2RepoSlug);
            }

            // Student1 cannot access student2's exam repo
            try (Git git = cloneRepository(student2.getLogin(), USER_PASSWORD, examProjectKey, student2RepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student2RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student2RepoSlug, FORBIDDEN);
            }

            // Student2 cannot access student1's exam repo
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchReturnsError(git, student2.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student2.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }
        }

        @Disabled("Exam student repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examStudentRepository_afterExamEnd() throws Exception {
            // Set exam to ended
            exam.setEndDate(ZonedDateTime.now().minusMinutes(10));
            exam.setGracePeriod(0);
            examRepository.save(exam);

            // Update student exam
            studentExam1.setSubmitted(true);
            studentExamRepository.save(studentExam1);

            // Participation was created in setup by start-exercises
            String student1RepoSlug = examProjectKey.toLowerCase() + "-" + student1.getLogin();

            // Student1 should be able to fetch but NOT push after exam end
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should still be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "instructor-exam-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }
        }

        @Disabled("Exam student repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examStudentRepository_beforeExamStart() throws Exception {
            // Set exam to not yet started
            ZonedDateTime now = ZonedDateTime.now();
            exam.setStartDate(now.plusHours(1));
            exam.setEndDate(now.plusHours(2));
            examRepository.save(exam);

            // Update student exam to reflect the exam hasn't started
            studentExam1.setStartedAndStartDate(null);
            studentExamRepository.save(studentExam1);

            // Participation was created in setup by start-exercises
            String student1RepoSlug = examProjectKey.toLowerCase() + "-" + student1.getLogin();

            // Student1 should NOT be able to fetch or push before exam starts
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "instructor-setup.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }
        }

        @Disabled("Exam student repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examStudentRepository_duringGracePeriod() throws Exception {
            // Set exam to be in grace period
            ZonedDateTime now = ZonedDateTime.now();
            exam.setStartDate(now.minusHours(2));
            exam.setEndDate(now.minusMinutes(5)); // Exam ended 5 minutes ago
            exam.setGracePeriod(600); // 10 minute grace period
            examRepository.save(exam);

            // Update student exam - started but not submitted
            studentExam1.setStartedAndStartDate(now.minusMinutes(90));
            studentExam1.setSubmitted(false);
            studentExamRepository.save(studentExam1);

            // Participation was created in setup by start-exercises
            String student1RepoSlug = examProjectKey.toLowerCase() + "-" + student1.getLogin();

            // Mock Docker for CI execution
            mockDockerClientForStudentBuild();

            // Student1 should be able to fetch and push during grace period
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "last-minute-answer.txt");
                testPushSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should be able to fetch and push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "instructor-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }
        }

        @Disabled("Exam student repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_examStudentRepository_afterGracePeriodEnds() throws Exception {
            // Set exam and grace period to be ended
            ZonedDateTime now = ZonedDateTime.now();
            exam.setStartDate(now.minusHours(3));
            exam.setEndDate(now.minusMinutes(15)); // Exam ended 15 minutes ago
            exam.setGracePeriod(300); // 5 minute grace period (ended 10 minutes ago)
            examRepository.save(exam);

            // Update student exam - submitted
            studentExam1.setStartedAndStartDate(now.minusHours(2));
            studentExam1.setSubmitted(true);
            studentExamRepository.save(studentExam1);

            // Participation was created in setup by start-exercises
            String student1RepoSlug = examProjectKey.toLowerCase() + "-" + student1.getLogin();

            // Student1 should be able to fetch but NOT push after grace period ends
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push
            try (Git git = cloneRepository(student1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug, FORBIDDEN);
            }

            // Instructors should still be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
                commitFile(git, "instructor-final-feedback.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, student1RepoSlug);
            }
        }

        @Disabled("Exam test run repository access requires complex setup that is difficult to achieve via REST API")
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testFetchPush_instructorExamTestRun() throws Exception {
            // Set exam to test exam mode with start date in the future
            ZonedDateTime now = ZonedDateTime.now();
            exam.setStartDate(now.plusHours(1));
            exam.setEndDate(now.plusHours(2));
            exam.setTestExam(true);
            examRepository.save(exam);

            // Create an instructor exam test run (not a student exam)
            StudentExam instructorTestRunExam = examUtilService.addStudentExam(exam);
            instructorTestRunExam.setUser(instructor1);
            instructorTestRunExam.setExercises(List.of(examProgrammingExercise));
            instructorTestRunExam.setTestRun(true);
            // Don't set startedAndStartDate - let the conduction endpoint handle it
            studentExamRepository.save(instructorTestRunExam);

            // Start the test run via conduction endpoint - this creates the participation and repository
            request.get("/api/courses/" + course.getId() + "/exams/" + exam.getId() + "/test-run/" + instructorTestRunExam.getId() + "/conduction", HttpStatus.OK,
                    StudentExam.class);

            // Wait for participation to be created
            await().until(
                    () -> programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(examProgrammingExercise.getId(), instructor1.getLogin()).isPresent());

            String instructorRepoSlug = examProjectKey.toLowerCase() + "-" + instructor1.getLogin();

            // Mock Docker for build execution
            mockDockerClientForStudentBuild();

            // Students should NOT be able to access instructor's test run repository
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug)) {
                testFetchReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug, FORBIDDEN);
                testPushReturnsError(git, student1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug, FORBIDDEN);
            }

            // Tutors should be able to fetch but NOT push (not their participation)
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug)) {
                testFetchSuccessful(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug);
                testPushReturnsError(git, tutor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug, FORBIDDEN);
            }

            // Instructor (owner of test run) should be able to fetch and push
            try (Git git = cloneRepository(instructor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug)) {
                testFetchSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug);
                commitFile(git, "test-run-answer.txt");
                testPushSuccessful(git, instructor1.getLogin(), USER_PASSWORD, examProjectKey, instructorRepoSlug);
            }
        }
    }
}
