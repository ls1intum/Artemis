package de.tum.cit.aet.artemis.hyperion.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.api.dtos.ParticipationReservation;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService;

/** Cross-module API for serializing external programming-exercise mutations with Hyperion generation. */
@Controller
@Lazy
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class HyperionExerciseMutationApi implements AbstractApi {

    private final ProgrammingExerciseWriteOwnershipService ownership;

    public HyperionExerciseMutationApi(ProgrammingExerciseWriteOwnershipService ownership) {
        this.ownership = ownership;
    }

    /** @return whether this deployment enables generation coordination for exercise writers */
    public boolean isCoordinationEnabled() {
        return true;
    }

    public String claimExternalMutationSlot(long exerciseId) {
        return ownership.claim(exerciseId, "WRITE", GenerationJobService.EXTERNAL_MUTATION_JOB_PREFIX + UUID.randomUUID());
    }

    public void clearExternalMutationSlot(long exerciseId, String token) {
        ownership.release(exerciseId, token);
    }

    /**
     * Claims a shared template-copy reservation that excludes generation and external writers.
     *
     * @param exerciseId exercise to reserve
     * @return exact reservation token
     */
    public String claimParticipationSlot(long exerciseId) {
        return ownership.claimReader(exerciseId);
    }

    /**
     * Releases only the matching template-copy reservation.
     *
     * @param exerciseId protected exercise
     * @param token      exact reservation token
     */
    public void clearParticipationSlot(long exerciseId, String token) {
        ownership.releaseReader(exerciseId, token);
    }

    public boolean isGenerationActive(long exerciseId) {
        return ownership.get(exerciseId).isPresent() || !ownership.readers(exerciseId).isEmpty();
    }

    public ParticipationReservation reserveParticipation(long exerciseId) {
        String token = claimParticipationSlot(exerciseId);
        return new ParticipationReservation(() -> clearParticipationSlot(exerciseId, token));
    }

}
