package de.tum.cit.aet.artemis.programming.icl;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_TEST_BUILDAGENT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.mockito.ArgumentMatcher;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.DisconnectFromNetworkCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RemoveImageCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.command.VersionCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Version;

import de.tum.cit.aet.artemis.buildagent.service.BuildAgentDockerService;

@Lazy
@Service
@Profile({ SPRING_PROFILE_TEST, PROFILE_TEST_BUILDAGENT })
public class DockerClientTestService {

    /**
     * Mock a DockerClient with the necessary methods for the BuildAgentDockerService to work.
     *
     * @return the mocked DockerClient.
     */
    public static DockerClient mockDockerClient() throws InterruptedException {
        DockerClient dockerClient = mock(DockerClient.class);

        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        inspectImageResponse.withArch("amd64");
        when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
        when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);

        // Mock PullImageCmd
        PullImageCmd pullImageCmd = mock(PullImageCmd.class);
        when(dockerClient.pullImageCmd(anyString())).thenReturn(pullImageCmd);
        when(pullImageCmd.withPlatform(anyString())).thenReturn(pullImageCmd);
        BuildAgentDockerService.MyPullImageResultCallback callback1 = mock(BuildAgentDockerService.MyPullImageResultCallback.class);
        when(pullImageCmd.exec(any(BuildAgentDockerService.MyPullImageResultCallback.class))).thenReturn(callback1);
        when(callback1.awaitCompletion()).thenReturn(null);

        String dummyContainerId = "1234567890";

        mockCreateContainerCmd(dockerClient, dummyContainerId, null);

        // Mock dockerClient.startContainerCmd(String containerId)
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);

        // Mock dockerClient.copyArchiveToContainer(String containerId).withRemotePath(String path).withTarInputStream(InputStream uploadStream).exec()
        CopyArchiveToContainerCmd copyArchiveToContainerCmd = mock(CopyArchiveToContainerCmd.class);
        when(dockerClient.copyArchiveToContainerCmd(anyString())).thenReturn(copyArchiveToContainerCmd);
        when(copyArchiveToContainerCmd.withRemotePath(anyString())).thenReturn(copyArchiveToContainerCmd);
        when(copyArchiveToContainerCmd.withTarInputStream(any())).thenReturn(copyArchiveToContainerCmd);
        doNothing().when(copyArchiveToContainerCmd).exec();

        // Mock dockerClient.execCreateCmd(String containerId).withAttachStdout(Boolean attachStdout).withAttachStderr(Boolean attachStderr).withCmd(String... cmd).exec()
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        when(dockerClient.execCreateCmd(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withUser(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateCmdResponse);
        when(execCreateCmdResponse.getId()).thenReturn("1234");

        // Mock dockerClient.execStartCmd(String execId).exec(T resultCallback)
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(anyString())).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(anyBoolean())).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(invocation -> {
            // Stub the 'exec' method of the 'ExecStartCmd' to call the 'onComplete' method of the provided 'ResultCallback.Adapter', which simulates the command completing
            // immediately.
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        });

        // Mock dockerClient.inspectExecCmd(String execId).exec() for exit code retrieval
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(dockerClient.inspectExecCmd(anyString())).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);

        // Mock listContainerCmd() method.
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(anyBoolean())).thenReturn(listContainersCmd);

        // Mock container class
        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[] { "dummy-container-name" });
        when(container.getImageId()).thenReturn("dummy-image-id");
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        // Mock listImagesCmd() method.
        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
        when(dockerClient.listImagesCmd()).thenReturn(listImagesCmd);
        Image image = mock(Image.class);
        when(image.getId()).thenReturn("test-image-id");
        when(image.getRepoTags()).thenReturn(new String[] { "test-image-name" });
        when(listImagesCmd.exec()).thenReturn(List.of(image));

        // Mock removeImageCmd method.
        RemoveImageCmd removeImageCmd = mock(RemoveImageCmd.class);
        when(dockerClient.removeImageCmd(anyString())).thenReturn(removeImageCmd);
        doNothing().when(removeImageCmd).exec();

        // Mock removeContainerCmd
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd(anyString())).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);

        // Mock stopContainerCmd
        StopContainerCmd stopContainerCmd = mock(StopContainerCmd.class);
        when(dockerClient.stopContainerCmd(anyString())).thenReturn(stopContainerCmd);
        when(stopContainerCmd.withTimeout(any())).thenReturn(stopContainerCmd);

        // Mock killContainerCmd
        KillContainerCmd killContainerCmd = mock(KillContainerCmd.class);
        when(dockerClient.killContainerCmd(anyString())).thenReturn(killContainerCmd);

        // Mock DisconnectFromNetworkCmd
        DisconnectFromNetworkCmd disconnectFromNetworkCmd = mock(DisconnectFromNetworkCmd.class);
        when(dockerClient.disconnectFromNetworkCmd()).thenReturn(disconnectFromNetworkCmd);
        when(disconnectFromNetworkCmd.withContainerId(anyString())).thenReturn(disconnectFromNetworkCmd);
        when(disconnectFromNetworkCmd.withNetworkId(anyString())).thenReturn(disconnectFromNetworkCmd);

        // Mock versionCmd for BuildAgentInformationService.updateDockerVersion()
        mockVersionCmd(dockerClient, "24.0.0-test");

        return dockerClient;
    }

    /**
     * Mock dockerClient.versionCmd().exec() to return a Version with the specified version string.
     *
     * @param dockerClient  the DockerClient to mock
     * @param versionString the Docker version string to return
     */
    public static void mockVersionCmd(DockerClient dockerClient, String versionString) {
        VersionCmd versionCmd = mock(VersionCmd.class);
        Version version = mock(Version.class);
        when(dockerClient.versionCmd()).thenReturn(versionCmd);
        when(versionCmd.exec()).thenReturn(version);
        when(version.getVersion()).thenReturn(versionString);
    }

    /**
     * Mock dockerClient.createContainerCmd(String dockerImage).withHostConfig(HostConfig hostConfig).withEnv(String... env).withEntrypoint().withCmd(String... cmd).exec()
     *
     * @param dockerClient     the DockerClient to mock.
     * @param dummyContainerId the ID of the container to return when the container is created.
     * @param image            the image to use for the container. Can be null, in which case will match any string.
     */
    public static void mockCreateContainerCmd(DockerClient dockerClient, String dummyContainerId, String image) {
        // Mock dockerClient.createContainerCmd(String dockerImage).withHostConfig(HostConfig hostConfig).withEnv(String... env).withEntrypoint().withCmd(String... cmd).exec()
        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createContainerResponse = new CreateContainerResponse();
        createContainerResponse.setId(dummyContainerId);
        if (image != null) {
            when(dockerClient.createContainerCmd(eq(image))).thenReturn(createContainerCmd);
        }
        else {
            when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        }
        when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);
        when(createContainerCmd.withUser(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEntrypoint()).thenReturn(createContainerCmd);
        when(createContainerCmd.withCmd(anyString(), anyString(), anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.exec()).thenReturn(createContainerResponse);
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
     * Mock dockerClient.copyArchiveFromContainerCmd() such that it returns the XMLs containing the test results.
     *
     * @param dockerClient          the DockerClient to mock.
     * @param mockedTestResultsPath the path to the directory containing the test results in the resources folder.
     * @param testResultsPath       the path to the directory containing the test results inside the container.
     * @param containerId           the ID of the container to mock the test results for.
     */
    public void mockTestResultsForContainer(DockerClient dockerClient, Path mockedTestResultsPath, String testResultsPath, String containerId) throws IOException {
        mockInputStreamReturnedFromContainer(dockerClient, containerId, testResultsPath, createMapFromTestResultsFolder(mockedTestResultsPath));
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
     * Mocks the inspection of the image returned by dockerClient.inspectImageCmd(String imageId).exec().
     * The mocked image inspection will have the architecture "amd64" to pass the check in LocalCIBuildService.
     *
     * @param dockerClient the DockerClient to mock.
     */
    public void mockInspectImage(DockerClient dockerClient) {
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        inspectImageResponse.withArch("amd64");

        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        doReturn(inspectImageCmd).when(dockerClient).inspectImageCmd(anyString());
        doReturn(inspectImageResponse).when(inspectImageCmd).exec();
    }

    /**
     * Mocks the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
     *
     * @param dockerClient         the DockerClient to mock.
     * @param resourceRegexPattern the regex pattern that the resource path must match. The resource path is the path of the file or directory inside the container.
     * @param dataToReturn         the data to return inside the InputStream in form of a map. Each entry of the map will be one TarArchiveEntry with the key denoting the
     *                                 tarArchiveEntry.name() and the value being the content of the TarArchiveEntry. There can be up to two dataToReturn entries, in which case
     *                                 the first call to "copyArchiveFromContainerCmd().exec()" will return the first entry, and the second call will return the second entry.
     * @throws IOException if the InputStream cannot be created.
     */
    @SafeVarargs
    public final void mockInputStreamReturnedFromContainer(DockerClient dockerClient, String resourceRegexPattern, Map<String, String>... dataToReturn) throws IOException {
        mockInputStreamReturnedFromContainer(dockerClient, null, resourceRegexPattern, dataToReturn);
    }

    /**
     * Mocks the InputStream returned by dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
     *
     * @param dockerClient         the DockerClient to mock.
     * @param containerId          the ID of the container to mock the InputStream for.
     * @param resourceRegexPattern the regex pattern that the resource path must match. The resource path is the path of the file or directory inside the container.
     * @param dataToReturn         the data to return inside the InputStream in form of a map. Each entry of the map will be one TarArchiveEntry with the key denoting the
     *                                 tarArchiveEntry.name() and the value being the content of the TarArchiveEntry. There can be up to two dataToReturn entries, in which case
     *                                 the first call to "copyArchiveFromContainerCmd().exec()" will return the first entry, and the second call will return the second entry.
     * @throws IOException if the InputStream cannot be created.
     */
    @SafeVarargs
    public final void mockInputStreamReturnedFromContainer(DockerClient dockerClient, String containerId, String resourceRegexPattern, Map<String, String>... dataToReturn)
            throws IOException {
        // Mock dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec()
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmd = mock(CopyArchiveFromContainerCmd.class);
        ArgumentMatcher<String> expectedPathMatcher = path -> path.matches(resourceRegexPattern);
        if (containerId == null) {
            doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcher));
        }
        else {
            doReturn(copyArchiveFromContainerCmd).when(dockerClient).copyArchiveFromContainerCmd(eq(containerId), argThat(expectedPathMatcher));
        }

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
     * Create a BufferedInputStream from a map. Each entry of the map will be one TarArchiveEntry with the key denoting the tarArchiveEntry.name() and the value being the
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
}
