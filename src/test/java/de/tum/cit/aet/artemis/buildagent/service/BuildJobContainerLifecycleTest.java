package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CopyArchiveFromContainerCmd;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmd;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.command.InspectExecCmd;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StopContainerCmd;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.StreamType;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildLogDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerRunConfig;
import de.tum.cit.aet.artemis.core.service.TempFileUtilService;
import de.tum.cit.aet.artemis.localci.exception.ContainerNetworkNotAllowedException;
import de.tum.cit.aet.artemis.localci.exception.LocalCIException;

class BuildJobContainerLifecycleTest {

    private static final String ALLOWED_NETWORKS_PROPERTY = "artemis.continuous-integration.container-flags-limit.allowed-custom-networks";

    private final BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);

    private final DockerClient docker = mock(DockerClient.class);

    private final BuildLogsMap logs = mock(BuildLogsMap.class);

    private final BuildJobContainerService service = new BuildJobContainerService(configuration, logs, new TempFileUtilService());

    private final ListContainersCmd list = mock(ListContainersCmd.class, RETURNS_SELF);

    private final CreateContainerCmd create = mock(CreateContainerCmd.class, RETURNS_SELF);

    @BeforeEach
    void setUp() {
        when(configuration.getDockerClient()).thenReturn(docker);
        when(configuration.isDockerAvailable()).thenReturn(true);
        when(docker.listContainersCmd()).thenReturn(list);
        when(list.exec()).thenReturn(List.of());
        when(docker.createContainerCmd("image")).thenReturn(create);
        when(configuration.hostConfig())
                .thenReturn(HostConfig.newHostConfig().withCpuPeriod(100000L).withCpuQuota(200000L).withMemory(1024L).withMemorySwap(2048L).withPidsLimit(100L));
        when(configuration.applyStorageLimit(any(HostConfig.class))).thenCallRealMethod();
    }

    @Test
    void requestIncludesProxyScriptAndExerciseEnvironmentWithUncappedResources() {
        ReflectionTestUtils.setField(service, "useSystemProxy", true);
        ReflectionTestUtils.setField(service, "httpProxy", "http://proxy");
        ReflectionTestUtils.setField(service, "httpsProxy", "https://proxy");
        ReflectionTestUtils.setField(service, "noProxy", "localhost");
        CreateContainerResponse response = mock(CreateContainerResponse.class);
        when(create.exec()).thenReturn(response);
        assertThat(service.configureContainer("build", "image", "echo test", new DockerRunConfig(List.of("CUSTOM=value"), "network", 3, 10, 20))).isSameAs(response);
        var host = ArgumentCaptor.forClass(HostConfig.class);
        verify(create).withHostConfig(host.capture());
        assertThat(host.getValue().getCpuQuota()).isEqualTo(300000L);
        assertThat(host.getValue().getMemory()).isEqualTo(10 * 1024L * 1024L);
        assertThat(host.getValue().getMemorySwap()).isEqualTo(20 * 1024L * 1024L);
        assertThat(host.getValue().getNetworkMode()).isEqualTo("network");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> environment = ArgumentCaptor.forClass(List.class);
        verify(create).withEnv(environment.capture());
        assertThat(environment.getValue()).containsExactly("HTTP_PROXY=http://proxy", "HTTPS_PROXY=https://proxy", "NO_PROXY=localhost", "SCRIPT=echo test", "CUSTOM=value");
    }

    @Test
    void defaultResourceRequestKeepsHostConfigurationAndHandlesMissingExerciseVariables() {
        HostConfig defaultHost = configuration.hostConfig();
        service.configureContainer("build", "image", "script", new DockerRunConfig(null, null, 0, 0, 0));
        verify(create).withHostConfig(defaultHost);
        verify(create).withEnv(List.of("SCRIPT=script"));
        when(configuration.isDockerAvailable()).thenReturn(false);
        assertThatThrownBy(() -> service.configureContainer("build", "image", "script", new DockerRunConfig(null, null, 0, 0, 0))).isInstanceOf(LocalCIException.class)
                .hasMessageContaining("Docker is not available");
        assertThatThrownBy(() -> service.startContainer("build")).isInstanceOf(LocalCIException.class).hasMessageContaining("Cannot start container build");
        assertThat(service.getIDOfRunningContainer("build")).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    void restrictiveNetworkListRejectsImplicitDockerBridge(String network) {
        service.setEnvironment(new MockEnvironment().withProperty(ALLOWED_NETWORKS_PROPERTY, "mirror-only"));

        assertThatThrownBy(() -> service.configureContainer("build", "image", "script", new DockerRunConfig(null, network, 0, 0, 0)))
                .isInstanceOf(ContainerNetworkNotAllowedException.class).hasMessageContaining("bridge");
        verify(docker, never()).createContainerCmd(any());
    }

    @ParameterizedTest
    @ValueSource(strings = { "host", "HOST", " host ", "container:other", "Container:other" })
    void sharedNetworkNamespaceIsRejectedWithoutPausingTheAgent(String network) {
        assertThatThrownBy(() -> service.configureContainer("build", "image", "script", new DockerRunConfig(null, network, 0, 0, 0)))
                .isInstanceOf(ContainerNetworkNotAllowedException.class);
        verify(docker, never()).createContainerCmd(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " ", "\t" })
    void listedDockerBridgeAllowsImplicitNetwork(String network) {
        service.setEnvironment(new MockEnvironment().withProperty(ALLOWED_NETWORKS_PROPERTY, "bridge"));

        service.configureContainer("build", "image", "script", new DockerRunConfig(null, network, 0, 0, 0));

        verify(create).exec();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void unsetOrEmptyNetworkListAllowsCustomNetwork(String configuredNetworks) {
        MockEnvironment environment = new MockEnvironment();
        if (configuredNetworks != null) {
            environment.withProperty(ALLOWED_NETWORKS_PROPERTY, configuredNetworks);
        }
        service.setEnvironment(environment);

        service.configureContainer("build", "image", "script", new DockerRunConfig(null, "custom-network", 0, 0, 0));

        var host = ArgumentCaptor.forClass(HostConfig.class);
        verify(create).withHostConfig(host.capture());
        assertThat(host.getValue().getNetworkMode()).isEqualTo("custom-network");
    }

    @ParameterizedTest
    @ValueSource(strings = { "", " ", "\t" })
    void networkListWithOnlyBlankEntriesFailsAtStartup(String entry) {
        MockEnvironment environment = new MockEnvironment().withProperty(ALLOWED_NETWORKS_PROPERTY + "[0]", entry);

        assertThatThrownBy(() -> service.setEnvironment(environment)).isInstanceOf(LocalCIException.class).hasMessageContaining(ALLOWED_NETWORKS_PROPERTY);
    }

    @Test
    void restrictiveNetworkListAlwaysAllowsNone() {
        service.setEnvironment(new MockEnvironment().withProperty(ALLOWED_NETWORKS_PROPERTY, "mirror-only"));

        service.configureContainer("build", "image", "script", new DockerRunConfig(null, "none", 0, 0, 0));

        var host = ArgumentCaptor.forClass(HostConfig.class);
        verify(create).withHostConfig(host.capture());
        assertThat(host.getValue().getNetworkMode()).isEqualTo("none");
    }

    @ParameterizedTest
    @CsvSource({ "' mirror-only ', mirror-only", "' none ', none" })
    void dockerReceivesTheNormalizedNetworkThatWasValidated(String requestedNetwork, String expectedNetwork) {
        service.setEnvironment(new MockEnvironment().withProperty(ALLOWED_NETWORKS_PROPERTY, "mirror-only"));

        service.configureContainer("build", "image", "script", new DockerRunConfig(null, requestedNetwork, 0, 0, 0));

        var host = ArgumentCaptor.forClass(HostConfig.class);
        verify(create).withHostConfig(host.capture());
        assertThat(host.getValue().getNetworkMode()).isEqualTo(expectedNetwork);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void archiveKeepsContainedSymbolicLinksRelativeToTheirParent(boolean absoluteTarget, @TempDir Path workDir) throws Exception {
        Path repository = Files.createDirectory(workDir.resolve("repository"));
        BuildJobContainerService archiveService = new BuildJobContainerService(configuration, logs, new TempFileUtilService(workDir.resolve("archives")));
        Path target = repository.resolve("target.txt");
        FileUtils.writeStringToFile(target.toFile(), "content", StandardCharsets.UTF_8);
        Path directory = Files.createDirectory(repository.resolve("nested"));
        Files.createSymbolicLink(directory.resolve("link"), absoluteTarget ? target : Path.of("../target.txt"));

        Path archive = ReflectionTestUtils.invokeMethod(archiveService, "createTarArchive", repository);
        boolean found = false;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(Files.newInputStream(archive))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.getName().endsWith("/nested/link")) {
                    found = true;
                    assertThat(entry.isSymbolicLink()).isTrue();
                    assertThat(entry.getLinkName()).isEqualTo("../target.txt");
                }
            }
        }
        finally {
            Files.deleteIfExists(archive);
        }
        assertThat(found).as("the archive contains the symbolic link").isTrue();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void archiveKeepsSymbolicLinksToTheirOwnDirectory(boolean absoluteTarget, @TempDir Path workDir) throws Exception {
        Path repository = Files.createDirectory(workDir.resolve("repository"));
        Path directory = Files.createDirectory(repository.resolve("nested"));
        Files.createSymbolicLink(directory.resolve("self"), absoluteTarget ? directory : Path.of("."));
        BuildJobContainerService archiveService = new BuildJobContainerService(configuration, logs, new TempFileUtilService(workDir.resolve("archives")));

        Path archive = ReflectionTestUtils.invokeMethod(archiveService, "createTarArchive", repository);
        boolean found = false;
        try (TarArchiveInputStream tar = new TarArchiveInputStream(Files.newInputStream(archive))) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (entry.getName().equals("repository/nested/self")) {
                    found = true;
                    assertThat(entry.isSymbolicLink()).isTrue();
                    assertThat(entry.getLinkName()).isEqualTo(".");
                }
            }
        }
        finally {
            Files.deleteIfExists(archive);
        }
        assertThat(found).as("the archive contains the symbolic link to its own directory").isTrue();
    }

    @Test
    void lookupFiltersUnnamedContainersAndNonMatchingNames() {
        Container unnamed = container("unnamed", null, null);
        Container empty = container("empty", new String[0], null);
        Container other = container("other", new String[] { "/other" }, "running");
        Container matching = container("matching", new String[] { "/build" }, "exited");
        when(list.exec()).thenReturn(List.of(unnamed, empty, other, matching));
        assertThat(service.getIDOfRunningContainer("build")).isEqualTo("matching");
        service.stopContainer("build");
        verify(docker, never()).execCreateCmd(any());
        when(list.exec()).thenThrow(new IllegalStateException("unexpected daemon failure"));
        assertThat(service.getIDOfRunningContainer("build")).isNull();
        doThrow(new RuntimeException("Connection refused")).when(list).exec();
        assertThat(service.getIDOfRunningContainer("build")).isNull();
    }

    @Test
    void stoppingAContainerThatDisappearsAfterLookupDoesNotFailCleanup() {
        Container running = container("id", new String[] { "/build" }, "running");
        when(list.exec()).thenReturn(List.of(running));
        when(docker.execCreateCmd("id")).thenThrow(new NotFoundException("removed after lookup"));

        assertThatCode(() -> service.stopContainer("build")).doesNotThrowAnyException();

        verify(docker).execCreateCmd("id");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "../results", "results/$(command)", "results with spaces" })
    void rejectsUnsafePathsBeforeExecutingDockerCommands(String path) {
        assertThatThrownBy(() -> service.moveResultsToSpecifiedDirectory("build", List.of(), path)).isInstanceOf(LocalCIException.class).hasMessageContaining("Invalid path:");
        verify(docker, never()).execCreateCmd(any());
    }

    @Test
    void archiveRetrievalRetriesTransientNullStreamThenReturnsUsableArchive() throws Exception {
        CopyArchiveFromContainerCmd copy = mock(CopyArchiveFromContainerCmd.class);
        when(docker.copyArchiveFromContainerCmd("build", "/results")).thenReturn(copy);
        when(copy.exec()).thenReturn((InputStream) null).thenReturn(new ByteArrayInputStream(new byte[1024]));
        try (var archive = service.getArchiveFromContainer("build", "/results", "job")) {
            assertThat(archive.getNextEntry()).isNull();
        }
        verify(copy, times(2)).exec();
        verify(logs).appendBuildLogEntry("job", "Retrieve archive from container build succeeded after 2 attempt(s).");
    }

    @Test
    void missingArchiveFailsImmediatelyAndPersistentConnectivityFailureExhaustsRetries() {
        CopyArchiveFromContainerCmd copy = mock(CopyArchiveFromContainerCmd.class);
        when(docker.copyArchiveFromContainerCmd("build", "/results")).thenReturn(copy);
        when(copy.exec()).thenThrow(new NotFoundException("missing"));
        assertThatThrownBy(() -> service.getArchiveFromContainer("build", "/results", null)).isInstanceOf(NotFoundException.class);
        verify(copy).exec();
        doThrow(new IllegalStateException("Connection refused")).when(copy).exec();
        assertThatThrownBy(() -> service.getArchiveFromContainer("build", "/results", "job")).isInstanceOf(LocalCIException.class).hasMessageContaining("after 3 attempts");
        verify(copy, times(4)).exec();
        verify(logs).appendBuildLogEntry("job",
                "Failed to retrieve build results after multiple attempts. This is an infrastructure issue, not a problem with your code. Please try rerunning your build.");
    }

    /**
     * A build that times out is cancelled by interrupting its thread while its container is stopped. The archive can then no longer be read, which is no reason to
     * retry, and not an infrastructure problem that the student is told to rerun the build for.
     */
    @Test
    void archiveRetrievalOfCancelledBuildIsNotRetriedAndNotReportedAsInfrastructureIssue() {
        CopyArchiveFromContainerCmd copy = mock(CopyArchiveFromContainerCmd.class);
        when(docker.copyArchiveFromContainerCmd("build", "/results")).thenReturn(copy);
        doThrow(new IllegalStateException("Connection reset")).when(copy).exec();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> service.getArchiveFromContainer("build", "/results", "job")).isInstanceOf(CancellationException.class);
        }
        finally {
            Thread.interrupted();
        }
        verify(copy, never()).exec();
        verify(logs, never()).appendBuildLogEntry(eq("job"), any(String.class));
    }

    /**
     * The cancellation can also arrive while an attempt is running or while the pause before the next one is waited out.
     */
    @Test
    void archiveRetrievalCancelledWhileAnAttemptFailsStopsAfterThatAttempt() {
        CopyArchiveFromContainerCmd copy = mock(CopyArchiveFromContainerCmd.class);
        when(docker.copyArchiveFromContainerCmd("build", "/results")).thenReturn(copy);
        doAnswer(invocation -> {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Connection reset");
        }).when(copy).exec();
        try {
            assertThatThrownBy(() -> service.getArchiveFromContainer("build", "/results", "job")).isInstanceOf(CancellationException.class);
        }
        finally {
            Thread.interrupted();
        }
        verify(copy, times(1)).exec();
        verify(logs, never()).appendBuildLogEntry(eq("job"), any(String.class));
    }

    @Test
    void scriptLogsPreserveLinesAndMissingExitCodeReturnsMinusOne() {
        ExecStartCmd start = execSetup();
        when(start.exec(any())).thenAnswer(call -> {
            ResultCallback<Frame> callback = call.getArgument(0);
            callback.onNext(new Frame(StreamType.STDOUT, "first\nsecond\n".getBytes(StandardCharsets.UTF_8)));
            callback.onNext(new Frame(StreamType.STDOUT, new byte[0]));
            callback.onComplete();
            return null;
        });
        assertThat(service.runScriptInContainer("build", "job")).isEqualTo(-1);
        var lines = ArgumentCaptor.forClass(BuildLogDTO.class);
        verify(logs, times(2)).appendBuildLogEntry(eq("job"), lines.capture());
        assertThat(lines.getAllValues()).extracting(BuildLogDTO::log).containsExactly("first\n", "second\n");
    }

    @Test
    void scriptCallbackErrorIsReportedWithoutInspectingExitCode() {
        ExecStartCmd start = execSetup();
        when(start.exec(any())).thenAnswer(call -> {
            ResultCallback<?> callback = call.getArgument(0);
            callback.onError(new IllegalStateException("stream failed"));
            return null;
        });
        assertThatThrownBy(() -> service.runScriptInContainer("build", "job")).isInstanceOf(LocalCIException.class).hasMessageContaining("Docker command failed");
    }

    @ParameterizedTest
    @ValueSource(strings = { "missing", "stopped", "other" })
    void unresponsiveContainerRemovesStoppedOnesAndKillsOtherFailures(String failure) {
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        KillContainerCmd kill = mock(KillContainerCmd.class);
        when(docker.stopContainerCmd("id")).thenReturn(stop);
        when(docker.removeContainerCmd("id")).thenReturn(remove);
        when(docker.killContainerCmd("id")).thenReturn(kill);
        RuntimeException exception = switch (failure) {
            case "missing" -> new NotFoundException("already removed");
            case "stopped" -> new NotModifiedException("already stopped");
            default -> new IllegalStateException("daemon failed");
        };
        when(stop.exec()).thenThrow(exception);
        assertThatCode(() -> service.stopUnresponsiveContainer("id")).doesNotThrowAnyException();
        if (failure.equals("other")) {
            verify(kill).exec();
            verify(remove, never()).exec();
        }
        else {
            verify(remove).exec();
            verify(kill, never()).exec();
        }
    }

    @Test
    void failedRemovalClosesCommandsAndPreservesCancellation() {
        StopContainerCmd stop = mock(StopContainerCmd.class, RETURNS_SELF);
        RemoveContainerCmd remove = mock(RemoveContainerCmd.class);
        when(docker.stopContainerCmd("id")).thenReturn(stop);
        when(stop.exec()).thenThrow(new NotModifiedException("already stopped"));
        when(docker.removeContainerCmd("id")).thenReturn(remove);
        when(remove.exec()).thenThrow(new IllegalStateException("daemon disconnected"));

        Thread.currentThread().interrupt();
        try {
            assertThatCode(() -> service.stopUnresponsiveContainer("id")).doesNotThrowAnyException();
            assertThat(Thread.currentThread().isInterrupted()).as("cancellation remains observable after failed cleanup").isTrue();
            verify(remove).exec();
            verify(remove).close();
            verify(stop).close();
        }
        finally {
            Thread.interrupted();
        }
    }

    private ExecStartCmd execSetup() {
        ExecCreateCmd createExec = mock(ExecCreateCmd.class, RETURNS_SELF);
        ExecCreateCmdResponse created = mock(ExecCreateCmdResponse.class);
        when(created.getId()).thenReturn("exec-id");
        when(docker.execCreateCmd("build")).thenReturn(createExec);
        when(createExec.exec()).thenReturn(created);
        ExecStartCmd start = mock(ExecStartCmd.class, RETURNS_SELF);
        when(docker.execStartCmd("exec-id")).thenReturn(start);
        InspectExecCmd inspect = mock(InspectExecCmd.class);
        InspectExecResponse inspected = mock(InspectExecResponse.class);
        when(docker.inspectExecCmd("exec-id")).thenReturn(inspect);
        when(inspect.exec()).thenReturn(inspected);
        when(inspected.getExitCodeLong()).thenReturn(null);
        return start;
    }

    private Container container(String id, String[] names, String state) {
        Container container = mock(Container.class);
        when(container.getId()).thenReturn(id);
        when(container.getNames()).thenReturn(names);
        when(container.getState()).thenReturn(state);
        return container;
    }
}
