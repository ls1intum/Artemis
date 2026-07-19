package de.tum.cit.aet.artemis.hyperion.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;

/** Cross-module API for serializing external programming-exercise mutations with Hyperion generation. */
@Controller
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class HyperionExerciseMutationApi implements AbstractApi {

    private final GenerationJobService generationJobService;

    public HyperionExerciseMutationApi(GenerationJobService generationJobService) {
        this.generationJobService = generationJobService;
    }

    public String claimExternalMutationSlot(long exerciseId) {
        return generationJobService.claimExternalMutationSlot(exerciseId);
    }

    public void clearExternalMutationSlot(long exerciseId, String token) {
        generationJobService.clearExternalMutationSlot(exerciseId, token);
    }
}
