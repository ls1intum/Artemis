package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.user.UserFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Fail.fail;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionTestRepository;
import de.tum.in.www1.artemis.repository.ResultRepository;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.util.LocalRepository;

/**
 * This class contains helper methods for all tests of the local VC and local CI system..
 */
@Service
public class LocalVCLocalCITestService {

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Value("${artemis.version-control.url}")
    private URL localVCBaseUrl;

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

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
        participation.setRepositoryUri(String.format(localVCBaseUrl + "/git/%s/%s.git", projectKey, repositorySlug));
        participation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(participation);

        return participation;
    }

    /**
     * Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
     *
     * @param dockerClient          the DockerClient to mock.
     * @param mockedTestResultsPath the path to the directory containing the test results in the resources folder.
     * @param testResultsPath       the path to the directory containing the test results inside the container.
     */
    public void mockTestResults(DockerClient dockerClient, Path mockedTestResultsPath, String testResultsPath) throws IOException {
        mockInputStreamReturnedFromContainer(dockerClient, testResultsPath, createMapFromTestResultsFolder(mockedTestResultsPath));
    }

    /**
     * Overloaded version of mockTestResults(DockerClient dockerClient, Path mockedTestResultsPath, String testResultsPath) that allows to mock multiple test result folders.
     *
     * @param dockerClient           the DockerClient to mock.
     * @param mockedTestResultsPaths the paths to the directories containing the test results in the resources folder.
     * @param testResultsPath        the path to the directory containing the test results inside the container.
     */
    public void mockTestResults(DockerClient dockerClient, List<Path> mockedTestResultsPaths, String testResultsPath) throws IOException {
        mockInputStreamReturnedFromContainer(dockerClient, testResultsPath, createMapFromMultipleTestResultFolders(mockedTestResultsPaths));
    }

    /**
     * Mocks the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
     *
     * @param dockerClient         the DockerClient to mock.
     * @param resourceRegexPattern the regex pattern that the resource path must match. The resource path is the path of the file or directory inside the container.
     * @param dataToReturn         the data to return inside the InputStream in form of a map. Each entry of the map will be one TarArchiveEntry with the key denoting the
     *                                 tarArchiveEntry.getName() and the value being the content of the TarArchiveEntry. There can be up to two dataToReturn entries, in which case
     *                                 the first call to "copyArchiveFromContainerCmd().exec()" will return the first entry, and the second call will return the second entry.
     * @throws IOException if the InputStream cannot be created.
     */
    @SafeVarargs
    public final void mockInputStreamReturnedFromContainer(DockerClient dockerClient, String resourceRegexPattern, Map<String, String>... dataToReturn) throws IOException {
        // Mock dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(resourceRegexPattern);
        doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));

        if (dataToReturn.length == 0) {
            throw new IllegalArgumentException("At least one dataToReturn entry must be provided.");
        }

        if (dataToReturn.length > 2) {
            throw new IllegalArgumentException("At most two dataToReturn entries are supported.");
        }

        if (dataToReturn.length == 1) {
            // If only one dataToReturn entry is provided, return it for every call to "copyArchiveFromContainerCmd().exec()"
            doReturn(createInputStreamForTarArchiveFromMap(dataToReturn[0])).when(copyArchiveFromContainerCmd).exec();
        }
        else {
            // If two dataToReturn entries are provided, return the first one for the first call to "copyArchiveFromContainerCmd().exec()" and the second one for the second call to
            // "copyArchiveFromContainerCmd().exec()"
            doReturn(createInputStreamForTarArchiveFromMap(dataToReturn[0])).doReturn(createInputStreamForTarArchiveFromMap(dataToReturn[1])).when(copyArchiveFromContainerCmd)
                    .exec();
        }
    }

    /**
     * Create a BufferedInputStream from a map. Each entry of the map will be one TarArchiveEntry with the key denoting the tarArchiveEntry.getName() and the value being the
     * content.
     * The returned InputStream can be used to mock the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec().
     *
     * @param dataMap the data to return inside the InputStream in form of a map.
     * @return the BufferedInputStream.
     * @throws IOException if any interaction with the TarArchiveOutputStream fails.
     */
    public BufferedInputStream createInputStreamForTarArchiveFromMap(Map<String, String> dataMap) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(byteArrayOutputStream);

        for (Map.Entry<String, String> entry : dataMap.entrySet()) {
            String filePath = entry.getKey();
            String content = entry.getValue();

            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);

            TarArchiveEntry tarEntry = new TarArchiveEntry(filePath);
            tarEntry.setSize(contentBytes.length);
            tarArchiveOutputStream.putArchiveEntry(tarEntry);
            tarArchiveOutputStream.write(contentBytes);
            tarArchiveOutputStream.closeArchiveEntry();
        }

        tarArchiveOutputStream.close();

        return new BufferedInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
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
        Path localRepositoryFolder = createRepositoryFolderInTempDirectory(projectKey, repositorySlug);
        LocalRepository repository = new LocalRepository(defaultBranch);
        repository.configureRepos("localRepo", localRepositoryFolder);
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
    private Path createRepositoryFolderInTempDirectory(String projectKey, String repositorySlug) throws IOException {
        String tempDir = System.getProperty("java.io.tmpdir");

        Path projectFolder = Paths.get(tempDir, projectKey);

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
     * @return the URL to the repository.
     */
    public String constructLocalVCUrl(String username, String projectKey, String repositorySlug) {
        return constructLocalVCUrl(username, USER_PASSWORD, projectKey, repositorySlug);
    }

    /**
     * Construct a repository URI that works with the local VC system.
     *
     * @param username       the username of the user that tries to access the repository using this URL.
     * @param password       the password of the user that tries to access the repository using this URL.
     * @param projectKey     the project key of the repository.
     * @param repositorySlug the repository slug of the repository.
     * @return the URL to the repository.
     */
    public String constructLocalVCUrl(String username, String password, String projectKey, String repositorySlug) {
        return "http://" + username + (!password.isEmpty() ? ":" : "") + password + (!username.isEmpty() ? "@" : "") + "localhost:" + port + "/git/" + projectKey.toUpperCase()
                + "/" + repositorySlug + ".git";
    }

    /**
     * Create a map from the files in a folder containing test results.
     * This map contains one entry for each file in the folder, the key being the file path and the value being the content of the file in case it is an XML file.
     * This map is used by localVCLocalCITestService.mockInputStreamReturnedFromContainer() to mock the InputStream returned by dockerClient.copyArchiveFromContainerCmd() and thus
     * mocks the retrieval of test results from the Docker container.
     *
     * @param testResultsPath Path to the folder containing the test results.
     * @return Map containing the file paths and the content of the files.
     */
    public Map<String, String> createMapFromTestResultsFolder(Path testResultsPath) throws IOException {
        Map<String, String> resultMap = new HashMap<>();
        String testResultsPathString = testResultsPath.toString();

        if (Files.isDirectory(testResultsPath)) {
            Files.walkFileTree(testResultsPath, new SimpleFileVisitor<>() {

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!attrs.isDirectory()) {
                        String key = file.toString().replace(testResultsPathString, "test");
                        String value;
                        if (file.getFileName().toString().endsWith(".xml")) {
                            value = new String(Files.readAllBytes(file));
                        }
                        else {
                            value = "dummy-data";
                        }
                        resultMap.put(key, value);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        else {
            // If it's a file, handle it directly
            String key = testResultsPath.toString();
            String value = Files.isRegularFile(testResultsPath) && testResultsPath.toString().endsWith(".xml") ? new String(Files.readAllBytes(testResultsPath)) : "dummy-data";
            resultMap.put(key, value);
        }

        return resultMap;
    }

    /**
     * Overloaded version of createMapFromTestResultsFolder(Path testResultsPath) that allows to create a map from multiple test result folders.
     *
     * @param testResultsPaths Paths to the folders containing the test results.
     * @return Map containing the file paths and the content of the files.
     */
    public Map<String, String> createMapFromMultipleTestResultFolders(List<Path> testResultsPaths) throws IOException {
        Map<String, String> resultMap = new HashMap<>();
        for (Path testResultsPath : testResultsPaths) {
            resultMap.putAll(createMapFromTestResultsFolder(testResultsPath));
        }
        return resultMap;
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
        catch (GitAPIException e) {
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

    private void performFetch(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException {
        String repositoryUri = constructLocalVCUrl(username, password, projectKey, repositorySlug);
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
        try {
            performPush(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug);
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
     */
    public void testLatestSubmission(Long participationId, String expectedCommitHash, int expectedSuccessfulTestCaseCount, boolean buildFailed, boolean isStaticCodeAnalysisEnabled,
            int expectedCodeIssueCount) {
        // wait for result to be persisted
        await().until(() -> resultRepository.findFirstByParticipationIdOrderByCompletionDateDesc(participationId).isPresent());
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        List<ProgrammingSubmission> submissions = programmingSubmissionRepository.findAllByParticipationIdWithResults(participationId);
        log.info("Expected commit hash: " + expectedCommitHash);
        for (ProgrammingSubmission submission : submissions) {
            log.info("Submission with commit hash: " + submission.getCommitHash());
        }
        await().until(() -> {
            // get the latest valid submission (!ILLEGAL and with results) of the participation
            SecurityContextHolder.getContext().setAuthentication(auth);
            var submission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderByLegalSubmissionDateDesc(participationId);
            return submission.orElseThrow().getLatestResult() != null;
        });
        // get the latest valid submission (!ILLEGAL and with results) of the participation
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdWithResultsOrderByLegalSubmissionDateDesc(participationId)
                .orElseThrow();
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
        testLatestSubmission(participationId, expectedCommitHash, expectedSuccessfulTestCaseCount, buildFailed, false, 0);
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
        String repositoryUri = constructLocalVCUrl(username, password, projectKey, repositorySlug);
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
    public void verifyRepositoryFoldersExist(ProgrammingExercise programmingExercise, String localVCBasePath) {
        LocalVCRepositoryUri templateRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTemplateRepositoryUri(), localVCBaseUrl);
        assertThat(templateRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();
        LocalVCRepositoryUri solutionRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getSolutionRepositoryUri(), localVCBaseUrl);
        assertThat(solutionRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();
        LocalVCRepositoryUri testsRepositoryUri = new LocalVCRepositoryUri(programmingExercise.getTestRepositoryUri(), localVCBaseUrl);
        assertThat(testsRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();
    }
}
