package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationStateDTO;

/** Event published when a generation job acquires or releases its exercise mutation slot. */
public record ExerciseGenerationStateChangedEvent(ExerciseGenerationStateDTO state) {
}
