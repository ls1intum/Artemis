package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationEventDTO.Phase;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.agent.AgentActivitySink;

/**
 * Progress sink that optionally attaches structured repair-round telemetry, and — through {@link AgentActivitySink} — the run's live activity. Text-only consumers receive only the
 * message.
 */
@FunctionalInterface
public interface GenerationProgressSink extends AgentActivitySink {

    default void progress(String message, ExerciseGenerationRepairRoundDTO repairRound) {
        accept(message);
    }

    default void phase(Phase phase, String message) {
        accept(message);
    }
}
