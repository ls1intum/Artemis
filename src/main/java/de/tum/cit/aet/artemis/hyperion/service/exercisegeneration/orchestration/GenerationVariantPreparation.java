package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;

/** Core-local preparation of a new destination; the transformation itself is the ordinary worker adaptation. */
public record GenerationVariantPreparation(long sourceExerciseId, String sourceReservationToken, VariantGenerationRequestDTO request) {
}
