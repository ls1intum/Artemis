package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.connectors.localci.LocalCISharedBuildJobQueueService;
import de.tum.in.www1.artemis.service.connectors.localci.dto.LocalCIBuildJobQueueItem;

@Profile("localci")
@RestController
@RequestMapping("/api/admin")
public class AdminBuildJobQueueResource {

    private final LocalCISharedBuildJobQueueService localCIBuildJobQueueService;

    private final Logger log = LoggerFactory.getLogger(AdminBuildJobQueueResource.class);

    public AdminBuildJobQueueResource(LocalCISharedBuildJobQueueService localCIBuildJobQueueService) {
        this.localCIBuildJobQueueService = localCIBuildJobQueueService;
    }

    @GetMapping("/build-job-queue/queued")
    @EnforceAdmin
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getQueuedBuildJobs() {
        log.debug("REST request to get the queued build jobs");
        List<LocalCIBuildJobQueueItem> buildJobQueue = localCIBuildJobQueueService.getQueuedJobs();
        return ResponseEntity.ok(buildJobQueue);
    }

    @GetMapping("/build-job-queue/running")
    @EnforceAdmin
    public ResponseEntity<List<LocalCIBuildJobQueueItem>> getRunningBuildJobs() {
        log.debug("REST request to get the running build jobs");
        List<LocalCIBuildJobQueueItem> runningBuildJobs = localCIBuildJobQueueService.getProcessingJobs();
        return ResponseEntity.ok(runningBuildJobs);
    }
}
