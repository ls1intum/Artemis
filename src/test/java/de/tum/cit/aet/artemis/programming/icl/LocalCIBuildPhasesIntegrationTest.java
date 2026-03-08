package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY;
import static de.tum.cit.aet.artemis.core.config.Constants.LOCAL_CI_RESULTS_DIRECTORY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.NotFoundException;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.core.service.ldap.LdapUserDto;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTestBase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.build.BuildPhaseCondition;
import de.tum.cit.aet.artemis.programming.dto.BuildPhaseDTO;
import de.tum.cit.aet.artemis.programming.dto.BuildPlanPhasesDTO;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIResultListenerService;
import de.tum.cit.aet.artemis.programming.service.localci.LocalCIResultProcessingService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;

/**
 * Integration tests for the build phases feature with full end-to-end execution
 * through the mocked Docker pipeline.
 * <p>
 * These tests verify that the phases-based build plan format works correctly
 * through the entire pipeline: push -> queue -> Docker mock -> result processing -> DB.
 * <p>
 * Exercises are created via REST API (not using the deprecated {@code LocalRepository}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
@Isolated
class LocalCIBuildPhasesIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTestBase {

    private static final String TEST_PREFIX = "localcibuildphases";

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Autowired
    private TempFileUtilService tempFileUtilService;

    @Autowired
    private ApplicationContext applicationContext;

    private Course course;

    private User student1;

    private User instructor1;

    private final List<Path> clonedRepoPaths = new ArrayList<>();

    private DistributedQueue<BuildJobQueueItem> queuedJobs;

    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @BeforeEach
    void setup() throws InvalidNameException {
        List<User> users = userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        student1 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "student1")).findFirst().orElseThrow();
        instructor1 = users.stream().filter(u -> u.getLogin().equals(TEST_PREFIX + "instructor1")).findFirst().orElseThrow();

        course = courseUtilService.addEmptyCourse();

        mockLdapUserAuthentication();
        forceLocalCIResultProcessingInitialization();
        dockerClientTestService.mockInspectImage(dockerClient);
        queuedJobs = distributedDataAccessService.getDistributedBuildJobQueue();
        processingJobs = distributedDataAccessService.getDistributedProcessingJobs();
    }

    private void forceLocalCIResultProcessingInitialization() {
        applicationContext.getBean(LocalCIResultProcessingService.class);
        applicationContext.getBean(LocalCIResultListenerService.class);
    }

    @AfterEach
    void cleanUp() throws IOException {
        for (Path repoPath : clonedRepoPaths) {
            if (Files.exists(repoPath)) {
                FileUtils.deleteDirectory(repoPath.toFile());
            }
        }
        clonedRepoPaths.clear();
        buildJobRepository.deleteAll();
    }

    private void mockLdapUserAuthentication() throws InvalidNameException {
        var student1Ldap = new LdapUserDto().login(TEST_PREFIX + "student1");
        student1Ldap.setUid(new LdapName("cn=student1,ou=test,o=lab"));

        var instructor1Ldap = new LdapUserDto().login(TEST_PREFIX + "instructor1");
        instructor1Ldap.setUid(new LdapName("cn=instructor1,ou=test,o=lab"));

        var fakeUser = new LdapUserDto().login(localVCBaseUsername);
        fakeUser.setUid(new LdapName("cn=" + localVCBaseUsername + ",ou=test,o=lab"));

        doReturn(Optional.of(student1Ldap)).when(ldapUserService).findByLogin(student1Ldap.getLogin());
        doReturn(Optional.of(instructor1Ldap)).when(ldapUserService).findByLogin(instructor1Ldap.getLogin());
        doReturn(Optional.of(fakeUser)).when(ldapUserService).findByLogin(localVCBaseUsername);

        doReturn(true).when(ldapTemplate).compare(anyString(), anyString(), any());
    }

    /**
     * Creates a programming exercise via REST API with the phases configuration already set and
     * waits for the template and solution builds to complete. This ensures the exercise creation
     * mocks are fully consumed before any student build mocks are set up, avoiding race conditions.
     */
    private ProgrammingExercise createExerciseAndWaitForBuilds(String channelName, ZonedDateTime dueDate) throws Exception {
        mockDockerClientForExerciseCreation();

        ProgrammingExercise newExercise = ProgrammingExerciseFactory.generateProgrammingExercise(ZonedDateTime.now().minusDays(1), dueDate, course);
        newExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        newExercise.setAllowOfflineIde(true);
        newExercise.setChannelName(channelName);
        newExercise.getBuildConfig().setBuildPlanConfiguration(createBuildPlanPhases().serialize());

        ProgrammingExercise exercise = request.postWithResponseBody("/api/programming/programming-exercises/setup", newExercise, ProgrammingExercise.class, HttpStatus.CREATED);

        // Wait for template and solution builds (triggered asynchronously during exercise creation)
        // to fully complete before returning. This ensures the exercise creation Docker mocks are
        // consumed and won't interfere with subsequent student build mocks.
        waitForExerciseCreationBuilds(exercise);

        return exercise;
    }

    /**
     * Waits until the asynchronous template and solution builds triggered by exercise creation
     * have fully left the LocalCI queue and processing map. This is the actual synchronization point
     * needed by these tests so that the exercise-creation Docker mocks are fully consumed before
     * student-build-specific mocks are installed.
     */
    private void waitForExerciseCreationBuilds(ProgrammingExercise exercise) {
        long templateParticipationId = exercise.getTemplateParticipation().getId();
        long solutionParticipationId = exercise.getSolutionParticipation().getId();

        await().atMost(Duration.ofSeconds(30))
                .until(() -> queuedJobs.getAll().stream().noneMatch(job -> job.participationId() == templateParticipationId || job.participationId() == solutionParticipationId)
                        && processingJobs.values().stream().noneMatch(job -> job.participationId() == templateParticipationId || job.participationId() == solutionParticipationId));
    }

    /**
     * Creates a {@link CopyArchiveFromContainerCmd} mock whose {@code exec()} uses {@code doAnswer}
     * to create a fresh {@link java.io.BufferedInputStream} on every invocation. This avoids the
     * problem where Mockito's {@code doReturn} pre-creates a single stream object that gets consumed
     * by the first caller (or by retries in {@code executeWithRetry}), leaving subsequent callers
     * with an exhausted stream.
     */
    private void mockFreshStreamForContainer(String resourceRegexPattern, Map<String, String> dataMap) {
        CopyArchiveFromContainerCmd cmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> matcher = path -> path.matches(resourceRegexPattern);
        doReturn(cmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(matcher));
        doAnswer(invocation -> dockerClientTestService.createInputStreamForTarArchiveFromMap(dataMap)).when(cmd).exec();
    }

    /**
     * Mocks Docker client for the initial template and solution builds during exercise creation.
     * Uses {@code doAnswer} so that each {@code exec()} call returns a fresh InputStream,
     * preventing the retry mechanism in {@code getArchiveFromContainer} from consuming
     * pre-created streams.
     */
    private void mockDockerClientForExerciseCreation() throws IOException {
        // For exercise creation, use ALL_SUCCEED results for both template and solution.
        // The template build will show "all pass" (not matching reality) but this is acceptable
        // because our tests only verify student build results — not the exercise creation results.
        Map<String, String> testResults = dockerClientTestService.createMapFromTestResultsFolder(ALL_SUCCEED_TEST_RESULTS_PATH);
        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY, testResults);

        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", DUMMY_COMMIT_HASH));

        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+", Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
    }

    /**
     * Mocks Docker client for a student build that produces test results.
     * Uses {@code doAnswer} for fresh streams on every invocation.
     */
    private void mockDockerClientForStudentBuildWithTestResults() throws IOException {
        Map<String, String> testResults = dockerClientTestService.createMapFromTestResultsFolder(PARTLY_SUCCESSFUL_TEST_RESULTS_PATH);
        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY, testResults);

        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+", Map.of("commitHash", DUMMY_COMMIT_HASH));

        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+", Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
    }

    /**
     * Mocks Docker client for a compile-only build where no test results are expected.
     * The results directory mock throws {@link NotFoundException} to simulate the absence
     * of test result files, which causes the build agent to use {@code constructFailedBuildResult}
     * with the script exit code determining success/failure.
     * Uses {@code doAnswer} for commit hash streams so retries get fresh streams.
     */
    private void mockDockerClientForCompileOnlyBuild() {
        // Mock the results directory to throw NotFoundException (no test results produced)
        CopyArchiveFromContainerCmd resultsCopyCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> resultsPathMatcher = path -> path.matches(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + LOCAL_CI_RESULTS_DIRECTORY);
        doReturn(resultsCopyCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(resultsPathMatcher));
        doThrow(new NotFoundException("No test results in compile-only build")).when(resultsCopyCmd).exec();

        // Mock commit hash retrieval with fresh streams
        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/assignment/.git/refs/heads/[^/]+", Map.of("commitHash", DUMMY_COMMIT_HASH));

        mockFreshStreamForContainer(LOCAL_CI_DOCKER_CONTAINER_WORKING_DIRECTORY + "/testing-dir/.git/refs/heads/[^/]+", Map.of("testsCommitHash", DUMMY_COMMIT_HASH));
    }

    /**
     * Overrides the Docker exec inspect mock to return a non-zero exit code,
     * simulating a build script failure.
     */
    private void mockDockerExecExitCode(long exitCode) {
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(anyString())).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(exitCode);
    }

    private BuildPlanPhasesDTO createBuildPlanPhases() {
        return new BuildPlanPhasesDTO(List.of(new BuildPhaseDTO("Compile", "./gradlew testClasses", BuildPhaseCondition.ALWAYS, List.of()),
                new BuildPhaseDTO("Test", "./gradlew test", BuildPhaseCondition.AFTER_DUE_DATE, List.of("build/test-results/test/*.xml"))), null);
    }

    /**
     * Adds the standard 13 test cases to the exercise (required for test result matching).
     */
    private void addTestCasesToExercise(ProgrammingExercise exercise) {
        exercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(exercise.getId()).orElseThrow();
        if (testCaseRepository.findByExerciseId(exercise.getId()).size() != 13) {
            localVCLocalCITestService.addTestCases(exercise);
        }
    }

    private Git cloneRepository(String username, String projectKey, String repositorySlug) throws GitAPIException, IOException {
        String repoUri = buildRepositoryUri(username, projectKey, repositorySlug);
        Path clonePath = tempFileUtilService.createTempDirectory(tempPath, "build-phases-test-clone-");
        clonedRepoPaths.add(clonePath);
        return Git.cloneRepository().setURI(repoUri).setDirectory(clonePath.toFile()).call();
    }

    private String buildRepositoryUri(String username, String projectKey, String repositorySlug) {
        String userInfo = username + ":" + UserFactory.USER_PASSWORD;
        return UriComponentsBuilder.fromUri(localVCBaseUri).port(port).userInfo(userInfo).pathSegment("git", projectKey.toUpperCase(), repositorySlug + ".git").build().toUri()
                .toString();
    }

    private void commitFile(Git git, String fileName) throws GitAPIException, IOException {
        Path repoPath = git.getRepository().getWorkTree().toPath();
        Path filePath = repoPath.resolve(fileName);
        FileUtils.writeStringToFile(filePath.toFile(), "Test content for " + fileName, StandardCharsets.UTF_8);
        git.add().addFilepattern(fileName).call();
        GitService.commit(git).setMessage("Add " + fileName).call();
    }

    private void pushToRepo(Git git, String username, String projectKey, String repositorySlug) throws GitAPIException {
        String repoUri = buildRepositoryUri(username, projectKey, repositorySlug);
        git.push().setRemote(repoUri).call();
    }

    // ---- Test Methods ----

    /**
     * Tests that after the due date, both compile and test phases run and test results are processed.
     * The AFTER_DUE_DATE condition causes the test phase (with result paths) to be included,
     * so the build produces test results that are parsed and stored.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBuildWithPhases_allPhasesActive() throws Exception {
        // Create exercise with phases configured up front
        ProgrammingExercise exercise = createExerciseAndWaitForBuilds("test-phases-all", ZonedDateTime.now().plusDays(7));
        String projectKey = exercise.getProjectKey();

        addTestCasesToExercise(exercise);

        // Create student participation via REST API
        userUtilService.changeUser(TEST_PREFIX + "student1");
        // Mock for the initial participation build (may or may not trigger a build)
        mockDockerClientForStudentBuildWithTestResults();

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

        // Move the due date to the past after participation creation so AFTER_DUE_DATE phases are active for the pushed submission.
        exercise.setDueDate(ZonedDateTime.now().minusMinutes(5));
        programmingExerciseRepository.save(exercise);

        // Set up fresh mocks for the actual test build
        mockDockerClientForStudentBuildWithTestResults();

        // Push as instructor (who can push even after due date) to the student's repo.
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        try (Git git = cloneRepository(instructor1.getLogin(), projectKey, student1RepoSlug)) {
            commitFile(git, "Solution.java");
            pushToRepo(git, instructor1.getLogin(), projectKey, student1RepoSlug);
        }

        // Wait for results and verify: partly successful test results contain 1 passing test
        localVCLocalCITestService.testLatestSubmission(participation.getId(), null, 1, false);
    }

    /**
     * Tests that before the due date, only the compile phase runs (ALWAYS condition).
     * The test phase (AFTER_DUE_DATE) is skipped, so no result paths are collected,
     * and the build produces a compile-only result with 0 test cases.
     * With exit code 0, the submission should NOT be marked as build failed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBuildWithPhases_compileOnlyBeforeDueDate() throws Exception {
        // Create exercise with phases configured up front and due date in the future
        ProgrammingExercise exercise = createExerciseAndWaitForBuilds("test-phases-compile", ZonedDateTime.now().plusDays(7));
        String projectKey = exercise.getProjectKey();

        addTestCasesToExercise(exercise);

        // Create student participation
        userUtilService.changeUser(TEST_PREFIX + "student1");
        mockDockerClientForStudentBuildWithTestResults();

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

        // Mock for the compile-only build:
        // Due date is 7 days in the future -> only ALWAYS phases run -> no result paths -> compile-only
        // The results directory mock throws NotFoundException since no test results exist
        mockDockerClientForCompileOnlyBuild();
        mockDockerExecExitCode(0L);

        // Clone, commit, and push to trigger a compile-only build
        try (Git git = cloneRepository(student1.getLogin(), projectKey, student1RepoSlug)) {
            commitFile(git, "CompileOnly.java");
            pushToRepo(git, student1.getLogin(), projectKey, student1RepoSlug);
        }

        // Wait for the result and verify compile-only success
        // Cannot use testLatestSubmission() because it expects 13 test cases when buildFailed=false
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            var submission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participation.getId());
            return submission.isPresent() && submission.get().getLatestResult() != null;
        });

        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participation.getId())
                .orElseThrow();
        assertThat(programmingSubmission.isBuildFailed()).as("Compile-only build with exit code 0 should not be marked as failed").isFalse();

        Result result = programmingSubmission.getLatestResult();
        assertThat(result).isNotNull();
        assertThat(result.getTestCaseCount()).as("Compile-only build should have 0 test cases").isZero();
        assertThat(result.getPassedTestCaseCount()).as("Compile-only build should have 0 passed test cases").isZero();
    }

    /**
     * Tests that a compile failure before the due date is correctly detected.
     * Only the compile phase runs (ALWAYS condition), and the build script exits with
     * a non-zero code. The submission should be marked as build failed.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testBuildWithPhases_compileFailureBeforeDueDate() throws Exception {
        // Create exercise with phases configured up front and due date in the future
        ProgrammingExercise exercise = createExerciseAndWaitForBuilds("test-phases-fail", ZonedDateTime.now().plusDays(7));
        String projectKey = exercise.getProjectKey();

        addTestCasesToExercise(exercise);

        // Create student participation
        userUtilService.changeUser(TEST_PREFIX + "student1");
        mockDockerClientForStudentBuildWithTestResults();

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/participations", null, StudentParticipation.class,
                HttpStatus.CREATED);
        assertThat(participation).isNotNull();

        String student1RepoSlug = projectKey.toLowerCase() + "-" + student1.getLogin();

        // Mock for the compile failure build:
        // Due date is 7 days in the future -> only ALWAYS phases run -> compile-only
        // Exit code 1 -> build failure
        mockDockerClientForCompileOnlyBuild();
        mockDockerExecExitCode(1L);

        // Clone, commit, and push to trigger a build that will fail
        try (Git git = cloneRepository(student1.getLogin(), projectKey, student1RepoSlug)) {
            commitFile(git, "BrokenCode.java");
            pushToRepo(git, student1.getLogin(), projectKey, student1RepoSlug);
        }

        // Wait for the result and verify build failure
        await().atMost(Duration.ofSeconds(30)).until(() -> {
            var submission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participation.getId());
            return submission.isPresent() && submission.get().getLatestResult() != null;
        });

        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participation.getId())
                .orElseThrow();
        assertThat(programmingSubmission.isBuildFailed()).as("Compile build with non-zero exit code should be marked as failed").isTrue();

        Result result = programmingSubmission.getLatestResult();
        assertThat(result).isNotNull();
        assertThat(result.getTestCaseCount()).as("Failed compile build should have 0 test cases").isZero();
        assertThat(result.getPassedTestCaseCount()).as("Failed compile build should have 0 passed test cases").isZero();
    }
}
