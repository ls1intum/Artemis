package de.tum.in.www1.artemis.web.rest.admin;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_LOCALCI;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.repository.BuildJobRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.connectors.localci.SharedQueueManagementService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.BuildJobQueueItem;
import de.tum.in.www1.artemis.service.dto.FinishedBuildJobDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.FinishedBuildJobPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;
import tech.jhipster.web.util.PaginationUtil;

@Profile(PROFILE_LOCALCI)
@RestController
@RequestMapping("api/admin/")
public class AdminBuildJobQueueResource {

    private final SharedQueueManagementService localCIBuildJobQueueService;

    private final BuildJobRepository buildJobRepository;

    private static final Logger log = LoggerFactory.getLogger(AdminBuildJobQueueResource.class);

    public AdminBuildJobQueueResource(SharedQueueManagementService localCIBuildJobQueueService, BuildJobRepository buildJobRepository) {
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
        this.buildJobRepository = buildJobRepository;
    }

    /**
     * Returns the queued build jobs.
     *
     * @return the queued build jobs
     */
    @GetMapping("queued-jobs")
    @EnforceAdmin
    public ResponseEntity<List<BuildJobQueueItem>> getQueuedBuildJobs() {
        log.debug("REST request to get the queued build jobs");
        List<BuildJobQueueItem> buildJobQueue = localCIBuildJobQueueService.getQueuedJobs();
        return ResponseEntity.ok(buildJobQueue);
    }

    /**
     * Returns the running build jobs.
     *
     * @return the running build jobs
     */
    @GetMapping("running-jobs")
    @EnforceAdmin
    public ResponseEntity<List<BuildJobQueueItem>> getRunningBuildJobs() {
        log.debug("REST request to get the running build jobs");
        List<BuildJobQueueItem> runningBuildJobs = localCIBuildJobQueueService.getProcessingJobs();
        return ResponseEntity.ok(runningBuildJobs);
    }

    /**
     * Returns information on available build agents
     *
     * @return list of build agents information
     */
    @GetMapping("build-agents")
    @EnforceAdmin
    public ResponseEntity<List<BuildAgentInformation>> getBuildAgentSummary() {
        log.debug("REST request to get information on available build agents");
        List<BuildAgentInformation> buildAgentSummary = localCIBuildJobQueueService.getBuildAgentInformationWithoutRecentBuildJobs();
        return ResponseEntity.ok(buildAgentSummary);
    }

    /**
     * Returns detailed information on a specific build agent
     *
     * @param agentName the name of the agent
     * @return the build agent information
     */
    @GetMapping("build-agent")
    @EnforceAdmin
    public ResponseEntity<BuildAgentInformation> getBuildAgentDetails(@RequestParam String agentName) {
        log.debug("REST request to get information on build agent {}", agentName);
        BuildAgentInformation buildAgentDetails = localCIBuildJobQueueService.getBuildAgentInformation().stream().filter(agent -> agent.name().equals(agentName)).findFirst()
                .orElse(null);
        return ResponseEntity.ok(buildAgentDetails);
    }

    /**
     * Cancels the build job for the given participation.
     *
     * @param buildJobId the id of the build job to cancel
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("cancel-job/{buildJobId}")
    @EnforceAdmin
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
    @EnforceAdmin
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
    @EnforceAdmin
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
    @EnforceAdmin
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
    @EnforceAdmin
    public ResponseEntity<List<FinishedBuildJobDTO>> getFinishedBuildJobs(PageableSearchDTO<String> search) {
        log.debug("REST request to get a page of finished build jobs");
        final Page<BuildJob> page = buildJobRepository.findAll(PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.BUILD_JOB));
        Page<FinishedBuildJobDTO> finishedBuildJobDTOs = FinishedBuildJobDTO.fromBuildJobsPage(page);
        log.debug("Found {} finished build jobs", finishedBuildJobDTOs.getTotalElements());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(finishedBuildJobDTOs.getContent(), headers, HttpStatus.OK);
    }

    @GetMapping("finished-jobs-custom")
    @EnforceAdmin
    public ResponseEntity<List<FinishedBuildJobDTO>> getFinishedBuildJobsCustom(FinishedBuildJobPageableSearchDTO search) {
        log.debug("REST request to get a page of finished build jobs with build status {}, build agent address {}, start date {} and end date {}", search.getBuildStatus(),
                search.getBuildAgentAddress(), search.getStartDate(), search.getEndDate());

        final Page<BuildJob> page = buildJobRepository.findAllByBuildStatusAndBuildAgentAddressAndBuildStartDateBetween(search.getBuildStatus(), search.getBuildAgentAddress(),
                search.getStartDate(), search.getEndDate(), search.getSearchTerm(), PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.BUILD_JOB));
        List<BuildJob> filteredBuildJobs = page.get().filter(buildJob -> {
            Duration buildDuration = Duration.between(buildJob.getBuildStartDate(), buildJob.getBuildCompletionDate());
            return buildDuration.toSeconds() >= search.getBuildDurationLower() && buildDuration.toSeconds() <= search.getBuildDurationUpper();
        }).collect(Collectors.toList());

        Page<BuildJob> filteredPage = new PageImpl<>(filteredBuildJobs, page.getPageable(), filteredBuildJobs.size());

        Page<FinishedBuildJobDTO> finishedBuildJobDTOs = FinishedBuildJobDTO.fromBuildJobsPage(filteredPage);
        log.debug("Found {} finished build jobs", finishedBuildJobDTOs.getTotalElements());
        HttpHeaders headers = PaginationUtil.generatePaginationHttpHeaders(ServletUriComponentsBuilder.fromCurrentRequest(), page);
        return new ResponseEntity<>(finishedBuildJobDTOs.getContent(), headers, HttpStatus.OK);
    }
}
