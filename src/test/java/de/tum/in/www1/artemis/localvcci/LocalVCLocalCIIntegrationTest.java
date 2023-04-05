package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;

import com.github.dockerjava.api.DockerClient;

import de.tum.in.www1.artemis.AbstractSpringIntegrationLocalCILocalVCTest;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.Result;
import de.tum.in.www1.artemis.domain.enumeration.ProjectType;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.util.GitUtilService;

class LocalVCLocalCIIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "localvclocalciintegration";

    @Autowired
    private ProgrammingExerciseRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private DockerClient mockDockerClient;

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    @Autowired
    private ProgrammingSubmissionRepository programmingSubmissionRepository;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    private Path templateRepositoryFolder;

    private Git templateGit;

    private Path testsRepositoryFolder;

    private Git testsGit;

    private Path remoteAssignmentRepositoryFolder;

    private Git remoteAssignmentGit;

    private String assignmentRepoName;

    private Path localAssignmentRepositoryFolder;

    private Git localAssignmentGit;

    @BeforeEach
    void initTestCase() throws Exception {
        database.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        String studentLogin = TEST_PREFIX + "student1";

        Course course = database.addCourseWithOneProgrammingExercise();
        programmingExercise = database.getFirstExerciseWithType(course, ProgrammingExercise.class);
        addTestCases();
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        programmingExercise.setProjectType(ProjectType.PLAIN_GRADLE);
        programmingExercise.setAllowOfflineIde(true);
        programmingExercise.setTestRepositoryUrl(
                localVCSBaseUrl + "/git/" + programmingExercise.getProjectKey().toUpperCase() + "/" + programmingExercise.getProjectKey().toLowerCase() + "-tests.git");
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsById(programmingExercise.getId()).get();
        participation = database.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        final String repoName = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        participation.setRepositoryUrl(String.format(localVCSBaseUrl + "/git/%s/%s.git", programmingExercise.getProjectKey(), repoName));
        participation.setBranch(defaultBranch);
        programmingExerciseStudentParticipationRepository.save(participation);

        // Create template and tests repository
        final String templateRepoName = programmingExercise.getProjectKey().toLowerCase() + "-exercise";
        templateRepositoryFolder = createRepositoryFolderInTempDirectory(programmingExercise.getProjectKey(), templateRepoName);
        ClassPathResource templateResource = new ClassPathResource("test-data/java-templates/exercise");
        templateGit = createGitRepository(templateRepositoryFolder, templateResource.getFile());
        final String testsRepoName = programmingExercise.getProjectKey().toLowerCase() + "-tests";
        testsRepositoryFolder = createRepositoryFolderInTempDirectory(programmingExercise.getProjectKey(), testsRepoName);
        ClassPathResource testsResource = new ClassPathResource("test-data/java-templates/tests");
        testsGit = createGitRepository(testsRepositoryFolder, testsResource.getFile());

        // Create remote assignment repository
        assignmentRepoName = (programmingExercise.getProjectKey() + "-" + studentLogin).toLowerCase();
        remoteAssignmentRepositoryFolder = createRepositoryFolderInTempDirectory(programmingExercise.getProjectKey(), assignmentRepoName);
        ClassPathResource assignmentResource = new ClassPathResource("test-data/java-templates/exercise");
        remoteAssignmentGit = createGitRepository(remoteAssignmentRepositoryFolder, assignmentResource.getFile());

        // Clone the remote assignment repository into a local folder.
        localAssignmentRepositoryFolder = Files.createTempDirectory("localAssignment");
        localAssignmentGit = Git.cloneRepository().setURI(remoteAssignmentRepositoryFolder.toString()).setDirectory(localAssignmentRepositoryFolder.toFile()).call();
    }

    private void addTestCases() {
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

    private Path createRepositoryFolderInTempDirectory(String projectKey, String repositorySlug) {
        String tempDir = System.getProperty("java.io.tmpdir");

        Path projectFolder = Paths.get(tempDir, projectKey);

        // Create the project folder if it does not exist.
        try {
            if (!Files.exists(projectFolder)) {
                Files.createDirectories(projectFolder);
            }
        }
        catch (IOException e) {
            throw new RuntimeException(e);
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

    private Git createGitRepository(Path repositoryFolder, File resourcesFolder) throws IOException, GitAPIException, URISyntaxException {

        // Initialize bare Git repository in the repository folder.
        Git remoteGit = Git.init().setDirectory(repositoryFolder.toFile()).setBare(true).call();
        modifyDefaultBranch(remoteGit);

        // Initialize a non-bare Git repository in a temporary folder.
        Path tempDirectory = Files.createTempDirectory("temp");
        Git localGit = Git.init().setDirectory(tempDirectory.toFile()).call();
        modifyDefaultBranch(localGit);

        // Copy the files from "test/resources/test-data/java-templates/..." to the temporary directory.
        FileUtils.copyDirectory(resourcesFolder, tempDirectory.toFile());
        // Add all files to the Git repository.
        localGit.add().addFilepattern(".").call();
        // Commit the files.
        localGit.commit().setMessage("Initial commit").call();

        // Set the remote to the bare repository.
        localGit.remoteAdd().setName("origin").setUri(new URIish(repositoryFolder.toString())).call();

        // Push the files to the bare repository.
        localGit.push().setRemote("origin").call();

        localGit.close();
        FileUtils.deleteDirectory(tempDirectory.toFile());

        return remoteGit;
    }

    private void modifyDefaultBranch(Git gitHandle) throws IOException {
        Repository repository = gitHandle.getRepository();
        RefUpdate refUpdate = repository.getRefDatabase().newUpdate(Constants.HEAD, false);
        refUpdate.setForceUpdate(true);
        refUpdate.link("refs/heads/" + defaultBranch);
    }

    private String constructLocalVCUrl(String username, String projectKey, String repositorySlug) {
        return "http://" + username + ":" + USER_PASSWORD + "@localhost:" + port + "/git/" + projectKey.toUpperCase() + "/" + repositorySlug + ".git";
    }

    @AfterEach
    void removeRepositories() throws IOException {
        if (templateGit != null) {
            templateGit.close();
        }
        if (testsGit != null) {
            testsGit.close();
        }
        if (remoteAssignmentGit != null) {
            remoteAssignmentGit.close();
        }
        if (localAssignmentGit != null) {
            localAssignmentGit.close();
        }
        if (templateRepositoryFolder != null && Files.exists(templateRepositoryFolder)) {
            FileUtils.deleteDirectory(templateRepositoryFolder.toFile());
        }
        if (testsRepositoryFolder != null && Files.exists(testsRepositoryFolder)) {
            FileUtils.deleteDirectory(testsRepositoryFolder.toFile());
        }
        if (remoteAssignmentRepositoryFolder != null && Files.exists(remoteAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(remoteAssignmentRepositoryFolder.toFile());
        }
        if (localAssignmentRepositoryFolder != null && Files.exists(localAssignmentRepositoryFolder)) {
            FileUtils.deleteDirectory(localAssignmentRepositoryFolder.toFile());
        }
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
    private Map<String, String> createMapFromTestResultsFolder(Path testResultsPath) throws IOException {
        Map<String, String> resultMap = new HashMap<>();
        String testResultsPathString = testResultsPath.toString();

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

        return resultMap;
    }

    @Test
    public void testPushAndReceiveResult_partlySuccessful() throws Exception {
        // Create a file and push the changes to the remote assignment repository.
        Path testJsonFilePath = Path.of(localAssignmentRepositoryFolder.toString(), "src", programmingExercise.getPackageFolderName(), "test.txt");
        gitUtilService.writeEmptyJsonFileToPath(testJsonFilePath);
        localAssignmentGit.add().addFilepattern(".").call();
        RevCommit commit = localAssignmentGit.commit().setMessage("Add test.txt").call();
        String commitHash = commit.getId().getName();

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the commitHash for the assignment repository.
        // XML containing failed test cases for the test results.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/assignment-repository/.git/refs/heads/[^/]+",
                Map.of("assignmentCommitHash", commitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns a dummy commit hash for the tests repository.
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/.git/refs/heads/[^/]+",
                Map.of("testCommitHash", dummyCommitHash));

        // Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XML containing the failed test cases.
        Path failedTestResultsPath = Paths.get("src", "test", "resources", "test-data", "test-results", "java-gradle", "partly-successful");
        localVCLocalCITestService.mockInputStreamReturnedFromContainer(mockDockerClient, "/repositories/test-repository/build/test-results/test",
                createMapFromTestResultsFolder(failedTestResultsPath));

        localAssignmentGit.push().setRemote(constructLocalVCUrl(TEST_PREFIX + "student1", programmingExercise.getProjectKey(), assignmentRepoName)).call();

        // Assert that the latest submission has the correct commit hash and the correct result.
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId()).get();
        assertThat(programmingSubmission.getCommitHash()).isEqualTo(commitHash);
        Result result = programmingSubmission.getLatestResult();
        assertThat(result.getTestCaseCount()).isEqualTo(13);
        assertThat(result.getPassedTestCaseCount()).isEqualTo(1);
    }
}
