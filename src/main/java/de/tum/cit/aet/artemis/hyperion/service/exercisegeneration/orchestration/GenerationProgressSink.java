package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.util.function.Consumer;

import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationRepairRoundDTO;

/**
 * The run's progress channel: the human-readable lines every stage already emits, plus the structured telemetry a few of them can attach to the same line.
 * <p>
 * Deliberately a {@link Consumer}{@code <String>} subtype with a single abstract method, so every existing caller — including the inner stages that only ever take a plain
 * {@code Consumer<String>} — keeps working unchanged, and a plain lambda still satisfies the type. The structured overload degrades to the plain line for such a caller, which
 * is the honest fallback: the instructor-facing transcript is never poorer than before, only the machine-readable enrichment is absent.
 */
@FunctionalInterface
public interface GenerationProgressSink extends Consumer<String> {

    /**
     * Emits {@code message} together with one repair round's finding bookkeeping, so a run's persisted transcript answers whether repairing drains findings.
     *
     * @param message     the human-readable progress line, exactly as it would be emitted without telemetry
     * @param repairRound the round's finding counts
     */
    default void progress(String message, ExerciseGenerationRepairRoundDTO repairRound) {
        accept(message);
    }
}
