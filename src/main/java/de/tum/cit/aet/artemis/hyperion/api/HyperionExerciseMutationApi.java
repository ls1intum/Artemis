package de.tum.cit.aet.artemis.hyperion.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.util.function.BooleanSupplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.api.dtos.ParticipationReservation;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;

/** Cross-module API for coordinating durable exercise saves with ordinary writes and repository copies. */
@Controller
@Lazy
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
@ConditionalOnProperty(name = "artemis.hyperion.exercise-generation.enabled", havingValue = "true")
public class HyperionExerciseMutationApi implements AbstractApi {

    private final GenerationLiveSaveService mutationService;

    public HyperionExerciseMutationApi(GenerationLiveSaveService mutationService) {
        this.mutationService = mutationService;
    }

    /** @return whether this deployment enables generation coordination for exercise writers */
    public boolean isCoordinationEnabled() {
        return mutationService.isCoordinationEnabled();
    }

    public String claimExternalMutationSlot(long exerciseId) {
        return mutationService.claim(exerciseId);
    }

    public void clearExternalMutationSlot(long exerciseId, String token) {
        mutationService.release(exerciseId, token);
    }

    public ParticipationReservation reserveParticipation(long exerciseId) {
        String token = mutationService.claimReader(exerciseId);
        return new ParticipationReservation(() -> mutationService.releaseReader(exerciseId, token));
    }

    /**
     * Coordinates canonical test discovery without discarding historical build results.
     *
     * @param exerciseId     protected exercise
     * @param solutionCommit result solution revision
     * @param testsCommit    result tests revision
     * @param callback       revision-checked canonical discovery
     * @return whether canonical discovery accepted the result
     */
    public boolean synchronizeTestCases(long exerciseId, String solutionCommit, String testsCommit, BooleanSupplier callback) {
        return mutationService.synchronizeTestCases(exerciseId, solutionCommit, testsCommit, callback);
    }

}
