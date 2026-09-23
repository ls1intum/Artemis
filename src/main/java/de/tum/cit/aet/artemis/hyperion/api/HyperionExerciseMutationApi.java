package de.tum.cit.aet.artemis.hyperion.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.api.dtos.ParticipationReservation;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;

/** Cross-module API for serializing external programming-exercise mutations with Hyperion generation. */
@Controller
@Lazy
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
@ConditionalOnProperty(name = "artemis.hyperion.exercise-generation.enabled", havingValue = "true")
public class HyperionExerciseMutationApi implements AbstractApi {

    private final GenerationExternalMutationService mutationService;

    public HyperionExerciseMutationApi(GenerationExternalMutationService mutationService) {
        this.mutationService = mutationService;
    }

    public String claimExternalMutationSlot(long exerciseId) {
        return mutationService.claimExternalMutationSlot(exerciseId);
    }

    public void clearExternalMutationSlot(long exerciseId, String token) {
        mutationService.clearExternalMutationSlot(exerciseId, token);
    }

    /**
     * Claims a shared template-copy reservation that excludes generation and external writers.
     *
     * @param exerciseId exercise to reserve
     * @return exact reservation token
     */
    public String claimParticipationSlot(long exerciseId) {
        return mutationService.claimParticipationSlot(exerciseId);
    }

    /**
     * Releases only the matching template-copy reservation.
     *
     * @param exerciseId protected exercise
     * @param token      exact reservation token
     */
    public void clearParticipationSlot(long exerciseId, String token) {
        mutationService.clearParticipationSlot(exerciseId, token);
    }

    public boolean isGenerationActive(long exerciseId) {
        return mutationService.isGenerationActive(exerciseId);
    }

    public ParticipationReservation reserveParticipation(long exerciseId) {
        String token = claimParticipationSlot(exerciseId);
        return new ParticipationReservation(() -> clearParticipationSlot(exerciseId, token));
    }

}
