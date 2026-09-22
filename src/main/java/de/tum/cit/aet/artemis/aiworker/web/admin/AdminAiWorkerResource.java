package de.tum.cit.aet.artemis.aiworker.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.aiworker.dto.WorkerStatusDTO;
import de.tum.cit.aet.artemis.aiworker.service.WorkerRegistryService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;

/** Read-only operational visibility for standalone generation workers. */
@Lazy
@RestController
@Profile(PROFILE_CORE)
@RequestMapping("api/aiworker/admin/")
@EnforceAdmin
@FeatureUsage("aiworker/workers")
public class AdminAiWorkerResource {

    private final Optional<WorkerRegistryService> workers;

    public AdminAiWorkerResource(Optional<WorkerRegistryService> workers) {
        this.workers = workers;
    }

    /**
     * Lists configured workers with their latest core-observed heartbeat and capacity state.
     *
     * @return diagnostic snapshot, or 404 when AI Worker coordination is disabled
     */
    @GetMapping("workers")
    public ResponseEntity<List<WorkerStatusDTO>> getWorkers() {
        return workers.map(registry -> ResponseEntity.ok(registry.workerStatuses())).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
