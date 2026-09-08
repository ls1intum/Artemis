package de.tum.cit.aet.artemis.hyperionworker.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.InspectImageCmd;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;

import de.tum.cit.aet.artemis.hyperion.runtime.agent.SandboxUnavailableException;
import de.tum.cit.aet.artemis.hyperionworker.config.WorkerSettings;

class DockerSandboxTest {

    private static final String IMAGE_ID = "sha256:" + "a".repeat(64);

    private DockerClient dockerClient;

    private CreateContainerCmd createContainerCmd;

    private StartContainerCmd startContainerCmd;

    private InspectImageCmd inspectImageCmd;

    private InspectImageResponse inspectImageResponse;

    private final ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        createContainerCmd = mock(CreateContainerCmd.class, org.mockito.Mockito.RETURNS_SELF);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("container-1");
        startContainerCmd = mock(StartContainerCmd.class);
        inspectImageCmd = mock(InspectImageCmd.class);
        inspectImageResponse = org.mockito.Mockito.spy(new InspectImageResponse().withConfig(new ContainerConfig()));
        org.mockito.Mockito.doReturn(IMAGE_ID).when(inspectImageResponse).getId();

        when(dockerClient.inspectImageCmd(IMAGE_ID)).thenReturn(inspectImageCmd);
        when(inspectImageCmd.exec()).thenReturn(inspectImageResponse);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEntrypoint()).thenReturn(createContainerCmd);
        when(createContainerCmd.withCmd(any(String[].class))).thenReturn(createContainerCmd);
        when(createContainerCmd.exec()).thenReturn(response);
        when(dockerClient.startContainerCmd("container-1")).thenReturn(startContainerCmd);
    }

    @Test
    void createSession_enforcesNoNetworkAndSandboxHardening() {
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));

        service.createSession();

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        HostConfig hostConfig = hostConfigCaptor.getValue();
        assertThat(hostConfig.getNetworkMode()).isEqualTo("none");
        assertThat(hostConfig.getSecurityOpts()).containsExactly("no-new-privileges");
        assertThat(hostConfig.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(hostConfig.getAutoRemove()).isFalse();
        assertThat(hostConfig.getTmpFs()).containsEntry("/workspace", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m");
        assertThat(hostConfig.getTmpFs()).containsEntry("/tmp", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=512m");
        assertThat(hostConfig.getTmpFs()).containsEntry("/opt/hyperion", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=256m");
        assertThat(hostConfig.getTmpFs()).containsEntry("/opt/hyperion-readiness-fixture", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=64m");
        assertThat(hostConfig.getReadonlyRootfs()).isTrue();
    }

    @Test
    void createSessionRunsAnInitProcessAsPidOneSoTheContainerStopsOnSignal() {
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));

        service.createSession();

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        assertThat(hostConfigCaptor.getValue().getInit()).isTrue();
    }

    @Test
    void createSessionBoundsImageDeclaredVolumesWithTmpfs() {
        inspectImageResponse.withConfig(new ContainerConfig().withVolumes(Map.of("/var/cache/compiler", Map.of())));
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));

        service.createSession();

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        assertThat(hostConfigCaptor.getValue().getTmpFs()).containsEntry("/var/cache/compiler", "rw,exec,nosuid,nodev,uid=1000,gid=1000,mode=0700,size=256m");
    }

    @Test
    void createSession_removesContainerWhenStartFails() {
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        doThrow(new RuntimeException("start failed")).when(startContainerCmd).exec();
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> service.createSession()).withMessage("start failed");

        verify(removeContainerCmd).withForce(true);
        verify(removeContainerCmd).withRemoveVolumes(true);
        verify(removeContainerCmd).exec();
    }

    @Test
    void createSessionReportsSurvivingContainerWhenStartAndCleanupFail() {
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        RuntimeException startFailure = new RuntimeException("start response lost");
        RuntimeException cleanupFailure = new RuntimeException("cleanup failed");
        doThrow(startFailure).when(startContainerCmd).exec();
        doThrow(cleanupFailure).when(removeContainerCmd).exec();
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));

        assertThatExceptionOfType(DockerSandbox.SessionCreationException.class).isThrownBy(() -> service.createSession()).satisfies(failure -> {
            assertThat(failure.containerId).isEqualTo("container-1");
            assertThat(failure.getCause()).isSameAs(startFailure);
            assertThat(failure.getCause().getSuppressed()).containsExactly(cleanupFailure);
        });
    }

    @Test
    void capturedOutput_keepsTheLatestOutputWhenTheLimitIsExceeded() {
        DockerSandbox.BoundedOutput output = new DockerSandbox.BoundedOutput();

        output.append("a".repeat(80_000).getBytes(StandardCharsets.UTF_8));
        output.append("b".repeat(80_000).getBytes(StandardCharsets.UTF_8));
        output.append("final compiler error".getBytes(StandardCharsets.UTF_8));

        assertThat(output.snapshot()).endsWith("final compiler error");
    }

    @Test
    void destroySession_keepsTrackingAndReportsDockerRemovalFailure() {
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        doThrow(new RuntimeException("Docker daemon unavailable")).when(removeContainerCmd).exec();
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));
        service.markActive("container-1");

        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(() -> service.destroySession("container-1")).withMessageContaining("container-1");
        assertThat(service.lastActivity("container-1")).isPresent();
    }

    @Test
    void resetSessionRestartsTheExistingContainer() {
        RestartContainerCmd restartContainerCmd = mock(RestartContainerCmd.class);
        when(dockerClient.restartContainerCmd("container-1")).thenReturn(restartContainerCmd);
        when(restartContainerCmd.withTimeout(DockerSandbox.SESSION_RESET_STOP_GRACE_SECONDS)).thenReturn(restartContainerCmd);
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));
        service.markActive("container-1");

        service.resetSession("container-1");

        verify(restartContainerCmd).withTimeout(DockerSandbox.SESSION_RESET_STOP_GRACE_SECONDS);
        verify(restartContainerCmd).exec();
        assertThat(service.lastActivity("container-1")).isPresent();
    }

    @Test
    void createSessionKeepsPidOneIndependentFromWorkspaceFiles() {
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));

        service.createSession();

        ArgumentCaptor<String[]> command = ArgumentCaptor.forClass(String[].class);
        verify(createContainerCmd).withCmd(command.capture());
        assertThat(String.join(" ", command.getValue())).contains("while :").doesNotContain(".stop_sandbox");
    }

    @Test
    void copyOutStreamsTmpfsContentThroughContainerExec() throws Exception {
        byte[] content = new byte[] { 0, 1, 2, 3, (byte) 0xFF };
        byte[] tarBytes;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream(); TarArchiveOutputStream tar = new TarArchiveOutputStream(buffer)) {
            TarArchiveEntry entry = new TarArchiveEntry("out/result.bin");
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
            tar.finish();
            tarBytes = buffer.toByteArray();
        }

        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(dockerClient.execCreateCmd("container-1")).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(execCreateResponse.getId()).thenReturn("exec-1");
        when(dockerClient.execStartCmd("exec-1")).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(false)).thenReturn(execStartCmd);
        doAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onNext(new Frame(StreamType.STDOUT, tarBytes));
            callback.onComplete();
            return callback;
        }).when(execStartCmd).exec(any());
        when(dockerClient.inspectExecCmd("exec-1")).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));
        service.markActive("container-1");

        try (var archive = service.copyOut("container-1", "/workspace")) {
            assertThat(archive.getNextEntry().getName()).isEqualTo("out/result.bin");
            assertThat(archive.readAllBytes()).containsExactly(content);
        }

        ArgumentCaptor<String[]> command = ArgumentCaptor.forClass(String[].class);
        verify(execCreateCmd).withCmd(command.capture());
        assertThat(command.getValue()).endsWith("sandbox-copy-out", "/workspace");
        assertThat(command.getValue()[2]).contains("[ -n \"$parent\" ] || parent=/").doesNotContain("/workspace");
    }

    @Test
    void execPreservesUtf8CharactersSplitAcrossDockerFrames() {
        byte[] encoded = "compiler says: ä".getBytes(StandardCharsets.UTF_8);
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(dockerClient.execCreateCmd("container-1")).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(execCreateResponse.getId()).thenReturn("exec-1");
        when(dockerClient.execStartCmd("exec-1")).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(false)).thenReturn(execStartCmd);
        doAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onNext(new Frame(StreamType.STDOUT, java.util.Arrays.copyOf(encoded, encoded.length - 1)));
            callback.onNext(new Frame(StreamType.STDOUT, new byte[] { encoded[encoded.length - 1] }));
            callback.onComplete();
            return callback;
        }).when(execStartCmd).exec(any());
        when(dockerClient.inspectExecCmd("exec-1")).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));
        service.markActive("container-1");

        var result = service.exec("container-1", Duration.ofSeconds(1), "echo");

        assertThat(result.stdout()).isEqualTo("compiler says: ä");
    }

    @Test
    void execCallbackFailureInvalidatesTheSession() {
        ExecCreateCmd execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateResponse = mock(ExecCreateCmdResponse.class);
        ExecStartCmd execStartCmd = mock(ExecStartCmd.class);
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.execCreateCmd("container-1")).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(true)).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateResponse);
        when(execCreateResponse.getId()).thenReturn("exec-1");
        when(dockerClient.execStartCmd("exec-1")).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(false)).thenReturn(execStartCmd);
        doAnswer(invocation -> {
            ResultCallback<Frame> callback = invocation.getArgument(0);
            callback.onError(new IllegalStateException("stream failed"));
            return callback;
        }).when(execStartCmd).exec(any());
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        DockerSandbox service = new DockerSandbox(dockerClient,
                new WorkerSettings("worker-1", IMAGE_ID, "runc", 1024 * 1024 * 1024, 100_000, 128, Duration.ofSeconds(10), Duration.ofSeconds(45), Duration.ofSeconds(5)));
        service.markActive("container-1");

        assertThatExceptionOfType(SandboxUnavailableException.class).isThrownBy(() -> service.exec("container-1", Duration.ofSeconds(1), "build", "TOP_SECRET_SOURCE"))
                .withMessageContaining("Sandbox command failed").withMessageNotContaining("TOP_SECRET_SOURCE");

        verify(removeContainerCmd).exec();
        assertThat(service.lastActivity("container-1")).isEmpty();
    }
}
