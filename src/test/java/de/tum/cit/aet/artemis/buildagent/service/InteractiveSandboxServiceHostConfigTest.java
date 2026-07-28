package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.RestartContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerConfig;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpecDTO;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

class InteractiveSandboxServiceHostConfigTest {

    private static final String IMAGE = "registry.example/artemis-java:stable";

    private static final String IMAGE_ID = "sha256:" + "a".repeat(64);

    private BuildAgentConfiguration buildAgentConfiguration;

    private BuildAgentDockerService buildAgentDockerService;

    private DockerClient dockerClient;

    private CreateContainerCmd createContainerCmd;

    private StartContainerCmd startContainerCmd;

    private InspectImageCmd inspectImageCmd;

    private InspectImageResponse inspectImageResponse;

    private final ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        buildAgentDockerService = mock(BuildAgentDockerService.class);
        dockerClient = mock(DockerClient.class);
        createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("container-1");
        startContainerCmd = mock(StartContainerCmd.class);
        inspectImageCmd = mock(InspectImageCmd.class);
        inspectImageResponse = new InspectImageResponse().withConfig(new ContainerConfig());

        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        doReturn(dockerClient).when(buildAgentConfiguration).getDockerClient();
        doReturn(limitedHostConfig()).when(buildAgentConfiguration).hostConfig();
        when(buildAgentDockerService.ensureDockerImageAvailable(IMAGE)).thenReturn(IMAGE_ID);
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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        service.createSession(new SandboxSessionSpecDTO(IMAGE, new DockerRunConfig(List.of(), "none", 0, 0, 0)));

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        HostConfig hostConfig = hostConfigCaptor.getValue();
        assertThat(hostConfig.getNetworkMode()).isEqualTo("none");
        assertThat(hostConfig.getSecurityOpts()).containsExactly("no-new-privileges");
        assertThat(hostConfig.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(hostConfig.getAutoRemove()).isFalse();
        assertThat(hostConfig.getTmpFs()).containsEntry("/workspace", "rw,exec,nosuid,nodev,size=512m");
        assertThat(hostConfig.getTmpFs()).containsEntry("/tmp", "rw,exec,nosuid,nodev,size=512m");
        assertThat(hostConfig.getTmpFs()).containsEntry("/opt/hyperion", "rw,exec,nosuid,nodev,size=256m");
        assertThat(hostConfig.getTmpFs()).containsEntry("/opt/hyperion-readiness-fixture", "rw,exec,nosuid,nodev,size=64m");
        assertThat(hostConfig.getReadonlyRootfs()).isTrue();
    }

    /**
     * The container command is a bare shell loop, and the kernel discards signals a PID 1 has no handler for. Without Docker's init forwarding it, the SIGTERM that
     * {@link InteractiveSandboxService#resetSession} sends is ignored and every reset waits out the whole stop grace before Docker escalates to SIGKILL.
     */
    @Test
    void createSessionRunsAnInitProcessAsPidOneSoTheContainerStopsOnSignal() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        service.createSession(new SandboxSessionSpecDTO(IMAGE, null));

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        assertThat(hostConfigCaptor.getValue().getInit()).isTrue();
    }

    @Test
    void createSession_defaultsToNoNetworkWhenRunConfigIsAbsent() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        service.createSession(new SandboxSessionSpecDTO(IMAGE, null));

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        assertThat(hostConfigCaptor.getValue().getNetworkMode()).isEqualTo("none");
    }

    @Test
    void createSessionBoundsImageDeclaredVolumesWithTmpfs() {
        inspectImageResponse.withConfig(new ContainerConfig().withVolumes(Map.of("/var/cache/compiler", Map.of())));
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        service.createSession(new SandboxSessionSpecDTO(IMAGE, null));

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        assertThat(hostConfigCaptor.getValue().getTmpFs()).containsEntry("/var/cache/compiler", "rw,exec,nosuid,nodev,size=256m");
    }

    @Test
    void createSessionRejectsMissingResourceLimits() {
        doReturn(HostConfig.newHostConfig()).when(buildAgentConfiguration).hostConfig();
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.createSession(new SandboxSessionSpecDTO(IMAGE, null)))
                .withMessageContaining("require positive CPU, memory, and PID limits");
    }

    private static HostConfig limitedHostConfig() {
        return HostConfig.newHostConfig().withCpuQuota(200_000L).withCpuPeriod(100_000L).withMemory(2L * 1024 * 1024 * 1024).withMemorySwap(2L * 1024 * 1024 * 1024)
                .withPidsLimit(1000L);
    }

    @Test
    void createSession_rejectsNetworkModesOtherThanNone() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.createSession(new SandboxSessionSpecDTO(IMAGE, new DockerRunConfig(List.of(), "host", 0, 0, 0))))
                .withMessageContaining("only allow Docker network mode 'none'");
    }

    @Test
    void createSession_removesContainerWhenStartFails() {
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        doThrow(new RuntimeException("start failed")).when(startContainerCmd).exec();
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> service.createSession(new SandboxSessionSpecDTO(IMAGE, null))).withMessage("start failed");

        verify(removeContainerCmd).withForce(true);
        verify(removeContainerCmd).withRemoveVolumes(true);
        verify(removeContainerCmd).exec();
    }

    @Test
    void removeSessionsForCurrentAgentRemovesOnlyThisAgentsSandboxContainers() {
        Container ownSandboxContainer = mock(Container.class);
        doReturn("own-sandbox-id").when(ownSandboxContainer).getId();
        doReturn(new String[] { "/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + "agent-7e916dbb-838e-4fac-852d-3854762812eb" }).when(ownSandboxContainer).getNames();
        Container otherAgentSandboxContainer = mock(Container.class);
        doReturn("other-sandbox-id").when(otherAgentSandboxContainer).getId();
        doReturn(new String[] { "/" + InteractiveSandboxService.SANDBOX_CONTAINER_PREFIX + "agent-a-67eb8e66-3163-4f3d-b65f-8f47e129fe41" }).when(otherAgentSandboxContainer)
                .getNames();
        Container otherContainer = mock(Container.class);
        doReturn("other-id").when(otherContainer).getId();
        doReturn(new String[] { "/local-ci-build" }).when(otherContainer).getNames();
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(List.of(ownSandboxContainer, otherAgentSandboxContainer, otherContainer));
        when(dockerClient.removeContainerCmd("own-sandbox-id")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
        ReflectionTestUtils.setField(service, "buildAgentShortName", "agent");

        int removed = service.removeSessionsForCurrentAgent();

        assertThat(removed).isOne();
        verify(dockerClient).removeContainerCmd("own-sandbox-id");
        verify(removeContainerCmd).withRemoveVolumes(true);
    }

    @Test
    void capturedOutput_keepsTheLatestOutputWhenTheLimitIsExceeded() {
        InteractiveSandboxService.BoundedOutput output = new InteractiveSandboxService.BoundedOutput();

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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
        service.markActive("container-1");

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.destroySession("container-1")).withMessageContaining("container-1");
        assertThat(service.lastActivity("container-1")).isPresent();
    }

    @Test
    void createSession_resolvesTheConfiguredImageBeforeCreatingFromItsImmutableId() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        service.createSession(new SandboxSessionSpecDTO(IMAGE, null));

        InOrder imageThenContainer = inOrder(buildAgentDockerService, dockerClient);
        imageThenContainer.verify(buildAgentDockerService).ensureDockerImageAvailable(IMAGE);
        imageThenContainer.verify(dockerClient).createContainerCmd(IMAGE_ID);
    }

    @Test
    void resetSessionRestartsTheExistingContainer() {
        RestartContainerCmd restartContainerCmd = mock(RestartContainerCmd.class);
        when(dockerClient.restartContainerCmd("container-1")).thenReturn(restartContainerCmd);
        when(restartContainerCmd.withTimeout(InteractiveSandboxService.SESSION_RESET_STOP_GRACE_SECONDS)).thenReturn(restartContainerCmd);
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
        service.markActive("container-1");

        service.resetSession("container-1");

        verify(restartContainerCmd).withTimeout(InteractiveSandboxService.SESSION_RESET_STOP_GRACE_SECONDS);
        verify(restartContainerCmd).exec();
        assertThat(service.lastActivity("container-1")).isPresent();
    }

    @Test
    void createSessionKeepsPidOneIndependentFromWorkspaceFiles() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);

        service.createSession(new SandboxSessionSpecDTO(IMAGE, null));

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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration, buildAgentDockerService);
        service.markActive("container-1");

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.exec("container-1", Duration.ofSeconds(1), "build", "TOP_SECRET_SOURCE"))
                .withMessageContaining("Sandbox command failed").withMessageNotContaining("TOP_SECRET_SOURCE");

        verify(removeContainerCmd).exec();
        assertThat(service.lastActivity("container-1")).isEmpty();
    }
}
