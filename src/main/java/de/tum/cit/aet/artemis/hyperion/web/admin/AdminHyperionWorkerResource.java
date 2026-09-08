package de.tum.cit.aet.artemis.hyperion.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.hyperion.dto.GenerationWorkerStatusDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.worker.GenerationWorkerRegistryService;

/** Read-only operational visibility for standalone generation workers. */
@Lazy
@RestController
@Profile(PROFILE_CORE)
@RequestMapping("api/hyperion/admin/")
@EnforceAdmin
@FeatureUsage("authoring-assistance/generation-workers")
public class AdminHyperionWorkerResource {

    private final Optional<GenerationWorkerRegistryService> workers;

    public AdminHyperionWorkerResource(Optional<GenerationWorkerRegistryService> workers) {
        this.workers = workers;
    }

    /**
     * Lists configured workers with their latest core-observed heartbeat and capacity state.
     *
     * @return diagnostic snapshot, or 404 when whole-exercise generation is disabled
     */
    @GetMapping("workers")
    public ResponseEntity<List<GenerationWorkerStatusDTO>> getGenerationWorkers() {
        return workers.map(registry -> ResponseEntity.ok(registry.workerStatuses())).orElseGet(() -> ResponseEntity.notFound().build());
    }
}
