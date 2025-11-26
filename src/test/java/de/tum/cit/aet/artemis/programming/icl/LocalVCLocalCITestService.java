package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.user.util.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Fail.fail;
import static org.awaitility.Awaitility.await;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationVCSAccessToken;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;

/**
 * This class contains helper methods for all tests of the local VC and local CI system..
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class LocalVCLocalCITestService {

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    @Autowired
    private ParticipantScoreScheduleService participantScoreScheduleService;

    @Autowired
    private ResultTestRepository resultRepository;

    @Value("${artemis.version-control.url}")
    private URI localVCBaseUri;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCBasePath;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    private static final int DEFAULT_AWAITILITY_TIMEOUT_IN_SECONDS = 10;

    private static final Logger log = LoggerFactory.getLogger(LocalVCLocalCITestService.class);

    // Cannot inject {local.server.port} here, because it is not available at the time this class is instantiated.
    private int port;

    public void setPort(int port) {
        this.port = port;
    }

    public String getRepositorySlug(String projectKey, String repositoryTypeOrUserName) {
        return (projectKey + "-" + repositoryTypeOrUserName).toLowerCase();
    }

    /**
     * Create a participation for a given user in a given programming exercise.
     *
     * @param programmingExercise the programming exercise.
     * @param userLogin           the user login.
     * @return the participation.
     */
    public ProgrammingExerciseStudentParticipation createParticipation(ProgrammingExercise programmingExercise, String userLogin) {
        String projectKey = programmingExercise.getProjectKey();
        String repositorySlug = getRepositorySlug(projectKey, userLogin);
        ProgrammingExerciseStudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userLogin);
        participation.setRepositoryUri(String.format(localVCBaseUri + "/git/%s/%s.git", projectKey, repositorySlug));
        participation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(participation);

        log.debug("Created participation with id: {}", participation.getId());
        return participation;
    }

    /**
     * Create the standard test cases for a programming exercise and save them in the database.
     *
     * @param programmingExercise the programming exercise for which the test cases should be created.
     */
    public void addTestCases(ProgrammingExercise programmingExercise) {
        // Clean up existing test cases
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[SortStrategy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testAttributes[Context]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testAttributes[Policy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[MergeSort]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testClass[BubbleSort]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testConstructors[Policy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Context]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[Policy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMethods[SortStrategy]").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testMergeSort()").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testUseBubbleSortForSmallList()").weight(1.0).active(true).exercise(programmingExercise)
                .visibility(Visibility.ALWAYS).bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testUseMergeSortForBigList()").weight(1.0).active(true).exercise(programmingExercise)
                .visibility(Visibility.ALWAYS).bonusMultiplier(1D).bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("testBubbleSort()").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS)
                .bonusMultiplier(1D).bonusPoints(0D));

        testCaseRepository.saveAll(testCases);

        List<ProgrammingExerciseTestCase> tests = new ArrayList<>(testCaseRepository.findByExerciseId(programmingExercise.getId()));
        assertThat(tests).as("test case is initialized").hasSize(13);
    }

    /**
     * Create and configure a LocalRepository that works for the local VC system, i.e. the remote folder adheres to the folder structure required for local VC and the remote
     * repository is bare.
     *
     * @param projectKey     the project key of the exercise this repository is to be created for.
     * @param repositorySlug the repository slug of the repository to be created (e.g. "someprojectkey-solution" or "someprojectkey-practice-student1").
     * @return the configured LocalRepository that contains Git handles to the remote and local repository.
     */
    public LocalRepository createAndConfigureLocalRepository(String projectKey, String repositorySlug) throws GitAPIException, IOException, URISyntaxException {
        Path localRepositoryFolder = createRepositoryFolder(projectKey, repositorySlug);
        LocalRepository repository = new LocalRepository(defaultBranch);
        repository.configureRepos(localVCBasePath, "localRepo", localRepositoryFolder);

        // Create an initial commit in both the working copy and bare repository
        // so that copy operations and other Git operations that require a HEAD commit work correctly
        de.tum.cit.aet.artemis.programming.service.GitService.commit(repository.workingCopyGitRepo).setMessage("Initial commit").setAllowEmpty(true).call();
        repository.workingCopyGitRepo.push().call();

        return repository;
    }

    /**
     * Create a folder in the temporary directory with the project key as its name and another folder inside there that gets the name of the repository slug + ".git".
     * This is consistent with the repository folder structure used for the local VC system (though the repositories for the local VC system are not saved in the temporary
     * directory).
     *
     * @param projectKey     the project key of the repository.
     * @param repositorySlug the repository slug of the repository.
     * @return the path to the repository folder.
     */
    private Path createRepositoryFolder(String projectKey, String repositorySlug) throws IOException {

        Path projectFolder = localVCBasePath.resolve(projectKey);

        // Create the project folder if it does not exist.
        if (!Files.exists(projectFolder)) {
            Files.createDirectories(projectFolder);
        }

        // Create the repository folder.
        Path repositoryFolder = projectFolder.resolve(repositorySlug + ".git");
        try {
            Files.createDirectories(repositoryFolder);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }

        return repositoryFolder;
    }

    /**
     * Construct a repository URI that works with the local VC system.
     *
     * @param username       the username of the user that tries to access the repository using this URL.
     * @param projectKey     the project key of the repository.
     * @param repositorySlug the repository slug of the repository.
     * @return the URL as string to the repository.
     */
    public String buildLocalVCUri(String username, String projectKey, String repositorySlug) {
        return buildLocalVCUri(username, USER_PASSWORD, projectKey, repositorySlug);
    }

    /**
     * Construct a repository URI that works with the local VC system.
     *
     * @param username       the username of the user that tries to access the repository using this URL.
     * @param password       the password of the user that tries to access the repository using this URL.
     * @param projectKey     the project key of the repository.
     * @param repositorySlug the repository slug of the repository.
     * @return the URL as string to the repository.
     */
    public String buildLocalVCUri(@Nullable String username, @Nullable String password, @NonNull String projectKey, @NonNull String repositorySlug) {
        String userInfo = null;

        if (StringUtils.hasText(username)) {
            userInfo = username;
            if (StringUtils.hasText(password)) {
                userInfo += ":" + password;
            }
        }
        return UriComponentsBuilder.fromUri(localVCBaseUri).port(port).userInfo(userInfo).pathSegment("git", projectKey.toUpperCase(), repositorySlug + ".git").build().toUri()
                .toString();
    }

    /**
     * Create a file in the local repository and commit it.
     *
     * @param localRepositoryFolder the path to the local repository.
     * @param localGit              the Git object for the local repository.
     * @return the commit hash.
     * @throws Exception if the file could not be created or committed.
     */
    public String commitFile(Path localRepositoryFolder, Git localGit) throws Exception {
        return commitFile(localRepositoryFolder, localGit, "new-file.txt");
    }

    /**
     * Create a file in the local repository and commit it.
     *
     * @param localRepositoryFolder the path to the local repository.
     * @param localGit              the Git object for the local repository.
     * @param fileName              the name of the file to be created.
     * @return the commit hash.
     * @throws IOException     if the file could not be created
     * @throws GitAPIException if the file could not be committed.
     */
    public String commitFile(Path localRepositoryFolder, Git localGit, String fileName) throws GitAPIException, IOException {
        Path testFilePath = localRepositoryFolder.resolve(fileName);
        Files.createFile(testFilePath);
        localGit.add().addFilepattern(".").call();
        RevCommit commit = GitService.commit(localGit).setMessage("Add " + fileName).call();
        return commit.getId().getName();
    }

    /**
     * Perform a fetch operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testFetchSuccessful(Git repositoryHandle, String username, String projectKey, String repositorySlug) {
        testFetchSuccessful(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug);
    }

    /**
     * Perform a fetch operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param password         the password of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testFetchSuccessful(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) {
        try {
            performFetch(repositoryHandle, username, password, projectKey, repositorySlug);
        }
        catch (GitAPIException | URISyntaxException e) {
            fail("Fetching was not successful: " + e.getMessage());
        }
    }

    /**
     * Perform a fetch operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testFetchReturnsError(Git repositoryHandle, String username, String projectKey, String repositorySlug, String expectedMessage) {
        testFetchReturnsError(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug, expectedMessage);
    }

    /**
     * Perform a fetch operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to fetch from the repository.
     * @param password         the password of the user that tries to fetch from the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testFetchReturnsError(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug, String expectedMessage) {
        testFetchThrowsException(repositoryHandle, username, password, projectKey, repositorySlug, TransportException.class, expectedMessage);
    }

    /**
     * Perform a fetch operation and fail if there was no exception.
     *
     * @param repositoryHandle  the Git object for the repository.
     * @param username          the username of the user that tries to fetch from the repository.
     * @param password          the password of the user that tries to fetch from the repository.
     * @param projectKey        the project key of the repository.
     * @param repositorySlug    the repository slug of the repository.
     * @param expectedException the expected exception.
     * @param expectedMessage   the expected message of the exception.
     * @param <T>               the type of the expected exception.
     */
    public <T extends Exception> void testFetchThrowsException(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug,
            Class<T> expectedException, String expectedMessage) {
        assertThatExceptionOfType(expectedException).isThrownBy(() -> performFetch(repositoryHandle, username, password, projectKey, repositorySlug))
                .withMessageContaining(expectedMessage);
    }

    private void performFetch(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException, URISyntaxException {
        String repositoryUri = buildLocalVCUri(username, password, projectKey, repositorySlug);
        FetchCommand fetchCommand = repositoryHandle.fetch();
        // Set the remote URL.
        fetchCommand.setRemote(repositoryUri);
        // Set the refspec to fetch all branches.
        fetchCommand.setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
        // Execute the fetch.
        fetchCommand.call();
    }

    /**
     * Perform a push operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testPushSuccessful(Git repositoryHandle, String username, String projectKey, String repositorySlug) {
        testPushSuccessful(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug);
    }

    /**
     * Perform a push operation and fail if there was an exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param password         the password or token of the user
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     */
    public void testPushSuccessful(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) {
        try {
            performPush(repositoryHandle, username, password, projectKey, repositorySlug);
        }
        catch (GitAPIException e) {
            fail("Pushing was not successful: " + e.getMessage());
        }
    }

    /**
     * Assert that the latest submission has the correct commit hash and the correct result.
     *
     * @param participationId                 of the participation to check the latest submission for.
     * @param expectedCommitHash              the commit hash of the commit that triggered the creation of the submission and is thus expected to be saved in the submission. Null
     *                                            if the commit hash should not be checked.
     * @param expectedSuccessfulTestCaseCount the expected number or passed test cases.
     * @param buildFailed                     whether the build should have failed or not.
     * @param isStaticCodeAnalysisEnabled     whether static code analysis is enabled for the exercise.
     * @param expectedCodeIssueCount          the expected number of code issues (only relevant if static code analysis is enabled).
     * @param timeoutInSeconds                the maximum time to wait for the result to be persisted. If null, the default timeout of 10s is used.
     */
    public void testLatestSubmission(Long participationId, String expectedCommitHash, int expectedSuccessfulTestCaseCount, boolean buildFailed, boolean isStaticCodeAnalysisEnabled,
            int expectedCodeIssueCount, Integer timeoutInSeconds) {
        // wait for result to be persisted
        Duration timeoutDuration = timeoutInSeconds != null ? Duration.ofSeconds(timeoutInSeconds) : Duration.ofSeconds(DEFAULT_AWAITILITY_TIMEOUT_IN_SECONDS);
        await().atMost(timeoutDuration).until(() -> {
            participantScoreScheduleService.executeScheduledTasks();
            await().until(participantScoreScheduleService::isIdle);
            return resultRepository.findFirstWithSubmissionsByParticipationIdOrderByCompletionDateDesc(participationId).isPresent();
        });
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);
        log.info("Expected commit hash: {}", expectedCommitHash);
        for (ProgrammingSubmission submission : submissions) {
            log.info("Submission with commit hash: {}", submission.getCommitHash());
        }
        await().until(() -> {
            // get the latest valid submission (with results) of the participation
            SecurityContextHolder.getContext().setAuthentication(auth);
            var submission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId);
            return submission.orElseThrow().getLatestResult() != null;
        });
        // get the latest valid submission (with results) of the participation
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderBySubmissionDateDesc(participationId).orElseThrow();
        if (expectedCommitHash != null) {
            assertThat(programmingSubmission.getCommitHash()).isEqualTo(expectedCommitHash);
        }
        assertThat(programmingSubmission.isBuildFailed()).isEqualTo(buildFailed);
        Result result = programmingSubmission.getLatestResult();
        assertThat(result).isNotNull();
        int expectedTestCaseCount = buildFailed ? 0 : 13;
        assertThat(result.getTestCaseCount()).isEqualTo(expectedTestCaseCount);
        assertThat(result.getPassedTestCaseCount()).isEqualTo(expectedSuccessfulTestCaseCount);

        if (isStaticCodeAnalysisEnabled) {
            assertThat(result.getCodeIssueCount()).isEqualTo(expectedCodeIssueCount);
        }
    }

    public void testLatestSubmission(Long participationId, String expectedCommitHash, int expectedSuccessfulTestCaseCount, boolean buildFailed) {
        testLatestSubmission(participationId, expectedCommitHash, expectedSuccessfulTestCaseCount, buildFailed, false, 0, null);
    }

    public void testLatestSubmission(Long participationId, String expectedCommitHash, int expectedSuccessfulTestCaseCount, boolean buildFailed, int timeoutInSeconds) {
        testLatestSubmission(participationId, expectedCommitHash, expectedSuccessfulTestCaseCount, buildFailed, false, 0, timeoutInSeconds);
    }

    /**
     * Perform a push operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testPushReturnsError(Git repositoryHandle, String username, String projectKey, String repositorySlug, String expectedMessage) {
        testPushReturnsError(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug, expectedMessage);
    }

    /**
     * Perform a push operation and fail if there was no exception.
     *
     * @param repositoryHandle the Git object for the repository.
     * @param username         the username of the user that tries to push to the repository.
     * @param password         the password of the user that tries to push to the repository.
     * @param projectKey       the project key of the repository.
     * @param repositorySlug   the repository slug of the repository.
     * @param expectedMessage  the expected message of the exception.
     */
    public void testPushReturnsError(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug, String expectedMessage) {
        assertThatExceptionOfType(TransportException.class).isThrownBy(() -> performPush(repositoryHandle, username, password, projectKey, repositorySlug))
                .withMessageContaining(expectedMessage);
    }

    private void performPush(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException {
        String repositoryUri = buildLocalVCUri(username, password, projectKey, repositorySlug);
        PushCommand pushCommand = repositoryHandle.push();
        // Set the remote URL.
        pushCommand.setRemote(repositoryUri);
        // Execute the push.
        pushCommand.call();
    }

    /**
     * Assert that the base repository folders were created correctly for a programming exercise.
     *
     * @param programmingExercise the programming exercise.
     * @param localVCBasePath     the base path for the local repositories taken from the artemis.version-control.local-vcs-repo-path environment variable.
     */
    public void verifyRepositoryFoldersExist(ProgrammingExercise programmingExercise, Path localVCBasePath) {
        LocalVCRepositoryUri templateRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri());
        assertThat(templateRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();
        LocalVCRepositoryUri solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri());
        assertThat(solutionRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();
        LocalVCRepositoryUri testsRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri());
        assertThat(testsRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();
    }

    /**
     * Gets the participationVcsAccessToken belonging to a user and a participation
     *
     * @param user                       The user
     * @param programmingParticipationId The participation's id
     *
     * @return the participationVcsAccessToken of the user for the given participationId
     */
    public ParticipationVCSAccessToken getParticipationVcsAccessToken(User user, Long programmingParticipationId) {
        return participationVcsAccessTokenService.findByUserAndParticipationIdOrElseThrow(user, programmingParticipationId);
    }

    /**
     * Deletes the participationVcsAccessToken for a participation
     *
     * @param participationId The participationVcsAccessToken's participationId
     */
    public void deleteParticipationVcsAccessToken(long participationId) {
        participationVcsAccessTokenService.deleteByParticipationId(participationId);
    }

    /**
     * Creates the participationVcsAccessToken for a user and a participation
     *
     * @param user            The user for which the token should get created
     * @param participationId The participationVcsAccessToken's participationId
     */
    public void createParticipationVcsAccessToken(User user, long participationId) {
        participationVcsAccessTokenService.createVcsAccessTokenForUserAndParticipationIdOrElseThrow(user, participationId);
    }

    /**
     * Deletes a programmingParticipation
     *
     * @param programmingParticipation The participation to delete
     */
    public void deleteParticipation(ProgrammingExerciseStudentParticipation programmingParticipation) {
        programmingExerciseStudentParticipationRepository.delete(programmingParticipation);
    }
}
