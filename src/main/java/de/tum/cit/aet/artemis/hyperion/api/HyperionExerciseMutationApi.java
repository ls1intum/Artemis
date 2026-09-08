package de.tum.cit.aet.artemis.hyperion.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;

/** Cross-module API for serializing external programming-exercise mutations with Hyperion generation. */
@Controller
@Lazy
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
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
}
