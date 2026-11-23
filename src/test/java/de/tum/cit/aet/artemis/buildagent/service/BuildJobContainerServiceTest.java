package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveToContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;

import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.localci.exception.ContainerNetworkNotAllowedException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;
import de.tum.cit.aet.artemis.shared.base.AbstractArtemisBuildAgentTest;

class BuildJobContainerServiceTest extends AbstractArtemisBuildAgentTest {

    private static final String CONTAINER_NAME = "containerName";

    private static final String IMAGE_NAME = "imageName";

    private static final String BUILD_SCRIPT = "Hello_World();";

    private static final int MAX_XXX_VALUE = 2;

    private static final String DUMMY_CONTAINER_ID = "1234567890";

    @Autowired
    BuildJobContainerService buildJobContainerService;

    CreateContainerCmd createContainerCmd;

    ArgumentCaptor<HostConfig> hostConfigCaptor;

    private ExecCreateCmd execCreateCmd;

    private ExecStartCmd execStartCmd;

    @BeforeEach
    void setUp() {
        createContainerCmd = mock(CreateContainerCmd.class);
        hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);
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

        // Mock inspectExecCmd to return exit code 0 (success) by default
        InspectExecCmd inspectExecCmd = mock(InspectExecCmd.class);
        InspectExecResponse inspectExecResponse = mock(InspectExecResponse.class);
        when(buildAgentConfiguration.getDockerClient().inspectExecCmd(anyString())).thenReturn(inspectExecCmd);
        when(inspectExecCmd.exec()).thenReturn(inspectExecResponse);
        when(inspectExecResponse.getExitCodeLong()).thenReturn(0L);
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
    void testNetworkOfTheHostOrAnotherContainerIsRejected() {
        for (String network : List.of("host", "HOST", " host ", "container:other", "Container:other")) {
            var runConfig = new DockerRunConfig(List.of(), network, 1, 1, 1);
            assertThatThrownBy(() -> buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, runConfig)).isInstanceOf(LocalCIException.class);
        }
    }

    @Test
    void testNetworkOutsideTheAllowedListOfTheAgentIsRejected() {
        Object previous = ReflectionTestUtils.getField(buildJobContainerService, "allowedCustomNetworks");
        ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", List.of("mirror-only"));
        try {
            var notListed = new DockerRunConfig(List.of(), "my-network-name", 1, 1, 1);
            assertThatThrownBy(() -> buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, notListed))
                    .isInstanceOf(ContainerNetworkNotAllowedException.class);

            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "mirror-only", 1, 1, 1));
            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "none", 1, 1, 1));
        }
        finally {
            ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", previous);
        }
    }

    @Test
    void testStorageLimitIsAppliedToTheDefaultAndTheAdjustedHostConfig() {
        ReflectionTestUtils.setField(buildAgentConfiguration, "storageLimitBytes", 1024L);
        try {
            Assertions.assertThat(buildAgentConfiguration.hostConfig().getStorageOpt()).containsEntry("size", "1024");

            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "my-network-name", 1, 1, 1));
            Assertions.assertThat(captureHostConfig().getStorageOpt()).containsEntry("size", "1024");
        }
        finally {
            ReflectionTestUtils.setField(buildAgentConfiguration, "storageLimitBytes", null);
        }
    }

    @Test
    void testRestrictiveAgentListRefusesABuildWithoutNetworkExceptWhenItNamesTheDefaultNetwork() {
        Object previous = ReflectionTestUtils.getField(buildJobContainerService, "allowedCustomNetworks");
        try {
            ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", List.of("mirror-only"));
            for (String network : new String[] { null, "", "  " }) {
                var withoutNetwork = new DockerRunConfig(List.of(), network, 1, 1, 1);
                assertThatThrownBy(() -> buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, withoutNetwork))
                        .isInstanceOf(ContainerNetworkNotAllowedException.class);
            }
            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "none", 1, 1, 1));
            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "mirror-only", 1, 1, 1));

            // a list that names the default network of Docker allows a build without a network
            ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", List.of("mirror-only", "bridge"));
            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), null, 1, 1, 1));

            // without a list on the agent nothing changes
            ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", List.of());
            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), null, 1, 1, 1));
            buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "", 1, 1, 1));
        }
        finally {
            ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", previous);
        }
    }

    @Test
    void testAnAllowedNetworkListWithoutAnyNetworkIsAConfigurationError() {
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.continuous-integration.container-flags-limit.allowed-custom-networks[0]", "  ");
        Object previous = ReflectionTestUtils.getField(buildJobContainerService, "allowedCustomNetworks");
        try {
            assertThatThrownBy(() -> buildJobContainerService.setEnvironment(environment)).isInstanceOf(LocalCIException.class).hasMessageContaining("names no network");

            // a list that is not set at all does not check the network, and a list with a network is trimmed
            buildJobContainerService.setEnvironment(new MockEnvironment());
            assertThat(ReflectionTestUtils.getField(buildJobContainerService, "allowedCustomNetworks")).isEqualTo(List.of());
            buildJobContainerService
                    .setEnvironment(new MockEnvironment().withProperty("artemis.continuous-integration.container-flags-limit.allowed-custom-networks[0]", " mirror-only "));
            assertThat(ReflectionTestUtils.getField(buildJobContainerService, "allowedCustomNetworks")).isEqualTo(List.of("mirror-only"));
        }
        finally {
            ReflectionTestUtils.setField(buildJobContainerService, "allowedCustomNetworks", previous);
        }
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
    void testAdministratorCanAllowPrivilegeGainAndKeepsTheOtherRestrictions() {
        when(buildAgentConfiguration.isPrivilegeEscalationAllowed()).thenReturn(true);

        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "my-network-name", 1, 1, 1));

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig.getSecurityOpts() == null || hostConfig.getSecurityOpts().isEmpty()).isTrue();
        assertThat(hostConfig.getCapDrop()).containsExactlyInAnyOrder(Capability.MKNOD, Capability.SYS_CHROOT, Capability.SETFCAP);
    }

    @Test
    void testBuildContainerDropsTheUnusualCapabilitiesAndCannotGainPrivilegesWhenRestricted() {
        when(buildAgentConfiguration.isPrivilegeEscalationAllowed()).thenReturn(false);
        buildJobContainerService.configureContainer(CONTAINER_NAME, IMAGE_NAME, BUILD_SCRIPT, new DockerRunConfig(List.of(), "my-network-name", 1, 1, 1));

        HostConfig hostConfig = captureHostConfig();
        assertThat(hostConfig.getCapDrop()).containsExactlyInAnyOrder(Capability.MKNOD, Capability.SYS_CHROOT, Capability.SETFCAP, Capability.AUDIT_WRITE);
        assertThat(hostConfig.getSecurityOpts()).isEqualTo(List.of("no-new-privileges"));
    }

    @Test
    void testRunScriptInContainerExecutesSynchronously() {
        int exitCode = buildJobContainerService.runScriptInContainer(DUMMY_CONTAINER_ID, "build-job-1");

        // Verify the exit code is returned from the mocked inspect response
        assertThat(exitCode).isZero();

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
        int exitCode = buildJobContainerService.runScriptInContainer(DUMMY_CONTAINER_ID, "build-job-1");

        assertThat(exitCode).isZero();

        // Verify that withDetach(true) is never called for synchronous commands.
        // This guards against regression: previously, setup commands accidentally used detached mode.
        verify(execStartCmd, atLeastOnce()).withDetach(false);
        verify(execStartCmd, never()).withDetach(true);
    }

    @Test
    void testRepositoryArchiveIsStreamedFromDiskInsteadOfBeingHeldInMemory(@TempDir Path repository) throws Exception {
        FileUtils.writeStringToFile(repository.resolve("file.txt").toFile(), "content", StandardCharsets.UTF_8);
        CopyArchiveToContainerCmd copyCommand = mock(CopyArchiveToContainerCmd.class);
        when(buildAgentConfiguration.getDockerClient().copyArchiveToContainerCmd(anyString())).thenReturn(copyCommand);
        when(copyCommand.withRemotePath(anyString())).thenReturn(copyCommand);
        AtomicReference<InputStream> uploadedStream = new AtomicReference<>();
        when(copyCommand.withTarInputStream(any())).thenAnswer(invocation -> {
            uploadedStream.set(invocation.getArgument(0));
            return copyCommand;
        });
        List<String> uploadedEntries = new ArrayList<>();
        doAnswer(invocation -> {
            try (TarArchiveInputStream tar = new TarArchiveInputStream(uploadedStream.get())) {
                TarArchiveEntry entry;
                while ((entry = tar.getNextEntry()) != null) {
                    uploadedEntries.add(entry.getName());
                }
            }
            return null;
        }).when(copyCommand).exec();

        ReflectionTestUtils.invokeMethod(buildJobContainerService, "copyToContainer", repository, DUMMY_CONTAINER_ID, "build-job-1");

        assertThat(uploadedStream.get()).isNotInstanceOf(ByteArrayInputStream.class);
        assertThat(uploadedEntries.contains(repository.getFileName() + "/file.txt")).as("the archive holds the file of the repository").isTrue();
    }

    @Test
    void testTarArchiveDoesNotFollowSymbolicLinks(@TempDir Path workDir) throws Exception {
        Path repository = Files.createDirectories(workDir.resolve("repository"));
        Path outsideDirectory = Files.createDirectories(workDir.resolve("outside"));
        FileUtils.writeStringToFile(outsideDirectory.resolve("outside.txt").toFile(), "outside content", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(repository.resolve("file.txt").toFile(), "content", StandardCharsets.UTF_8);
        Files.createSymbolicLink(repository.resolve("inside-link"), Path.of("file.txt"));
        Files.createDirectories(repository.resolve("sub"));
        Files.createSymbolicLink(repository.resolve("sub/absolute-inside-link"), repository.resolve("file.txt").toAbsolutePath());
        Files.createSymbolicLink(repository.resolve("sub/self"), repository.resolve("sub").toAbsolutePath());
        Files.createSymbolicLink(repository.resolve("outside-file-link"), outsideDirectory.resolve("outside.txt"));
        Files.createSymbolicLink(repository.resolve("outside-directory-link"), outsideDirectory);

        Path archive = ReflectionTestUtils.invokeMethod(buildJobContainerService, "createTarArchive", repository);

        List<String> names = new ArrayList<>();
        String absoluteLinkTarget = null;
        String selfLinkTarget = null;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(Files.newInputStream(archive))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                names.add(entry.getName());
                if (entry.getName().endsWith("absolute-inside-link")) {
                    absoluteLinkTarget = entry.getLinkName();
                }
                if (entry.getName().endsWith("sub/self")) {
                    selfLinkTarget = entry.getLinkName();
                }
                Assertions.assertThat(new String(tar.readAllBytes(), StandardCharsets.UTF_8)).doesNotContain("outside content");
            }
        }
        finally {
            Files.deleteIfExists(archive);
        }
        Assertions.assertThat(names).contains("repository/file.txt", "repository/inside-link");
        Assertions.assertThat(absoluteLinkTarget).as("an absolute target inside the repository is archived relative to the link").isEqualTo("../file.txt");
        Assertions.assertThat(selfLinkTarget).as("a link to its own directory has a target that is not empty").isEqualTo(".");
        assertThat(names.stream().noneMatch(name -> name.contains("outside"))).as("links leaving the repository are not archived").isTrue();
    }
}
