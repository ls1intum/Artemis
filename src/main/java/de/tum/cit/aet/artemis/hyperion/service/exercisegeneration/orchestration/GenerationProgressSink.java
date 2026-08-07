package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;

/** Progress sink that optionally attaches structured repair-round telemetry. Text-only consumers receive only the message. */
@FunctionalInterface
public interface GenerationProgressSink extends Consumer<String> {

    default void progress(String message, ExerciseGenerationRepairRoundDTO repairRound) {
        accept(message);
    }
}
