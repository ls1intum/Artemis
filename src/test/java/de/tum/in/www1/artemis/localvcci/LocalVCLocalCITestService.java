package de.tum.in.www1.artemis.localvcci;

import static de.tum.in.www1.artemis.util.ModelFactory.USER_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
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
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingExerciseTestCase;
import de.tum.in.www1.artemis.domain.enumeration.Visibility;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseTestCaseRepository;

@Service
public class LocalVCLocalCITestService {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    private int port;

    public void setPort(int port) {
        this.port = port;
    }

    @Autowired
    private ProgrammingExerciseTestCaseRepository testCaseRepository;

    /**
     * Mocks the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
     *
     * @param mockDockerClient     the mocked DockerClient.
     * @param resourceRegexPattern the regex pattern that the resource path must match. The resource path is the path of the file or directory inside the container.
     * @param dataToReturn         the data to return inside the InputStream in form of a map. Each entry will be one TarArchiveEntry with the key denoting the
     *                                 tarArchiveEntry.getName() and the value being the content of the TarArchiveEntry.
     * @throws IOException if the InputStream cannot be created.
     */
    public void mockInputStreamReturnedFromContainer(DockerClient mockDockerClient, String resourceRegexPattern, Map<String, String> dataToReturn) throws IOException {
        // Mock dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        BufferedInputStream dataInputStream = createInputStreamForTarArchiveFromMap(dataToReturn);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(resourceRegexPattern);
        doReturn(copyArchiveFromContainerCmd).when(mockDockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        when(copyArchiveFromContainerCmd.exec()).thenReturn(dataInputStream);
    }

    private BufferedInputStream createInputStreamForTarArchiveFromMap(Map<String, String> dataMap) throws IOException {
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

    public Path createRepositoryFolderInTempDirectory(String projectKey, String repositorySlug) {
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

    public Git createGitRepository(Path repositoryFolder, File resourcesFolder) throws IOException, GitAPIException, URISyntaxException {

        // Initialize bare Git repository in the repository folder.
        Git remoteGit = Git.init().setDirectory(repositoryFolder.toFile()).setBare(true).call();
        modifyDefaultBranch(remoteGit);

        // Initialize a non-bare Git repository in a temporary folder.
        Path tempDirectory = Files.createTempDirectory("temp");
        Git localGit = Git.init().setDirectory(tempDirectory.toFile()).call();
        modifyDefaultBranch(localGit);

        // Copy the files from "test/resources/test-data/java-templates/..." to the temporary directory.
        if (resourcesFolder != null) {
            FileUtils.copyDirectory(resourcesFolder, tempDirectory.toFile());
        }
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

    public String constructLocalVCUrl(String username, String projectKey, String repositorySlug) {
        return constructLocalVCUrl(username, USER_PASSWORD, projectKey, repositorySlug);
    }

    private String constructLocalVCUrl(String username, String password, String projectKey, String repositorySlug) {
        return "http://" + username + (password.length() > 0 ? ":" : "") + password + "@localhost:" + port + "/git/" + projectKey.toUpperCase() + "/" + repositorySlug + ".git";
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

    public void testFetchSuccessful(Git repositoryHandle, String username, String projectKey, String repositorySlug) {
        testFetchSuccessful(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug);
    }

    public void testFetchSuccessful(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) {
        try {
            performFetch(repositoryHandle, username, password, projectKey, repositorySlug);
        }
        catch (GitAPIException e) {
            fail("Fetching was not successful: " + e.getMessage());
        }
    }

    public <T extends Exception> void testFetchThrowsException(Git repositoryHandle, String username, String projectKey, String repositorySlug, Class<T> expectedException,
            String expectedMessage) {
        testFetchThrowsException(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug, expectedException, expectedMessage);
    }

    public <T extends Exception> void testFetchThrowsException(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug,
            Class<T> expectedException, String expectedMessage) {
        T exception = assertThrows(expectedException, () -> performFetch(repositoryHandle, username, password, projectKey, repositorySlug));
        assertThat(exception.getMessage()).contains(expectedMessage);
    }

    private void performFetch(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException {
        String repositoryUrl = constructLocalVCUrl(username, password, projectKey, repositorySlug);
        FetchCommand fetchCommand = repositoryHandle.fetch();
        // Set the remote URL.
        fetchCommand.setRemote(repositoryUrl);
        // Set the refspec to fetch all branches.
        fetchCommand.setRefSpecs(new RefSpec("+refs/heads/*:refs/remotes/origin/*"));
        // Execute the fetch.
        fetchCommand.call();
    }

    public <T extends Exception> void testPushThrowsException(Git repositoryHandle, String username, String projectKey, String repositorySlug, Class<T> expectedException,
            String expectedMessage) {
        testPushThrowsException(repositoryHandle, username, USER_PASSWORD, projectKey, repositorySlug, expectedException, expectedMessage);
    }

    public <T extends Exception> void testPushThrowsException(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug,
            Class<T> expectedException, String expectedMessage) {
        T exception = assertThrows(expectedException, () -> performPush(repositoryHandle, username, password, projectKey, repositorySlug));
        assertThat(exception.getMessage()).contains(expectedMessage);
    }

    private void performPush(Git repositoryHandle, String username, String password, String projectKey, String repositorySlug) throws GitAPIException {
        String repositoryUrl = constructLocalVCUrl(username, password, projectKey, repositorySlug);
        PushCommand pushCommand = repositoryHandle.push();
        // Set the remote URL.
        pushCommand.setRemote(repositoryUrl);
        // Execute the push.
        pushCommand.call();
    }
}
