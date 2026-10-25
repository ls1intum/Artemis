package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.info.GitProperties;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildRunnerStatus;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildRunnerType;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.core.service.distributed.local.LocalDataProviderService;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;

class BuildAgentInformationLifecycleTest {

    private final BuildAgentConfiguration configuration = mock(BuildAgentConfiguration.class);

    private final BuildAgentSshKeyService ssh = mock(BuildAgentSshKeyService.class);

    private final DistributedDataAccessService distributed = mock(DistributedDataAccessService.class);

    private final GitProperties git = mock(GitProperties.class);

    private final BuildJobRunner runner = mock(BuildJobRunner.class);

    private final DistributedMap<String, BuildAgentInformation> agents = new LocalDataProviderService().getMap("agents");

    private final BuildAgentInformationService service = new BuildAgentInformationService(configuration, ssh, distributed, git, runner);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "buildAgentShortName", "agent");
        ReflectionTestUtils.setField(service, "buildAgentDisplayName", "Agent");
        when(distributed.isConnectedToCluster()).thenReturn(true);
        when(distributed.getDistributedBuildAgentInformation()).thenReturn(agents);
        when(distributed.getLocalMemberAddress()).thenReturn("address");
        when(distributed.getProcessingJobs()).thenReturn(List.of());
        when(configuration.getThreadPoolSize()).thenReturn(3);
        when(git.getShortCommitId()).thenReturn("revision");
        when(ssh.getPublicKeyAsString()).thenReturn("public-key");
        when(runner.type()).thenReturn(BuildRunnerType.KUBERNETES);
    }

    @ParameterizedTest
    @EnumSource(value = BuildAgentStatus.class, names = { "ACTIVE", "IDLE", "PAUSED", "SELF_PAUSED" })
    void runnerRefreshPreservesPauseAndFailureCountersAndPublishesKubernetesVersion(BuildAgentStatus previousStatus) {
        var started = ZonedDateTime.parse("2026-01-01T10:00:00Z");
        var details = new BuildAgentDetailsDTO(10, 3, 2, 1, 1, 7, started.plusHours(1), started, "old", 4, null, "Kubernetes", "old");
        agents.put("agent", new BuildAgentInformation(new BuildAgentDTO("agent", "address", "Agent"), 3, 0, List.of(), previousStatus, "public-key", details, 5));
        when(runner.status()).thenReturn(BuildRunnerStatus.available("v1"));
        service.updateBuildRunnerStatus();
        var updated = agents.get("agent");
        assertThat(updated.status())
                .isEqualTo(previousStatus == BuildAgentStatus.PAUSED || previousStatus == BuildAgentStatus.SELF_PAUSED ? previousStatus : BuildAgentStatus.IDLE);
        assertThat(updated.maxNumberOfConcurrentBuildJobs()).isEqualTo(3);
        assertThat(updated.buildAgentDetails().consecutiveBuildFailures()).isEqualTo(4);
        assertThat(updated.buildAgentDetails().averageBuildDuration()).isEqualTo(10);
        assertThat(updated.buildAgentDetails().totalBuilds()).isEqualTo(7);
        assertThat(updated.buildAgentDetails().startDate()).isEqualTo(started);
        assertThat(updated.buildAgentDetails().lastBuildDate()).isEqualTo(started.plusHours(1));
        assertThat(updated.buildAgentDetails().buildRunner()).isEqualTo("Kubernetes");
        assertThat(updated.buildAgentDetails().buildRunnerVersion()).isEqualTo("v1");
        assertThat(service.getDockerVersion()).isNull();
        assertThat(service.getBuildRunnerVersion()).isEqualTo("v1");
        verify(configuration, never()).setDockerAvailable(anyBoolean());
        service.updateBuildRunnerStatus();
        assertThat(agents.get("agent")).isSameAs(updated);
    }

    @Test
    void unavailableRunnerRetainsLastKnownVersionAndRepeatedFailureDoesNotRepublish() {
        when(runner.type()).thenReturn(BuildRunnerType.DOCKER);
        when(runner.status()).thenReturn(BuildRunnerStatus.available("27"));
        service.updateBuildRunnerStatus();
        assertThat(service.getDockerVersion()).isEqualTo("27");
        when(runner.status()).thenReturn(BuildRunnerStatus.unavailable("connection refused"));
        service.updateBuildRunnerStatus();
        var unavailable = agents.get("agent");
        assertThat(unavailable.buildAgentDetails().dockerVersion()).isEqualTo("27");
        assertThat(unavailable.buildAgentDetails().buildRunnerVersion()).isEqualTo("27");
        service.updateBuildRunnerStatus();
        assertThat(agents.get("agent")).isSameAs(unavailable);
        verify(configuration).setDockerAvailable(true);
        verify(configuration, org.mockito.Mockito.times(2)).setDockerAvailable(false);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { " " })
    void unconfiguredAgentCannotPublishEvenWhenRunnerIsAvailable(String name) {
        ReflectionTestUtils.setField(service, "buildAgentShortName", name);
        when(runner.status()).thenReturn(BuildRunnerStatus.available("v1"));
        service.updateBuildRunnerStatus();
        assertThat(agents.isEmpty()).isTrue();
        assertThat(service.getBuildRunnerVersion()).isEqualTo("v1");
    }

    @Test
    void disconnectedAgentDoesNotPublishAndShutdownOnlyRemovesWhileStoreRuns() {
        when(distributed.isConnectedToCluster()).thenReturn(false);
        when(runner.status()).thenReturn(BuildRunnerStatus.available("v1"));
        service.updateBuildRunnerStatus();
        assertThat(agents.isEmpty()).isTrue();
        agents.put("agent", new BuildAgentInformation(new BuildAgentDTO("agent", "address", "Agent"), 1, 0, List.of(), BuildAgentStatus.IDLE, null, null, 0));
        service.removeLocalBuildAgentInformationOnShutdown();
        assertThat(agents.containsKey("agent")).isTrue();
        when(distributed.isInstanceRunning()).thenReturn(true);
        service.removeLocalBuildAgentInformationOnShutdown();
        assertThat(agents.isEmpty()).isTrue();
        when(distributed.getDistributedBuildAgentInformation()).thenThrow(new IllegalStateException("store closed"));
        assertThatCode(service::removeLocalBuildAgentInformationOnShutdown).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void legacyAgentWithoutDetailsStartsFreshCountersAndKeepsSelfPause(boolean selfPaused) {
        agents.put("agent", new BuildAgentInformation(new BuildAgentDTO("agent", "address", "Agent"), 1, 0, List.of(),
                selfPaused ? BuildAgentStatus.SELF_PAUSED : BuildAgentStatus.IDLE, null, null, 0));
        var unidentifiedJob = new BuildJobQueueItem("unknown", "Unknown", null, 1, 2, 3, 0, 1, null, null, null, null, null);
        var foreignJob = new BuildJobQueueItem("foreign", "Foreign", new BuildAgentDTO("other", "address", "Other"), 1, 2, 3, 0, 1, null, null, null, null, null);
        when(distributed.getProcessingJobs()).thenReturn(List.of(unidentifiedJob, foreignJob));
        var before = ZonedDateTime.now();
        service.updateLocalBuildAgentInformation(true);
        var updated = agents.get("agent");
        assertThat(updated.status()).isEqualTo(selfPaused ? BuildAgentStatus.SELF_PAUSED : BuildAgentStatus.PAUSED);
        assertThat(updated.numberOfCurrentBuildJobs()).isZero();
        assertThat(updated.runningBuildJobs()).isEmpty();
        var details = updated.buildAgentDetails();
        assertThat(details.averageBuildDuration()).isZero();
        assertThat(details.successfulBuilds()).isZero();
        assertThat(details.failedBuilds()).isZero();
        assertThat(details.cancelledBuilds()).isZero();
        assertThat(details.timedOutBuild()).isZero();
        assertThat(details.totalBuilds()).isZero();
        assertThat(details.lastBuildDate()).isNull();
        assertThat(details.startDate()).isBetween(before, ZonedDateTime.now());
        assertThat(details.gitRevision()).isEqualTo("revision");
    }
}
