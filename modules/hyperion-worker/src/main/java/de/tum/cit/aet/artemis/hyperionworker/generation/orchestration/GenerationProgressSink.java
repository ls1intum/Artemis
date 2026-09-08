package de.tum.cit.aet.artemis.hyperionworker.generation.orchestration;

import de.tum.cit.aet.artemis.hyperion.protocol.GenerationProgress.RepairRound;
import de.tum.cit.aet.artemis.hyperion.runtime.agent.AgentActivitySink;
import de.tum.cit.aet.artemis.hyperionworker.generation.orchestration.GenerationProgressSink.Phase;

/**
 * Progress sink that optionally attaches structured repair-round telemetry, and — through {@link AgentActivitySink} — the run's live activity. Text-only consumers receive only the
 * message.
 */
@FunctionalInterface
public interface GenerationProgressSink extends AgentActivitySink {

    /** Worker-owned progress phases; saving belongs only to core. */
    enum Phase {
        PREPARING, DESIGNING, VERIFYING, REVIEWING, REPAIRING
    }

    default void progress(String message, RepairRound repairRound) {
        accept(message);
    }

    default void phase(Phase phase, String message) {
        accept(message);
    }
}
