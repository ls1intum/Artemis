package de.tum.cit.aet.artemis.hyperion.web;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastEditorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant.GenerationVariantService;

/** Programming variants use the common authoring lifecycle; quiz variants retain their independent domain workflow. */
@RestController
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
@RequestMapping("api/hyperion/")
@FeatureUsage(UserFeature.HYPERION_VARIANT_GENERATION)
public class HyperionProgrammingVariantResource {

    private final UserRepository users;

    private final GenerationVariantService variants;

    public HyperionProgrammingVariantResource(UserRepository users, GenerationVariantService variants) {
        this.users = users;
        this.variants = variants;
    }

    /**
     * Adapts an authorized source into a new protected destination using the isolated authoring worker.
     *
     * @param exerciseId authorized source exercise
     * @param request    structured transformation and placement
     * @return accepted common job with its source and destination ids
     */
    @PostMapping("programming-exercises/{exerciseId}/generation/variants")
    @EnforceAtLeastEditorInExercise
    public ResponseEntity<ExerciseGenerationJobStartDTO> generateProgrammingVariant(@PathVariable long exerciseId, @Valid @RequestBody VariantGenerationRequestDTO request) {
        return ResponseEntity.accepted().body(variants.start(users.getUserWithAuthorities(), exerciseId, request));
    }
}
