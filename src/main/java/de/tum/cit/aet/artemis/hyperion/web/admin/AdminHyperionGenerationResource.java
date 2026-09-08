package de.tum.cit.aet.artemis.hyperion.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Map;
import java.util.Optional;

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
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationWedgedSlotDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;

/** Audited recovery of fail-closed slots; it never runs commands on generation workers. */
@Lazy
@RestController
@Profile(PROFILE_CORE)
@RequestMapping("api/hyperion/admin/exercises/")
@EnforceAdmin
@FeatureUsage("authoring-assistance/generation-recovery")
public class AdminHyperionGenerationResource {

    private final Optional<GenerationJobService> jobs;

    private final GenerationExternalMutationService mutations;

    private final AuditEventRepository audit;

    public AdminHyperionGenerationResource(Optional<GenerationJobService> jobs, GenerationExternalMutationService mutations, AuditEventRepository audit) {
        this.jobs = jobs;
        this.mutations = mutations;
        this.audit = audit;
    }

    /**
     * Returns an exact recovery token without releasing the slot.
     *
     * @param exerciseId exercise blocked by a non-cancellable slot
     * @return administrator-only ownership evidence, or 404
     */
    @GetMapping("{exerciseId}/hyperion-wedged-slot")
    public ResponseEntity<ExerciseGenerationWedgedSlotDTO> getWedgedSlot(@PathVariable long exerciseId) {
        return slotInfo(exerciseId)
                .map(info -> ResponseEntity.ok(
                        new ExerciseGenerationWedgedSlotDTO(info.exerciseId(), info.token(), info.kind().name(), info.ownerNodeId(), info.startedAt(), info.ownerLeftCluster())))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Releases an exact departed-owner slot after the operator has confirmed its JVM is stopped.
     *
     * @param exerciseId exercise to recover
     * @param token      expected ownership token from the diagnostic operation
     * @param reason     incident reason recorded before attempting recovery
     * @return 204 on recovery, 400 for an invalid reason, or 404 for stale ownership evidence
     */
    @DeleteMapping("{exerciseId}/hyperion-wedged-slots/{token}")
    public ResponseEntity<Void> recoverWedgedSlot(@PathVariable long exerciseId, @PathVariable String token, @RequestParam String reason) {
        String boundedReason = reason.replaceAll("[\\p{Cntrl}]+", " ").trim();
        if (boundedReason.isBlank() || boundedReason.length() > 500) {
            return ResponseEntity.badRequest().build();
        }
        var wedged = slotInfo(exerciseId).filter(info -> info.token().equals(token));
        if (wedged.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var info = wedged.get();
        audit.add(new AuditEvent(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "HYPERION_SLOT_RECOVERY_ATTEMPT", Map.of("exerciseId", exerciseId, "token", token, "kind",
                info.kind().name(), "ownerNodeId", info.ownerNodeId() == null ? "unknown" : info.ownerNodeId(), "reason", boundedReason)));
        boolean recovered = jobs.isPresent() ? jobs.get().recoverWedgedSlot(exerciseId, token) : mutations.recoverWedgedSlot(exerciseId, token);
        return recovered ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Optional<GenerationJobService.WedgedSlotInfo> slotInfo(long exerciseId) {
        return jobs.isPresent() ? jobs.get().getWedgedSlotInfo(exerciseId) : mutations.getWedgedSlotInfo(exerciseId);
    }

}
