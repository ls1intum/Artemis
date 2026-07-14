package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.buildagent.dto.SandboxSessionSpec;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

class InteractiveSandboxServiceHostConfigTest {

    private BuildAgentConfiguration buildAgentConfiguration;

    private DockerClient dockerClient;

    private CreateContainerCmd createContainerCmd;

    private StartContainerCmd startContainerCmd;

    private final ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        dockerClient = mock(DockerClient.class);
        createContainerCmd = mock(CreateContainerCmd.class);
        CreateContainerResponse response = new CreateContainerResponse();
        response.setId("container-1");
        startContainerCmd = mock(StartContainerCmd.class);

        doReturn(true).when(buildAgentConfiguration).isDockerAvailable();
        doReturn(dockerClient).when(buildAgentConfiguration).getDockerClient();
        doReturn(HostConfig.newHostConfig()).when(buildAgentConfiguration).hostConfig();
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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration);

        service.createSession(new SandboxSessionSpec("image", new DockerRunConfig(List.of(), "none", 0, 0, 0)));

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        HostConfig hostConfig = hostConfigCaptor.getValue();
        assertThat(hostConfig.getNetworkMode()).isEqualTo("none");
        assertThat(hostConfig.getSecurityOpts()).containsExactly("no-new-privileges");
        assertThat(hostConfig.getCapDrop()).containsExactly(Capability.ALL);
        assertThat(hostConfig.getAutoRemove()).isFalse();
    }

    @Test
    void createSession_defaultsToNoNetworkWhenRunConfigIsAbsent() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration);

        service.createSession(new SandboxSessionSpec("image", null));

        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        assertThat(hostConfigCaptor.getValue().getNetworkMode()).isEqualTo("none");
    }

    @Test
    void createSession_rejectsNetworkModesOtherThanNone() {
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration);

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.createSession(new SandboxSessionSpec("image", new DockerRunConfig(List.of(), "host", 0, 0, 0))))
                .withMessageContaining("only allow Docker network mode 'none'");
    }

    @Test
    void createSession_removesContainerWhenStartFails() {
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        doThrow(new RuntimeException("start failed")).when(startContainerCmd).exec();
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration);

        assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> service.createSession(new SandboxSessionSpec("image", null))).withMessage("start failed");

        verify(removeContainerCmd).withForce(true);
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
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration);
        ReflectionTestUtils.setField(service, "buildAgentShortName", "agent");

        int removed = service.removeSessionsForCurrentAgent();

        assertThat(removed).isOne();
        verify(dockerClient).removeContainerCmd("own-sandbox-id");
    }

    @Test
    void capturedOutput_keepsTheLatestOutputWhenTheLimitIsExceeded() {
        StringBuilder output = new StringBuilder();

        InteractiveSandboxService.appendBounded(output, "a".repeat(80_000));
        InteractiveSandboxService.appendBounded(output, "b".repeat(80_000));
        InteractiveSandboxService.appendBounded(output, "final compiler error");
        String truncated = InteractiveSandboxService.truncateTail(output.toString());

        assertThat(truncated.endsWith("final compiler error")).isTrue();
    }

    @Test
    void destroySession_keepsTrackingAndReportsDockerRemovalFailure() {
        RemoveContainerCmd removeContainerCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-1")).thenReturn(removeContainerCmd);
        when(removeContainerCmd.withForce(true)).thenReturn(removeContainerCmd);
        doThrow(new RuntimeException("Docker daemon unavailable")).when(removeContainerCmd).exec();
        InteractiveSandboxService service = new InteractiveSandboxService(buildAgentConfiguration);
        service.markActive("container-1");

        assertThatExceptionOfType(LocalCIException.class).isThrownBy(() -> service.destroySession("container-1")).withMessageContaining("container-1");
        assertThat(service.lastActivity("container-1")).isPresent();
    }
}
