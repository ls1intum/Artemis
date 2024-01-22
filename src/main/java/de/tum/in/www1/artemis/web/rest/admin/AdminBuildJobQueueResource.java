package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCISharedBuildJobQueueService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildAgentInformation;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;

@Profile("localci")
@RestController
@RequestMapping("/api/admin")
public class AdminBuildJobQueueResource {

    private final LocalCISharedBuildJobQueueService localCIBuildJobQueueService;

    private static final Logger log = LoggerFactory.getLogger(AdminBuildJobQueueResource.class);

    public AdminBuildJobQueueResource(LocalCISharedBuildJobQueueService localCIBuildJobQueueService) {
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
    }

    /**
     * Returns the queued build jobs.
     *
     * @return the queued build jobs
     */
    @GetMapping("/queued-jobs")
    @EnforceAdmin
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getQueuedBuildJobs() {
        log.debug("REST request to get the queued build jobs");
        List<LocalCIBuildJobQueueItem> buildJobQueue = localCIBuildJobQueueService.getQueuedJobs();
        return ResponseEntity.ok(buildJobQueue);
    }

    /**
     * Returns the running build jobs.
     *
     * @return the running build jobs
     */
    @GetMapping("/running-jobs")
    @EnforceAdmin
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getRunningBuildJobs() {
        log.debug("REST request to get the running build jobs");
        List<LocalCIBuildJobQueueItem> runningBuildJobs = localCIBuildJobQueueService.getProcessingJobs();
        return ResponseEntity.ok(runningBuildJobs);
    }

    /**
     * Returns information on available build agents
     *
     * @return list of build agents information
     */
    @GetMapping("/build-agents")
    @EnforceAdmin
    public ResponseEntity<List<LocalCIBuildAgentInformation>> getBuildAgentInformation() {
        log.debug("REST request to get information on available build agents");
        List<LocalCIBuildAgentInformation> buildAgentInfo = localCIBuildJobQueueService.getBuildAgentInformation();
        return ResponseEntity.ok(buildAgentInfo);
    }

    /**
     * Cancels the build job for the given participation.
     *
     * @param buildJobId the id of the build job to cancel
     * @return the ResponseEntity with the result of the cancellation
     */
    @DeleteMapping("/cancel-job/{buildJobId}")
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
    @DeleteMapping("/cancel-all-queued-jobs")
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
    @DeleteMapping("/cancel-all-running-jobs")
    @EnforceAdmin
    public ResponseEntity<Void> cancelAllRunningBuildJobs() {
        log.debug("REST request to cancel all running build jobs");
        // Call the cancelAllRunningBuildJobs method in LocalCIBuildJobManagementService
        localCIBuildJobQueueService.cancelAllRunningBuildJobs();

        return ResponseEntity.noContent().build();
    }
}
