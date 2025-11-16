package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

class BuildJobContainerServiceTest extends AbstractArtemisBuildAgentTest {

    private static final String CONTAINER_NAME = "containerName";

    private static final String IMAGE_NAME = "imageName";

    private static final String BUILD_SCRIPT = "Hello_World();";

    private static final int MAX_XXX_VALUE = 2;

    @MockitoSpyBean
    BuildJobContainerService buildJobContainerService;

    @Mock
    CreateContainerCmd createContainerCmd;

    @Captor
    ArgumentCaptor<HostConfig> hostConfigCaptor;

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
}
