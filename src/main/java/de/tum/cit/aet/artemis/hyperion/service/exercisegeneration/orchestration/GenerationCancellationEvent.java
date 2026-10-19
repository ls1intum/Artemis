package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO;

public record GenerationCancellationEvent(String userLogin, String jobId, ExerciseGenerationEventDTO event) {
}
