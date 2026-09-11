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

    /**
     * Read-only check whether Hyperion (a generation run, a revert, or an external mutation) currently owns the exercise. Unlike {@link #claimExternalMutationSlot(long)} this
     * never takes the slot, so it is safe for high-frequency callers such as student participation starts that must not serialize each other.
     *
     * @param exerciseId the programming exercise
     * @return whether a run or mutation holds the exercise
     */
    public boolean isGenerationActive(long exerciseId) {
        return mutationService.isGenerationActive(exerciseId);
    }
}
