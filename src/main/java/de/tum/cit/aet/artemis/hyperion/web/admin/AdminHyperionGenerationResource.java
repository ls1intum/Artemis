package de.tum.cit.aet.artemis.hyperion.web.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

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
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationWedgedSlotDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;

/** Audited recovery of fail-closed slots; it never runs commands on generation workers. */
@Lazy
@RestController
@Profile(PROFILE_CORE)
@RequestMapping("api/hyperion/admin/exercises/")
@EnforceAdmin
@FeatureUsage(UserFeature.HYPERION_GENERATION_RECOVERY)
public class AdminHyperionGenerationResource {

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\p{Cntrl}]+");

    private final Optional<GenerationJobService> jobs;

    private final GenerationLiveSaveService liveSaves;

    private final AuditEventRepository audit;

    public AdminHyperionGenerationResource(Optional<GenerationJobService> jobs, GenerationLiveSaveService liveSaves, AuditEventRepository audit) {
        this.jobs = jobs;
        this.liveSaves = liveSaves;
        this.audit = audit;
    }

    /**
     * Returns a stale authoring, save, or undo reservation without releasing it or checking process liveness.
     *
     * @param exerciseId exercise whose Hyperion reservation should be inspected
     * @return administrator-only reservation details, or 404
     */
    @GetMapping("{exerciseId}/wedged-slot")
    public ResponseEntity<ExerciseGenerationWedgedSlotDTO> getWedgedSlot(@PathVariable long exerciseId) {
        return slotInfo(exerciseId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Releases an exact reservation after the operator has stopped the old core and worker executions and reconciled any incomplete writes.
     * This operation does not check process liveness or cluster topology.
     *
     * @param exerciseId exercise to recover
     * @param token      expected ownership token from the diagnostic operation
     * @param reason     incident reason recorded before attempting recovery
     * @return 204 on recovery, 400 for an invalid reason, or 404 if the reservation no longer matches
     */
    @DeleteMapping("{exerciseId}/wedged-slot/{token}")
    public ResponseEntity<Void> recoverWedgedSlot(@PathVariable long exerciseId, @PathVariable String token, @RequestParam String reason) {
        String boundedReason = CONTROL_CHARACTERS.matcher(reason).replaceAll(" ").trim();
        if (boundedReason.isBlank() || boundedReason.length() > 500) {
            return ResponseEntity.badRequest().build();
        }
        var wedged = slotInfo(exerciseId).filter(info -> info.token().equals(token));
        if (wedged.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var info = wedged.get();
        audit.add(new AuditEvent(SecurityUtils.getCurrentUserLogin().orElse("unknown"), "HYPERION_SLOT_RECOVERY_ATTEMPT", Map.of("exerciseId", exerciseId, "token", token, "kind",
                info.kind(), "ownerNodeId", info.ownerNodeId() == null ? "unknown" : info.ownerNodeId(), "reason", boundedReason)));
        boolean recovered = "LIVE_SAVE".equals(info.kind()) ? liveSaves.recoverSlot(exerciseId, token)
                : jobs.map(service -> service.recoverWedgedSlot(exerciseId, token)).orElse(false);
        return recovered ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    private Optional<ExerciseGenerationWedgedSlotDTO> slotInfo(long exerciseId) {
        var live = liveSaves.getSlotInfo(exerciseId);
        if (live.isPresent()) {
            var slot = live.get();
            return Optional.of(new ExerciseGenerationWedgedSlotDTO(exerciseId, slot.token(), "LIVE_SAVE", slot.ownerNodeId(), slot.startedAt(), false));
        }
        return jobs.flatMap(service -> service.getWedgedSlotInfo(exerciseId)).map(
                info -> new ExerciseGenerationWedgedSlotDTO(info.exerciseId(), info.token(), info.kind().name(), info.ownerNodeId(), info.startedAt(), info.ownerLeftCluster()));
    }

}
