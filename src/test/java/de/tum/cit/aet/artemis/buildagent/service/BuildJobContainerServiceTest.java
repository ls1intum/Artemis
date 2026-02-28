package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

class BuildJobContainerServiceTest extends AbstractArtemisBuildAgentTest {

    private static final String CONTAINER_NAME = "containerName";

    private static final String IMAGE_NAME = "imageName";

    private static final String BUILD_SCRIPT = "Hello_World();";

    private static final int MAX_XXX_VALUE = 2;

    private static final String DUMMY_CONTAINER_ID = "1234567890";

    @Autowired
    BuildJobContainerService buildJobContainerService;

    @Mock
    CreateContainerCmd createContainerCmd;

    @Captor
    ArgumentCaptor<HostConfig> hostConfigCaptor;

    private ExecCreateCmd execCreateCmd;

    private ExecStartCmd execStartCmd;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(buildJobContainerService, "buildAgentConfiguration", buildAgentConfiguration);
        ReflectionTestUtils.setField(buildJobContainerService, "maxCpuCount", MAX_XXX_VALUE);
        ReflectionTestUtils.setField(buildJobContainerService, "maxMemory", MAX_XXX_VALUE);
        ReflectionTestUtils.setField(buildJobContainerService, "maxMemorySwap", MAX_XXX_VALUE);

        when(buildAgentConfiguration.getDockerClient().createContainerCmd(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withName(anyString())).thenReturn(createContainerCmd);
        when(createContainerCmd.withHostConfig(any())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEnv(anyList())).thenReturn(createContainerCmd);
        when(createContainerCmd.withEntrypoint()).thenReturn(createContainerCmd);
        when(createContainerCmd.withCmd(ArgumentMatchers.any(String[].class))).thenReturn(createContainerCmd);
        when(createContainerCmd.exec()).thenReturn(null);

        // Set up dedicated exec mocks so we can capture withDetach/withAttachStdout/withAttachStderr calls
        execCreateCmd = mock(ExecCreateCmd.class);
        ExecCreateCmdResponse execCreateCmdResponse = mock(ExecCreateCmdResponse.class);
        when(buildAgentConfiguration.getDockerClient().execCreateCmd(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withCmd(any(String[].class))).thenReturn(execCreateCmd);
        when(execCreateCmd.withUser(anyString())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStdout(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.withAttachStderr(anyBoolean())).thenReturn(execCreateCmd);
        when(execCreateCmd.exec()).thenReturn(execCreateCmdResponse);
        when(execCreateCmdResponse.getId()).thenReturn("exec-1234");

        execStartCmd = mock(ExecStartCmd.class);
        when(buildAgentConfiguration.getDockerClient().execStartCmd(anyString())).thenReturn(execStartCmd);
        when(execStartCmd.withDetach(anyBoolean())).thenReturn(execStartCmd);
        when(execStartCmd.exec(any())).thenAnswer(invocation -> {
            ResultCallback.Adapter<?> callback = invocation.getArgument(0);
            callback.onComplete();
            return null;
        });
    }

    private HostConfig captureHostConfig() {
        verify(createContainerCmd).withHostConfig(hostConfigCaptor.capture());
        return hostConfigCaptor.getValue();
    }

    @Test
    void testInvalidValuesReturnsDefaultConfig() {
        var runConfig = new DockerRunConfig(List.of(), "", 0, 0, 0);
        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, runConfig);

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig).isEqualTo(buildAgentConfiguration.hostConfig());
    }

    @Test
    void testClipToMaxValues() {
        var runConfig = new DockerRunConfig(List.of(), "", MAX_XXX_VALUE + 1, MAX_XXX_VALUE + 1, MAX_XXX_VALUE + 1);
        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, runConfig);

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig).isNotNull();
        assertThat(hostConfig.getCpuQuota()).isEqualTo(MAX_XXX_VALUE * buildAgentConfiguration.hostConfig().getCpuPeriod());
        assertThat(hostConfig.getMemory()).isEqualTo(MAX_XXX_VALUE * 1024L * 1024L);
        assertThat(hostConfig.getMemorySwap()).isEqualTo(MAX_XXX_VALUE * 1024L * 1024L);
    }

    @Test
    void testNullNetworkIsIgnored() {
        var runConfig = new DockerRunConfig(List.of(), null, 1, 1, 1);
        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, runConfig);

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig).isNotNull();
        assertThat(hostConfig.getNetworkMode()).isNull();
    }

    @Test
    void testEmptyNetworkIsIgnored() {
        var runConfig = new DockerRunConfig(List.of(), "", 1, 1, 1);
        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, runConfig);

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig).isNotNull();
        assertThat(hostConfig.getNetworkMode()).isNull();
    }

    @Test
    void testNonEmptyNetworkIsTaken() {
        var runConfig = new DockerRunConfig(List.of(), "my-network-name", 1, 1, 1);
        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, runConfig);

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig).isNotNull();
        assertThat(hostConfig.getNetworkMode()).isEqualTo("my-network-name");
    }

    @Test
    void testRunScriptInContainerExecutesSynchronously() {
        buildJobContainerService.runScriptInContainer(DUMMY_CONTAINER_ID, "build-job-1");

        // Verify that the exec command attaches stdout and stderr for synchronous output capture
        verify(execCreateCmd).withAttachStdout(true);
        verify(execCreateCmd).withAttachStderr(true);

        // Verify that the exec start command runs in non-detached (synchronous) mode,
        // which ensures the command completes before control returns to the caller
        verify(execStartCmd).withDetach(false);
    }

    @Test
    void testStopContainerExecutesDetached() {
        // Set up a mock running container so stopContainer can find it
        var listContainersCmd = buildAgentConfiguration.getDockerClient().listContainersCmd().withShowAll(true);
        Container container = mock(Container.class);
        when(container.getNames()).thenReturn(new String[] { "/" + CONTAINER_NAME });
        when(container.getState()).thenReturn("running");
        when(container.getId()).thenReturn(DUMMY_CONTAINER_ID);
        when(listContainersCmd.exec()).thenReturn(List.of(container));

        buildJobContainerService.stopContainer(CONTAINER_NAME);

        // The stop signal (touch stop_container.txt) should be fire-and-forget (detached),
        // because the container's main process will detect the file and stop on its own
        verify(execStartCmd).withDetach(true);
    }

    @Test
    void testSynchronousExecNeverUsesDetachedMode() {
        buildJobContainerService.runScriptInContainer(DUMMY_CONTAINER_ID, "build-job-1");

        // Verify that withDetach(true) is never called for synchronous commands.
        // This guards against regression: previously, setup commands accidentally used detached mode.
        verify(execStartCmd, atLeastOnce()).withDetach(false);
        verify(execStartCmd, never()).withDetach(true);
    }
}
