package de.tum.cit.aet.artemis.core.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobsStatisticsDTO;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.FinishedBuildJobPageableSearchDTO;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.util.SliceUtil;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.repository.BuildJobRepository;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;
import de.tum.cit.aet.artemis.programming.service.localci.SharedQueueManagementService;

@Profile(PROFILE_LOCALCI)
@EnforceAdmin
@Lazy
@RestController
@RequestMapping("api/core/admin/")
public class AdminBuildJobQueueResource {

    private final SharedQueueManagementService localCIBuildJobQueueService;

    private final DistributedDataAccessService distributedDataAccessService;

    private final BuildJobRepository buildJobRepository;

    private static final Logger log = LoggerFactory.getLogger(AdminBuildJobQueueResource.class);

    public AdminBuildJobQueueResource(SharedQueueManagementService localCIBuildJobQueueService, BuildJobRepository buildJobRepository,
            DistributedDataAccessService distributedDataAccessService) {
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
        this.buildJobRepository = buildJobRepository;
        this.distributedDataAccessService = distributedDataAccessService;
    }

    /**
     * Returns the queued build jobs.
     *
     * @return the queued build jobs
     */
    @GetMapping("queued-jobs")
    public ResponseEntity<List<BuildJobQueueItem>> getQueuedBuildJobs() {
        log.debug("REST request to get the queued build jobs");
        List<BuildJobQueueItem> buildJobQueue = distributedDataAccessService.getQueuedJobs();
        return ResponseEntity.ok(buildJobQueue);
    }

    /**
     * Returns the queued result jobs.
     *
     * @return the queued result jobs
     */
    @GetMapping("queued-results")
    public ResponseEntity<List<ResultQueueItem>> getQueuedResultJobs() {
        log.debug("REST request to get the queued result jobs");
        List<ResultQueueItem> buildResultQueue = distributedDataAccessService.getBuildResultQueue();
        return ResponseEntity.ok(buildResultQueue);
    }

    /**
     * Returns the running build jobs, optionally filtered by agent name.
     *
     * @param agentName the name of the agent (optional)
     * @return the running build jobs
     */
    @GetMapping("running-jobs")
    public ResponseEntity<List<BuildJobQueueItem>> getRunningBuildJobs(@RequestParam(required = false) String agentName) {
        log.debug("REST request to get the running build jobs for agent {}", agentName);
        List<BuildJobQueueItem> runningBuildJobs = distributedDataAccessService.getProcessingJobs();
        if (agentName != null && !agentName.isEmpty()) {
            runningBuildJobs.removeIf(buildJobQueueItem -> !buildJobQueueItem.buildAgent().name().equals(agentName));
        }
        return ResponseEntity.ok(runningBuildJobs);
    }

    /**
     * Returns information on available build agents
     *
     * @return list of build agents information
     */
    @GetMapping("build-agents")
    public ResponseEntity<List<BuildAgentInformation>> getBuildAgentSummary() {
        log.debug("REST request to get information on available build agents");
        List<BuildAgentInformation> buildAgentSummary = distributedDataAccessService.getBuildAgentInformation();
        return ResponseEntity.ok(buildAgentSummary);
    }

    /**
     * Returns detailed information on a specific build agent
     *
     * @param agentName the name of the agent
     * @return the build agent information
     */
    @GetMapping("build-agent")
    public ResponseEntity<BuildAgentInformation> getBuildAgentDetails(@RequestParam String agentName) {
        log.debug("REST request to get information on build agent {}", agentName);
        Optional<BuildAgentInformation> buildAgentDetails = distributedDataAccessService.getBuildAgentInformation().stream()
                .filter(agent -> agent.buildAgent().name().equals(agentName)).findFirst();
        return buildAgentDetails.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Cancels the build job for the given participation.
     *
     * @param buildJobId the id of the build job to cancel
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("cancel-job/{buildJobId}")
    public ResponseEntity<Void> cancelBuildJob(@PathVariable String buildJobId) {
        log.debug("REST request to cancel the build job with id {}", buildJobId);
        // Call the cancelBuildJob method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelBuildJob(buildJobId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Cancels all queued build jobs.
     *
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("cancel-all-queued-jobs")
    public ResponseEntity<Void> cancelAllQueuedBuildJobs() {
        log.debug("REST request to cancel all queued build jobs");
        // Call the cancelAllQueuedBuildJobs method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelAllQueuedBuildJobs();

        return ResponseEntity.noContent().build();
    }

    /**
     * Cancels all running build jobs.
     *
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("cancel-all-running-jobs")
    public ResponseEntity<Void> cancelAllRunningBuildJobs() {
        log.debug("REST request to cancel all running build jobs");
        // Call the cancelAllRunningBuildJobs method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelAllRunningBuildJobs();

        return ResponseEntity.noContent().build();
    }

    /**
     * Cancels all running build jobs for the given agents
     *
     * @param agentName the name of the agent
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("cancel-all-running-jobs-for-agent")
    public ResponseEntity<Void> cancelAllRunningBuildJobsForAgent(@RequestParam String agentName) {
        log.debug("REST request to cancel all running build jobs for agent {}", agentName);
        // Call the cancelAllRunningBuildJobsForAgent method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelAllRunningBuildJobsForAgent(agentName);

        return ResponseEntity.noContent().build();
    }

    /**
     * Returns a page of finished build jobs.
     *
     * @param search the pagable search object
     * @return the page of finished build jobs
     */
    @GetMapping("finished-jobs")
    public ResponseEntity<List<FinishedBuildJobDTO>> getFinishedBuildJobs(FinishedBuildJobPageableSearchDTO search) {
        log.debug("REST request to get a page of finished build jobs with build status {}, build agent address {}, start date {} and end date {}", search.buildStatus(),
                search.buildAgentAddress(), search.startDate(), search.endDate());

        Slice<BuildJob> buildJobPage = localCIBuildJobQueueService.getFilteredFinishedBuildJobs(search, null);

        Slice<FinishedBuildJobDTO> finishedBuildJobDTOs = FinishedBuildJobDTO.fromBuildJobsSlice(buildJobPage);
        HttpHeaders headers = SliceUtil.generateSliceHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), buildJobPage);
        return new ResponseEntity<>(finishedBuildJobDTOs.getContent(), headers, HttpStatus.OK);
    }

    /**
     * Returns the build job statistics.
     *
     * @param span the time span in days. The statistics will be calculated for the last span days. Default is 7 days.
     * @return the build job statistics
     */
    @GetMapping("build-job-statistics")
    public ResponseEntity<BuildJobsStatisticsDTO> getBuildJobStatistics(@RequestParam(required = false, defaultValue = "7") int span) {
        log.debug("REST request to get the build job statistics");
        List<BuildJobResultCountDTO> buildJobResultCountDtos = buildJobRepository.getBuildJobsResultsStatistics(ZonedDateTime.now().minusDays(span), null);
        BuildJobsStatisticsDTO buildJobStatistics = BuildJobsStatisticsDTO.of(buildJobResultCountDtos);
        return ResponseEntity.ok(buildJobStatistics);
    }

    /**
     * {@code PUT /admin/agents/{agentName}/pause} : Pause the specified build agent.
     * This endpoint allows administrators to pause a specific build agent by its name.
     * Pausing a build agent will prevent it from picking up any new build jobs until it is resumed.
     *
     * <p>
     * <strong>Authorization:</strong> This operation requires admin privileges, enforced by {@code @EnforceAdmin}.
     * </p>
     *
     * @param agentName the name of the build agent to be paused (provided as a path variable)
     * @return {@link ResponseEntity} with status code 204 (No Content) if the agent was successfully paused
     *         or an appropriate error response if something went wrong
     */
    @PutMapping("agents/{agentName}/pause")
    public ResponseEntity<Void> pauseBuildAgent(@PathVariable String agentName) {
        log.debug("REST request to pause agent {}", agentName);
        localCIBuildJobQueueService.pauseBuildAgent(agentName);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PUT /admin/agents/pause-all} : Pause all build agents.
     * This endpoint allows administrators to pause all build agents.
     * Pausing all build agents will prevent them from picking up any new build jobs until they are resumed.
     *
     * <p>
     * <strong>Authorization:</strong> This operation requires admin privileges, enforced by {@code @EnforceAdmin}.
     * </p>
     *
     * @return {@link ResponseEntity} with status code 204 (No Content) if all agents were successfully paused
     *         or an appropriate error response if something went wrong
     */
    @PutMapping("agents/pause-all")
    public ResponseEntity<Void> pauseAllBuildAgents() {
        log.debug("REST request to pause all agents");
        localCIBuildJobQueueService.pauseAllBuildAgents();
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PUT /admin/agents/{agentName}/resume} : Resume the specified build agent.
     * This endpoint allows administrators to resume a specific build agent by its name.
     * Resuming a build agent will allow it to pick up new build jobs again.
     *
     * <p>
     * <strong>Authorization:</strong> This operation requires admin privileges, enforced by {@code @EnforceAdmin}.
     * </p>
     *
     * @param agentName the name of the build agent to be resumed (provided as a path variable)
     * @return {@link ResponseEntity} with status code 204 (No Content) if the agent was successfully resumed
     *         or an appropriate error response if something went wrong
     */
    @PutMapping("agents/{agentName}/resume")
    public ResponseEntity<Void> resumeBuildAgent(@PathVariable String agentName) {
        log.debug("REST request to resume agent {}", agentName);
        localCIBuildJobQueueService.resumeBuildAgent(agentName);
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PUT /admin/agents/resume-all} : Resume all build agents.
     * This endpoint allows administrators to resume all build agents.
     * Resuming all build agents will allow them to pick up new build jobs again.
     *
     * <p>
     * <strong>Authorization:</strong> This operation requires admin privileges, enforced by {@code @EnforceAdmin}.
     * </p>
     *
     * @return {@link ResponseEntity} with status code 204 (No Content) if all agents were successfully resumed
     *         or an appropriate error response if something went wrong
     */
    @PutMapping("agents/resume-all")
    public ResponseEntity<Void> resumeAllBuildAgents() {
        log.debug("REST request to resume all agents");
        localCIBuildJobQueueService.resumeAllBuildAgents();
        return ResponseEntity.noContent().build();
    }

    /**
     * {@code PUT /admin/clear-distributed-data} : Clear all distributed data.
     * This endpoint allows administrators to clear all distributed data. See {@link SharedQueueManagementService#clearDistributedData()}.
     *
     * <p>
     * <strong>Authorization:</strong> This operation requires admin privileges, enforced by {@code @EnforceAdmin}.
     * </p>
     *
     * @return {@link ResponseEntity} with status code 200 (OK) if the distributed data was successfully cleared
     *         or an appropriate error response if something went wrong
     */
    @DeleteMapping("clear-distributed-data")
    public ResponseEntity<Void> clearDistributedData() {
        log.debug("REST request to clear distributed data");
        localCIBuildJobQueueService.clearDistributedData();
        return ResponseEntity.noContent().build();
    }
}
