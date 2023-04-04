package de.tum.in.www1.artemis.localvcci;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.mockito.ArgumentMatcher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.StartContainerCmd;

@TestConfiguration
public class LocalVCLocalCITestConfig {

    private static final String dummyCommitHash = "1234567890abcdef";

    // private Supplier<String> assignmentRepoCommitHashSupplier = () -> dummyCommitHash;

    // public LocalVCLocalCITestConfig(Supplier<String> assignmentRepoCommitHashSupplier) {
    // this.assignmentRepoCommitHashSupplier = assignmentRepoCommitHashSupplier;
    // }

    // public void setAssignmentRepoCommitHashSupplier(Supplier<String> assignmentRepoCommitHashSupplier) {
    // this.assignmentRepoCommitHashSupplier = assignmentRepoCommitHashSupplier;
    // }

    @Bean
    public DockerClient dockerClient() throws IOException {
        DockerClient dockerClient = mock(DockerClient.class);

        // Mock dockerClient.inspectImageCmd(String dockerImage).exec()
        InspectImageCmd inspectImageCmd = mock(InspectImageCmd.class);
        InspectImageResponse inspectImageResponse = new InspectImageResponse();
        when(dockerClient.inspectImageCmd(anyString())).thenReturn(inspectImageCmd);
        when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);

        // Mock dockerClient.createContainerCmd(String dockerImage).withHostConfig(HostConfig hostConfig).withEnv(String... env).withCmd(String... cmd).exec()
        CreateContainerCmd createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse createContainerResponse = new CreateContainerResponse();
        createContainerResponse.setId("1234567890");
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEnv(anyString(), anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withCmd(anyString(), anyString(), anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.exec()).thenReturn(createContainerResponse);

        // Mock dockerClient.startContainerCmd(String containerId)
        StartContainerCmd startContainerCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(anyString())).thenReturn(startContainerCmd);

        // Mock dockerClient.execCreateCmd(String containerId).withAttachStdout(Boolean attachStdout).withAttachStderr(Boolean attachStderr).withCmd(String... cmd).exec()
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        when(dockerClient.execCreateCmd(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(anyString(), anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateCmdResponse);
        when(execCreateCmdResponse.getId()).thenReturn("1234");

        // Mock dockerClient.execStartCmd(String execId).exec(T resultCallback)
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        when(dockerClient.execStartCmd(anyString())).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(invocation -> {
            // Stub the 'exec' method of the 'ExecStartCmd' to call the 'onComplete' method of the provided 'ResultCallback.Adapter', which simulates the command completing
            // immediately.
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        });

        // Mock dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec() for retrieving the assignment commit hash
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmdAssignmentCommitHash = mock(CopyArchiveFromContainerCmd.class);
        String assignmentRepoCommitHash = dummyCommitHash;// assignmentRepoCommitHashSupplier.get();
        InputStream assignmentCommitHashInputStream = createInputStreamWithCommitHash(assignmentRepoCommitHash);
        ArgumentMatcher<String> expectedPathMatcherAssignmentRepository = path -> {
            String regexPattern = "/repositories/assignment-repository/.git/refs/heads/[^/]+";
            return path.matches(regexPattern);
        };
        when(dockerClient.copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcherAssignmentRepository))).thenReturn(copyArchiveFromContainerCmdAssignmentCommitHash);
        when(copyArchiveFromContainerCmdAssignmentCommitHash.exec()).thenReturn(assignmentCommitHashInputStream);

        // Mock dockerClient.copyArchiveFromContainerCmd(String containerId, String resource).exec() for retrieving the tests commit hash
        CopyArchiveFromContainerCmd copyArchiveFromContainerCmdTestsCommitHash = mock(CopyArchiveFromContainerCmd.class);
        InputStream testsCommitHashInputStream = createInputStreamWithCommitHash(dummyCommitHash);
        ArgumentMatcher<String> expectedPathMatcherTestsRepository = path -> {
            String regexPattern = "/repositories/test-repository/.git/refs/heads/[^/]+";
            return path.matches(regexPattern);
        };
        when(dockerClient.copyArchiveFromContainerCmd(anyString(), argThat(expectedPathMatcherTestsRepository))).thenReturn(copyArchiveFromContainerCmdAssignmentCommitHash);
        when(copyArchiveFromContainerCmdTestsCommitHash.exec()).thenReturn(testsCommitHashInputStream);

        return dockerClient;
    }

    private InputStream createInputStreamWithCommitHash(String commitHash) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        TarArchiveOutputStream tarArchiveOutputStream = new TarArchiveOutputStream(byteArrayOutputStream);

        byte[] commitHashBytes = (commitHash + "\n").getBytes(StandardCharsets.UTF_8);

        TarArchiveEntry tarEntry = new TarArchiveEntry("commit");
        tarEntry.setSize(commitHashBytes.length); // Add 1 for the newline character
        tarArchiveOutputStream.putArchiveEntry(tarEntry);
        tarArchiveOutputStream.write(commitHashBytes);
        tarArchiveOutputStream.closeArchiveEntry();
        tarArchiveOutputStream.close();

        return new BufferedInputStream(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
    }
}
