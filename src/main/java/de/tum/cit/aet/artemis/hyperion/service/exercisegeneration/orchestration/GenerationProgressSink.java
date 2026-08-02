package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;

/** Progress sink that optionally attaches structured repair-round telemetry. */
@FunctionalInterface
public interface GenerationProgressSink extends Consumer<String> {

    /**
     * Reports progress and optional repair telemetry. Text-only consumers receive only the message.
     *
     * @param message     the progress message
     * @param repairRound the repair telemetry, if present
     */
    default void progress(String message, ExerciseGenerationRepairRoundDTO repairRound) {
        accept(message);
    }
}
