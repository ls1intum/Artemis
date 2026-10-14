package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService;

/** Serializes canonical exercise writes through the database, independently of cache membership. */
@Lazy
@Service
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class GenerationExternalMutationService {

    private final ProgrammingExerciseWriteOwnershipService ownership;

    @org.springframework.beans.factory.annotation.Autowired
    public GenerationExternalMutationService(@Lazy ProgrammingExerciseWriteOwnershipService ownership) {
        this.ownership = ownership;
    }

    @Deprecated(forRemoval = true)
    public GenerationExternalMutationService(de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider ignored, int ignoredMembers) {
        throw new IllegalArgumentException("Database ownership service is required");
    }

    @Deprecated(forRemoval = true)
    public GenerationExternalMutationService(de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider ignored, int ignoredMembers, boolean ignoredEnabled) {
        throw new IllegalArgumentException("Database ownership service is required");
    }

    public void clearSeedSlot(long exerciseId, String token) {
        ownership.release(exerciseId, token);
    }

    public boolean isUndoRecovery(long exerciseId) {
        return ownership.get(exerciseId).map(slot -> slot.purpose().equals("UNDO_RECOVERY")).orElse(false);
    }

    public String claimExternalMutationSlot(long exerciseId) {
        return ownership.claim(exerciseId, "WRITE", GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + UUID.randomUUID());
    }

    public boolean isCoordinationEnabled() {
        return true;
    }

    public String claimParticipationSlot(long exerciseId) {
        return ownership.claimReader(exerciseId);
    }

    public void clearParticipationSlot(long exerciseId, String token) {
        ownership.releaseReader(exerciseId, token);
    }

    public void clearExternalMutationSlot(long exerciseId, String token) {
        ownership.release(exerciseId, token);
    }

    public String claimSeedSlot(long exerciseId) {
        return ownership.claim(exerciseId, "SEED");
    }

    public void claimSaveSlot(long exerciseId, String jobId) {
        ownership.claim(exerciseId, "SAVE", jobId);
    }

    public boolean owns(long exerciseId, String token) {
        return ownership.get(exerciseId).map(slot -> slot.token().equals(token) && !slot.purpose().endsWith("_RECOVERY")).orElse(false);
    }

    public void releaseSaveSlot(long exerciseId, String token) {
        releaseSaveSlotIfOwned(exerciseId, token);
    }

    /**
     * Releases only an active save; a recovery reservation with the same token remains held.
     *
     * @param exerciseId protected exercise
     * @param token      exact save token
     * @return whether the active save was released
     */
    public boolean releaseSaveSlotIfOwned(long exerciseId, String token) {
        return ownership.releaseActiveSave(exerciseId, token);
    }

    public void retainSaveRecovery(long exerciseId, String token) {
        ownership.retain(exerciseId, token, "SAVE_RECOVERY");
    }

    String claimRevertSlot(long exerciseId) {
        var current = ownership.get(exerciseId);
        if (current.isPresent() && current.get().purpose().equals("UNDO_RECOVERY")) {
            String retry = GenerationRecoverySlots.RETRY_PREFIX + UUID.randomUUID();
            if (!ownership.replaceRecovery(exerciseId, current.get().token(), "UNDO_RECOVERY", retry, "UNDO")) {
                throw new ConflictException("The undo recovery reservation changed.", "hyperionExerciseGeneration", "exerciseGenerationRunning");
            }
            return retry;
        }
        return ownership.claim(exerciseId, "UNDO", GenerationJobService.REVERT_JOB_PREFIX + UUID.randomUUID());
    }

    void releaseRevertSlot(long exerciseId, String token, GenerationJobService.JobInfo previousRecovery) {
        ownership.release(exerciseId, token);
    }

    void retainRevertRecoverySlot(long exerciseId, String token) {
        ownership.retain(exerciseId, token, "UNDO_RECOVERY");
    }

    public boolean isGenerationActive(long exerciseId) {
        return ownership.get(exerciseId).isPresent() || !ownership.readers(exerciseId).isEmpty();
    }

    /**
     * Reads the current durable writer or source-copy reservation for operator reconciliation.
     *
     * @param exerciseId protected exercise
     * @return one exact reservation, or empty when none is held
     */
    public Optional<GenerationJobService.WedgedSlotInfo> getWedgedSlotInfo(long exerciseId) {
        return ownership.get(exerciseId).or(() -> ownership.readers(exerciseId).stream().findFirst())
                .map(slot -> new GenerationJobService.WedgedSlotInfo(exerciseId, slot.token(),
                        slot.purpose().equals("UNDO_RECOVERY") ? GenerationJobService.WedgedSlotKind.REVERT_RECOVERY
                                : slot.purpose().startsWith("SAVE") ? GenerationJobService.WedgedSlotKind.GENERATION
                                        : slot.purpose().equals("UNDO") ? GenerationJobService.WedgedSlotKind.REVERT : GenerationJobService.WedgedSlotKind.EXTERNAL_MUTATION,
                        slot.owner(), slot.startedAt(), false));
    }

    /**
     * Releases an exact reservation after the administrator establishes writer or reader quiescence.
     *
     * @param exerciseId protected exercise
     * @param token      exact reservation token
     * @return whether the matching reservation was released
     */
    public boolean recoverWedgedSlot(long exerciseId, String token) {
        return ownership.release(exerciseId, token) || ownership.releaseReader(exerciseId, token);
    }
}
