package de.tum.cit.aet.artemis.buildagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.info.GitProperties;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;

/**
 * Unit tests for {@link BuildAgentInformationService} focused on the status-resolution contract introduced with the
 * {@code MAINTENANCE} value and on the {@code republishCurrentState} state-preservation contract.
 *
 * <h2>Why this test file matters</h2>
 * Two regression hazards live in this service:
 * <ol>
 * <li>The status precedence rule {@code MAINTENANCE > SELF_PAUSED > PAUSED} (when {@code isPaused=true}) is the whole
 * reason {@code BuildAgentStatus.MAINTENANCE} exists. A refactor that flips precedence — e.g. checking
 * {@code SELF_PAUSED} before {@code MAINTENANCE} — would silently leave operators unable to distinguish "cleanup
 * running" from "self-paused after build failures."</li>
 * <li>The periodic disk-stats publish ({@code republishCurrentState}) reads the previous DTO from the distributed
 * map to preserve {@code isPaused} / {@code isPausedDueToFailures} / {@code consecutiveBuildFailures}. A refactor
 * that hard-codes any of those would corrupt operator-visible status on every 5-minute tick.</li>
 * </ol>
 */
class BuildAgentInformationServiceTest {

    private static final String AGENT_NAME = "agent-under-test";

    private BuildAgentConfiguration buildAgentConfiguration;

    private DistributedDataAccessService distributedDataAccessService;

    private BuildAgentMaintenanceStateService maintenanceState;

    @SuppressWarnings("unchecked")
    private DistributedMap<String, BuildAgentInformation> agentMap = mock(DistributedMap.class);

    private BuildAgentInformationService service;

    @BeforeEach
    void setUp() {
        buildAgentConfiguration = mock(BuildAgentConfiguration.class);
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        maintenanceState = new BuildAgentMaintenanceStateService();

        // Sane defaults so the bean does not blow up reading config values.
        lenient().when(buildAgentConfiguration.getThreadPoolSize()).thenReturn(2);
        lenient().when(buildAgentConfiguration.getPauseAfterConsecutiveFailedJobs()).thenReturn(100);
        lenient().when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        lenient().when(distributedDataAccessService.getLocalMemberAddress()).thenReturn("127.0.0.1:5701");
        @SuppressWarnings("unchecked")
        DistributedMap<String, BuildAgentInformation> map = mock(DistributedMap.class);
        agentMap = map;
        lenient().when(distributedDataAccessService.getDistributedBuildAgentInformation()).thenReturn(agentMap);
        // The processing-jobs map is queried via getProcessingJobsOfNode; return an empty map.
        @SuppressWarnings("unchecked")
        DistributedMap<String, de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem> processingJobs = mock(DistributedMap.class);
        lenient().when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);
        lenient().when(processingJobs.values()).thenReturn(List.of());

        BuildAgentSshKeyService sshKey = mock(BuildAgentSshKeyService.class);
        lenient().when(sshKey.getPublicKeyAsString()).thenReturn("ssh-rsa AAAA");
        GitProperties gitProperties = mock(GitProperties.class);
        lenient().when(gitProperties.getShortCommitId()).thenReturn("abc1234");
        BuildAgentDockerService dockerService = mock(BuildAgentDockerService.class);
        lenient().when(dockerService.getUnusedDockerImageStats()).thenReturn(BuildAgentDockerService.UnusedImageStats.EMPTY);

        service = new BuildAgentInformationService(buildAgentConfiguration, sshKey, distributedDataAccessService, gitProperties, maintenanceState, dockerService);
        ReflectionTestUtils.setField(service, "buildAgentShortName", AGENT_NAME);
        ReflectionTestUtils.setField(service, "buildAgentDisplayName", "Agent Under Test");
    }

    // --- Status resolution: MAINTENANCE > SELF_PAUSED > PAUSED priority -------------------------------------

    @Test
    void maintenanceStatusTakesPriorityOverAdminPause() {
        // Admin paused the agent and a maintenance action is currently running. MAINTENANCE must win so the
        // operator sees "cleanup running" and not "admin paused" in the badge.
        maintenanceState.setInMaintenance(true);
        when(agentMap.get(AGENT_NAME)).thenReturn(null); // no previous state in the distributed map

        service.updateLocalBuildAgentInformation(true);

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BuildAgentStatus.MAINTENANCE);
    }

    @Test
    void maintenanceStatusTakesPriorityOverSelfPausedFromFailures() {
        // Self-pause has previously been recorded; a maintenance action then runs while the agent is also flagged
        // as paused-due-to-failures. MAINTENANCE must still win — the operator's current concern is "what action is
        // running now", not "this agent has been backing off".
        BuildAgentInformation prev = withStatus(BuildAgentStatus.SELF_PAUSED, 5);
        when(agentMap.get(AGENT_NAME)).thenReturn(prev);
        maintenanceState.setInMaintenance(true);

        service.updateLocalBuildAgentInformation(true, /* isPausedDueToFailures */ true, /* consecutiveFailures */ 7);

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BuildAgentStatus.MAINTENANCE);
    }

    @Test
    void selfPausedWinsOverPlainPaused_whenNotInMaintenance() {
        // Without maintenance, the existing precedence applies: SELF_PAUSED beats plain PAUSED when failures
        // triggered the pause OR when the previous state was already SELF_PAUSED.
        BuildAgentInformation prev = withStatus(BuildAgentStatus.SELF_PAUSED, 3);
        when(agentMap.get(AGENT_NAME)).thenReturn(prev);
        maintenanceState.setInMaintenance(false);

        service.updateLocalBuildAgentInformation(true, /* isPausedDueToFailures */ true, 3);

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BuildAgentStatus.SELF_PAUSED);
    }

    @Test
    void plainPausedFallback_whenIsPausedButNotMaintenanceAndNotSelfPaused() {
        when(agentMap.get(AGENT_NAME)).thenReturn(null);
        maintenanceState.setInMaintenance(false);

        service.updateLocalBuildAgentInformation(true, /* isPausedDueToFailures */ false, 0);

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BuildAgentStatus.PAUSED);
    }

    @Test
    void notPaused_resolvesToIdleWhenNoJobsAreRunning() {
        maintenanceState.setInMaintenance(false);

        service.updateLocalBuildAgentInformation(false);

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BuildAgentStatus.IDLE);
    }

    // --- refreshSlowDiskStats + republishCurrentState: state-preservation contract --------------------------

    @Test
    void refreshSlowDiskStats_republishesPreservingPreviousPausedStateFromMap() {
        // Setup: previous distributed-map entry shows the agent is PAUSED. The 5-minute disk-stats refresh must
        // not flip the published status DTO to "not paused" — otherwise the admin UI would briefly show ACTIVE
        // until the next 10s push corrects it.
        BuildAgentInformation prev = withStatus(BuildAgentStatus.PAUSED, 0);
        when(agentMap.get(AGENT_NAME)).thenReturn(prev);

        service.refreshSlowDiskStats();

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(BuildAgentStatus.PAUSED);
    }

    @Test
    void refreshSlowDiskStats_republishesPreservingSelfPausedAndConsecutiveFailuresFromMap() {
        // Previous state: SELF_PAUSED with 12 consecutive failures. The slow-stats republish must preserve both
        // pieces of data because they are not derivable from elsewhere on the agent side — the failure counter
        // lives in SharedQueueProcessingService but is captured into the DTO at publish time.
        BuildAgentInformation prev = withStatus(BuildAgentStatus.SELF_PAUSED, 12);
        when(agentMap.get(AGENT_NAME)).thenReturn(prev);

        service.refreshSlowDiskStats();

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        BuildAgentInformation republished = captor.getValue();
        assertThat(republished.status()).isEqualTo(BuildAgentStatus.SELF_PAUSED);
        assertThat(republished.buildAgentDetails()).isNotNull();
        assertThat(republished.buildAgentDetails().consecutiveBuildFailures()).isEqualTo(12);
    }

    @Test
    void refreshSlowDiskStats_isANoOpWhenNotConnectedToCluster() {
        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(false);
        when(agentMap.get(AGENT_NAME)).thenReturn(null);

        service.refreshSlowDiskStats();

        // Must not attempt to write into the distributed map while disconnected — the .put would otherwise throw a
        // HazelcastInstanceNotActiveException and the @Scheduled thread would log a stack trace every 5 minutes.
        verify(agentMap, never()).put(any(), any());
    }

    @Test
    void refreshSlowDiskStats_publishesNonZeroDiskTotalAfterFirstWalk() {
        // After the slow walk runs, the published DTO must carry a non-zero diskTotalBytes (probed via the file
        // store) so the admin UI's "Disk usage" tile is no longer in its initial "not yet reported" state.
        when(agentMap.get(AGENT_NAME)).thenReturn(null);

        service.refreshSlowDiskStats();

        ArgumentCaptor<BuildAgentInformation> captor = ArgumentCaptor.forClass(BuildAgentInformation.class);
        verify(agentMap).put(eq(AGENT_NAME), captor.capture());
        BuildAgentInformation published = captor.getValue();
        assertThat(published.buildAgentDetails()).isNotNull();
        // We expect *some* probe to have succeeded on the test host (it walks "/" by default when no cache root is
        // configured); the exact bytes vary, but it must be greater than zero.
        assertThat(published.buildAgentDetails().diskTotalBytes()).isPositive();
    }

    // --- helpers -----------------------------------------------------------------------------------------------

    private BuildAgentInformation withStatus(BuildAgentStatus status, int consecutiveFailures) {
        BuildAgentDTO dto = new BuildAgentDTO(AGENT_NAME, "127.0.0.1:5701", "Agent Under Test");
        BuildAgentDetailsDTO details = new BuildAgentDetailsDTO(0, 0, 0, 0, 0, 0, null, ZonedDateTime.now(), "abc1234", consecutiveFailures, "28.0", 0, 0, 0, 0, 0, 0);
        return new BuildAgentInformation(dto, 2, 0, List.of(), status, "ssh-rsa AAAA", details, 100);
    }
}
