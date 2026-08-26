package de.tum.cit.aet.artemis.buildagent.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import de.tum.cit.aet.artemis.buildagent.BuildAgentConfiguration;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.JobTimingInfo;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;
import de.tum.cit.aet.artemis.buildagent.service.runner.BuildJobRunner;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.localci.service.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * Covers the cross-check that removes a build job from the distributed processing map when this agent is not running
 * it locally.
 * <p>
 * The two states it has to tell apart look identical from the map: an agent that vanished mid-claim, and an agent that
 * has just claimed a job and not yet registered its future. Claiming publishes the job to the distributed map before
 * {@code executeBuildJob} registers it locally, and the reverse gap exists once the future completes, so a job is
 * legitimately absent from the local set for a moment at each end of its life.
 * <p>
 * Removing it during either window used to only skew the running-job counts. It no longer does: a build agent's clone
 * is authorized against this map, so a job removed from it fails its next clone with a 401 and the build ends at 0%.
 * That failure surfaces as an unrelated flaky test rather than as anything naming this cleanup, which is why the
 * behaviour is pinned here.
 */
class SharedQueueStaleJobCleanupTest {

    private static final String AGENT_NAME = "artemis-build-agent-1";

    private DistributedDataAccessService distributedDataAccessService;

    private BuildJobManagementService buildJobManagementService;

    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    private SharedQueueProcessingService sharedQueueProcessingService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        distributedDataAccessService = mock(DistributedDataAccessService.class);
        buildJobManagementService = mock(BuildJobManagementService.class);
        processingJobs = mock(DistributedMap.class);

        when(distributedDataAccessService.isConnectedToCluster()).thenReturn(true);
        when(distributedDataAccessService.getDistributedProcessingJobs()).thenReturn(processingJobs);

        sharedQueueProcessingService = new SharedQueueProcessingService(mock(BuildAgentConfiguration.class), buildJobManagementService, mock(BuildLogsMap.class),
                mock(TaskScheduler.class), mock(BuildJobRunner.class), mock(BuildAgentInformationService.class), distributedDataAccessService);
        ReflectionTestUtils.setField(sharedQueueProcessingService, "buildAgentShortName", AGENT_NAME);
    }

    private static BuildJobQueueItem claimedJob(String id, ZonedDateTime claimedAt) {
        var repositoryInfo = new RepositoryInfo("slug", RepositoryType.USER, RepositoryType.USER, "assignment", "tests", null, new String[0], new String[0]);
        var jobTimingInfo = new JobTimingInfo(claimedAt.minusSeconds(1), claimedAt, null, null, 60);
        var buildConfig = new BuildConfig("script", "image", "commit", "assignmentCommit", "testCommit", "main", null, null, false, false, List.of(), 0, null, null, null, null);
        return new BuildJobQueueItem(id, "name", new BuildAgentDTO(AGENT_NAME, "address", "display"), 1L, 2L, 3L, 0, 1, null, repositoryInfo, jobTimingInfo, buildConfig, null,
                "bjct-token");
    }

    /**
     * The window this exists for: the job is in the distributed map and not yet in the local set, which is what every
     * job looks like for the moment between being claimed and its future being registered.
     */
    @Test
    void shouldKeepAJobThatWasJustClaimedButIsNotRegisteredLocallyYet() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of());
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(claimedJob("job-1", ZonedDateTime.now())));

        sharedQueueProcessingService.detectAndCleanupStaleBuildJobs();

        verify(processingJobs, never()).remove("job-1");
    }

    /**
     * The counterpart: an entry old enough that no claim or completion could still be in flight is genuinely orphaned,
     * and leaving it would keep the agent's job count wrong for as long as the entry exists.
     */
    @Test
    void shouldRemoveAJobThatHasNotBeenRunningLocallyForLongerThanTheGracePeriod() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of());
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(claimedJob("job-1", ZonedDateTime.now().minusMinutes(10))));

        sharedQueueProcessingService.detectAndCleanupStaleBuildJobs();

        verify(processingJobs).remove("job-1");
    }

    /**
     * A job the agent is actually running is never the cleanup's business, whatever its age.
     */
    @Test
    void shouldKeepAJobThatIsRunningLocally() {
        when(buildJobManagementService.getRunningBuildJobIds()).thenReturn(Set.of("job-1"));
        when(distributedDataAccessService.getProcessingJobsForAgentByName(AGENT_NAME)).thenReturn(List.of(claimedJob("job-1", ZonedDateTime.now().minusMinutes(10))));
        when(distributedDataAccessService.getDistributedProcessingJobs().get("job-1")).thenReturn(claimedJob("job-1", ZonedDateTime.now().minusMinutes(10)));

        sharedQueueProcessingService.detectAndCleanupStaleBuildJobs();

        verify(processingJobs, never()).remove("job-1");
    }
}
