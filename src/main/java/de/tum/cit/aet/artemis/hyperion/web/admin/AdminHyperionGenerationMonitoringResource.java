package de.tum.cit.aet.artemis.hyperion.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.hyperion.dto.ActiveGenerationDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationMonitoringService;

/** Live, audited generation controls for administrators. */
@Lazy
@RestController
@Profile(PROFILE_CORE)
@RequestMapping("api/hyperion/admin/exercises/")
@EnforceAdmin
@FeatureUsage("authoring-assistance/generation-monitoring")
public class AdminHyperionGenerationMonitoringResource {

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}]+");

    private final Optional<GenerationMonitoringService> jobs;

    private final AuditEventRepository audit;

    public AdminHyperionGenerationMonitoringResource(Optional<GenerationMonitoringService> jobs, AuditEventRepository audit) {
        this.jobs = jobs;
        this.audit = audit;
    }

    /**
     * Lists active generation jobs across core nodes.
     *
     * @return metadata-only live jobs, including their cancellation availability
     */
    @GetMapping("generations")
    public ResponseEntity<List<ActiveGenerationDTO>> getActiveGenerations() {
        return ResponseEntity.ok(jobs.map(GenerationMonitoringService::activeGenerations).orElseGet(List::of));
    }

    /**
     * Audits and requests cancellation of one exact run, never a replacement run or a save in progress.
     *
     * @param exerciseId   the exercise
     * @param generationId the selected generation
     * @param reason       the operator's reason
     * @return 202 when requested, 400 for an invalid reason, or 409 when no longer cancellable
     */
    @DeleteMapping("{exerciseId}/generations/{generationId}")
    public ResponseEntity<Void> cancelGeneration(@PathVariable long exerciseId, @PathVariable String generationId, @RequestParam String reason) {
        String boundedReason = CONTROL_CHARACTERS.matcher(reason).replaceAll(" ").trim();
        if (boundedReason.isBlank() || boundedReason.length() > 500 || generationId.length() > 128) {
            return ResponseEntity.badRequest().build();
        }
        audit.add(new AuditEvent(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "HYPERION_GENERATION_CANCEL_ATTEMPT",
                Map.of("exerciseId", exerciseId, "jobId", generationId, "reason", boundedReason)));
        return jobs.filter(service -> service.cancel(exerciseId, generationId)).isPresent() ? ResponseEntity.accepted().build() : ResponseEntity.status(409).build();
    }

}
